package com.canchavoley.backend.service;

import com.canchavoley.backend.model.Cancha;
import com.canchavoley.backend.model.Cliente;
import com.canchavoley.backend.model.Horario;
import com.canchavoley.backend.model.EstadoPago;
import com.canchavoley.backend.model.Pago;
import com.canchavoley.backend.model.Reserva;
import com.canchavoley.backend.repository.CanchaRepository;
import com.canchavoley.backend.repository.ClienteRepository;
import com.canchavoley.backend.repository.HorarioRepository;
import com.canchavoley.backend.repository.PagoRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import com.canchavoley.backend.repository.TokenGestionClienteRepository;
import com.canchavoley.backend.model.TokenGestionCliente;
import com.canchavoley.backend.dto.ReservaCreadaResponse;
import com.canchavoley.backend.dto.ReservaGestionResponse;
import com.canchavoley.backend.dto.VaciadoDatosResponse;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class ReservaService {

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private CanchaRepository canchaRepository;

    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private TokenGestionClienteRepository tokenGestionClienteRepository;

    @Autowired
    private ReiniciarIdentidadesService reiniciarIdentidadesService;

    // --- GETs ---
    public List<Reserva> obtenerTodas() {
        return reservaRepository.findAll();
    }

    public Optional<Reserva> obtenerPorId(Long id) {
        return reservaRepository.findById(id);
    }

    public List<Reserva> obtenerPorFecha(LocalDate fecha) {
        return reservaRepository.findByFecha(fecha);
    }

    public List<Reserva> obtenerPorCliente(Long idCliente) {
        return reservaRepository.findByClienteIdCliente(idCliente);
    }

    public long contarTodas() {
        return reservaRepository.count();
    }

    @Transactional
    public ReservaCreadaResponse crearConTokenCancelacion(Reserva reserva) {
        String tokenCancelacion = TokenGestionUtils.generarToken();

        reserva.setTokenCancelacionHash(TokenGestionUtils.hashToken(tokenCancelacion));
        Reserva reservaGuardada = reservaRepository.save(resolverRelaciones(reserva));
        tokenGestionClienteRepository.save(new TokenGestionCliente(
                reservaGuardada.getCliente(),
                TokenGestionUtils.hashToken(tokenCancelacion)));
        return new ReservaCreadaResponse(reservaGuardada.getIdReserva(), tokenCancelacion);
    }

    @Transactional
    public void cancelarSolicitudCliente(Long idReserva, String tokenCancelacion) {
        Reserva reserva = obtenerReservaDelClienteAutorizado(idReserva, tokenCancelacion);

        Pago pago = pagoRepository.findByReservaIdReserva(idReserva)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya no está pendiente de pago."));

        if (pago.getEstado() == EstadoPago.REALIZADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un pago realizado no se puede cancelar desde esta página.");
        }

        pagoRepository.deleteByReservaIdReserva(idReserva);
        reservaRepository.delete(reserva);
    }

    @Transactional(readOnly = true)
    public ReservaGestionResponse obtenerGestionCliente(Long idReserva, String tokenGestion) {
        Reserva reserva = obtenerReservaConToken(idReserva, tokenGestion);
        Pago pago = pagoRepository.findByReservaIdReserva(idReserva)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return crearRespuestaGestion(reserva, pago);
    }

    @Transactional(readOnly = true)
    public List<ReservaGestionResponse> obtenerReservasCliente(String tokenGestion) {
        Long idCliente = obtenerIdClientePorToken(tokenGestion);
        return reservaRepository.findByClienteIdClienteOrderByFechaDescIdReservaDesc(idCliente).stream()
                .map(reserva -> crearRespuestaGestion(
                        reserva,
                        pagoRepository.findByReservaIdReserva(reserva.getIdReserva()).orElse(null)))
                .toList();
    }

    @Transactional
    public ReservaGestionResponse reprogramarComoCliente(
            Long idReserva,
            String tokenGestion,
            LocalDate nuevaFecha,
            Long idHorario) {
        Reserva reserva = obtenerReservaDelClienteAutorizado(idReserva, tokenGestion);
        Pago pago = pagoRepository.findByReservaIdReserva(idReserva)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Esta reserva ya no tiene una solicitud de pago activa."));

        if (pago.getEstado() != EstadoPago.PENDIENTE_VERIFICACION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden reprogramar pagos pendientes de verificación.");
        }
        if (nuevaFecha == null || nuevaFecha.isBefore(LocalDate.now(ZoneId.of("America/Lima")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva fecha debe ser hoy o posterior.");
        }
        if (idHorario == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un horario.");
        }
        if (reservaRepository.existsByFechaAndCanchaIdCanchaAndHorarioIdHorarioAndIdReservaNot(
                nuevaFecha, reserva.getCancha().getIdCancha(), idHorario, idReserva)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese horario ya está reservado.");
        }

        Horario horario = horarioRepository.findById(idHorario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El horario seleccionado no existe."));
        reserva.setFecha(nuevaFecha);
        reserva.setHorario(horario);
        pago.setTotal(horario.getPrecio());
        pagoRepository.save(pago);
        Reserva reservaActualizada = reservaRepository.save(reserva);
        return crearRespuestaGestion(reservaActualizada, pago);
    }

    private Reserva obtenerReservaConToken(Long idReserva, String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return reservaRepository.findByIdReservaAndTokenCancelacionHash(idReserva, TokenGestionUtils.hashToken(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Reserva obtenerReservaDelClienteAutorizado(Long idReserva, String token) {
        Long idClienteAutorizado = obtenerIdClientePorToken(token);
        Reserva reserva = reservaRepository.findById(idReserva)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!idClienteAutorizado.equals(reserva.getCliente().getIdCliente())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return reserva;
    }

    private Long obtenerIdClientePorToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String hash = TokenGestionUtils.hashToken(token);
        return tokenGestionClienteRepository.findByTokenHash(hash)
                .map(tokenCliente -> tokenCliente.getCliente().getIdCliente())
                .or(() -> reservaRepository.findByTokenCancelacionHash(hash)
                        .map(reserva -> reserva.getCliente().getIdCliente()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ReservaGestionResponse crearRespuestaGestion(Reserva reserva, Pago pago) {
        return new ReservaGestionResponse(
                reserva.getIdReserva(),
                reserva.getFecha(),
                reserva.getCancha().getIdCancha(),
                reserva.getCancha().getNumeroCancha(),
                reserva.getHorario().getIdHorario(),
                reserva.getHorario().getHora().toString(),
                reserva.getHorario().getPrecio(),
                pago == null ? null : pago.getEstado());
    }

    // --- Resuelve las relaciones (cliente/cancha/horario) por su ID real ---
    private Reserva resolverRelaciones(Reserva reserva) {
        if (reserva.getCliente() != null && reserva.getCliente().getIdCliente() != null) {
            Cliente cliente = clienteRepository.findById(reserva.getCliente().getIdCliente())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id: " + reserva.getCliente().getIdCliente()));
            reserva.setCliente(cliente);
        }
        if (reserva.getCancha() != null && reserva.getCancha().getIdCancha() != null) {
            Cancha cancha = canchaRepository.findById(reserva.getCancha().getIdCancha())
                    .orElseThrow(() -> new RuntimeException("Cancha no encontrada con id: " + reserva.getCancha().getIdCancha()));
            reserva.setCancha(cancha);
        }
        if (reserva.getHorario() != null && reserva.getHorario().getIdHorario() != null) {
            Horario horario = horarioRepository.findById(reserva.getHorario().getIdHorario())
                    .orElseThrow(() -> new RuntimeException("Horario no encontrado con id: " + reserva.getHorario().getIdHorario()));
            reserva.setHorario(horario);
        }
        return reserva;
    }

    // --- POSTs ---
    public Reserva guardar(Reserva reserva) {
        return reservaRepository.save(resolverRelaciones(reserva));
    }

    public List<Reserva> guardarVarias(List<Reserva> reservas) {
        reservas.forEach(this::resolverRelaciones);
        return reservaRepository.saveAll(reservas);
    }

    // --- PUTs ---
    public Reserva actualizar(Long id, Reserva detalles) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con id: " + id));
        Reserva detallesResueltos = resolverRelaciones(detalles);
        reserva.setCliente(detallesResueltos.getCliente());
        reserva.setCancha(detallesResueltos.getCancha());
        reserva.setHorario(detallesResueltos.getHorario());
        reserva.setFecha(detalles.getFecha());
        return reservaRepository.save(reserva);
    }

    public Reserva actualizarFecha(Long id, LocalDate nuevaFecha) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con id: " + id));
        reserva.setFecha(nuevaFecha);
        return reservaRepository.save(reserva);
    }

    // --- DELETEs ---
    @Transactional
    public void eliminarPorId(Long id) {
        pagoRepository.deleteByReservaIdReserva(id);
        reservaRepository.deleteById(id);
    }

    @Transactional
    public void eliminarPorFecha(LocalDate fecha) {
        reservaRepository.deleteByFecha(fecha);
    }

    @Transactional
    public VaciadoDatosResponse vaciarReservas() {
        long reservasEliminadas = reservaRepository.count();
        long pagosEliminados = pagoRepository.count();
        pagoRepository.deleteAllInBatch();
        reservaRepository.deleteAllInBatch();
        reiniciarIdentidadesService.reiniciarPagos();
        reiniciarIdentidadesService.reiniciarReservas();
        return new VaciadoDatosResponse(0, reservasEliminadas, pagosEliminados);
    }
}