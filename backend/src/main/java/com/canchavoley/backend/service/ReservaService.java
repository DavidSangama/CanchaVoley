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
import com.canchavoley.backend.dto.ReservaCreadaResponse;
import com.canchavoley.backend.dto.ReservaGestionTokenResponse;
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
    private ReiniciarIdentidadesService reiniciarIdentidadesService;

    // --- GETs ---
    public List<Reserva> obtenerTodas() {
        return reservaRepository.findAll();
    }

    public Optional<Reserva> obtenerPorId(Long id) {
        return reservaRepository.findById(id);
    }

    public List<Reserva> obtenerPorFecha(LocalDate fecha) {
        return reservaRepository.findOcupadasByFecha(fecha);
    }

    public List<Reserva> obtenerPorCliente(Long idCliente) {
        return reservaRepository.findByClienteIdCliente(idCliente);
    }

    public long contarTodas() {
        return reservaRepository.count();
    }

    @Transactional
    public ReservaCreadaResponse crearConTokenCancelacion(Reserva reserva) {
        if (reserva.getFecha() == null || reserva.getCancha() == null || reserva.getHorario() == null
                || reserva.getHorario().getIdHorario() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La reserva requiere fecha, cancha y horario.");
        }
        Horario horario = bloquearYValidarDisponibilidad(
                reserva.getFecha(),
                reserva.getCancha().getIdCancha(),
                reserva.getHorario().getIdHorario(),
                null);
        reserva.setHorario(horario);
        reserva.setPrecio(horario.getPrecio());
        String tokenCancelacion = TokenGestionUtils.generarToken();

        reserva.setTokenCancelacionHash(TokenGestionUtils.hashToken(tokenCancelacion));
        Reserva reservaGuardada = reservaRepository.save(resolverRelaciones(reserva));
        return new ReservaCreadaResponse(reservaGuardada.getIdReserva(), tokenCancelacion);
    }

    @Transactional
    public List<ReservaGestionTokenResponse> generarTokensGestionCliente(Long idCliente) {
        List<Reserva> reservas = reservaRepository.findByClienteIdClienteOrderByFechaDescIdReservaDesc(idCliente);
        List<String> tokens = reservas.stream()
                .map(reserva -> {
                    String token = TokenGestionUtils.generarToken();
                    reserva.setTokenCancelacionHash(TokenGestionUtils.hashToken(token));
                    return token;
                })
                .toList();
        reservaRepository.saveAll(reservas);

        return java.util.stream.IntStream.range(0, reservas.size())
                .mapToObj(index -> {
                    Reserva reserva = reservas.get(index);
                    Pago pago = pagoRepository.findByReservaIdReserva(reserva.getIdReserva()).orElse(null);
                    return crearRespuestaGestionConToken(reserva, pago, tokens.get(index));
                })
                .toList();
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
                .orElse(null);
        return crearRespuestaGestion(reserva, pago);
    }

    @Transactional(readOnly = true)
    public List<ReservaGestionResponse> obtenerReservasCliente(String tokenGestion) {
        if (tokenGestion == null || tokenGestion.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Reserva reserva = reservaRepository.findByTokenCancelacionHash(TokenGestionUtils.hashToken(tokenGestion))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return List.of(crearRespuestaGestion(
                reserva,
                pagoRepository.findByReservaIdReserva(reserva.getIdReserva()).orElse(null)));
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
        Horario horario = bloquearYValidarDisponibilidad(
                nuevaFecha, reserva.getCancha().getIdCancha(), idHorario, idReserva);

        reserva.setFecha(nuevaFecha);
        reserva.setHorario(horario);
        reserva.setPrecio(horario.getPrecio());
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
        return obtenerReservaConToken(idReserva, token);
    }

    private Horario bloquearYValidarDisponibilidad(
            LocalDate fecha,
            Long idCancha,
            Long idHorario,
            Long idReservaExcluida) {
        Horario horario = horarioRepository.findByIdForUpdate(idHorario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El horario seleccionado no existe."));
        if (reservaRepository.existsOcupada(fecha, idCancha, idHorario, idReservaExcluida)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese horario ya está reservado.");
        }
        return horario;
    }

    private ReservaGestionTokenResponse crearRespuestaGestionConToken(
            Reserva reserva,
            Pago pago,
            String tokenGestion) {
        return new ReservaGestionTokenResponse(
                reserva.getIdReserva(),
                reserva.getFecha(),
                reserva.getCancha().getIdCancha(),
                reserva.getCancha().getNumeroCancha(),
                reserva.getHorario().getIdHorario(),
                reserva.getHorario().getHora().toString(),
                pago == null ? reserva.getPrecio() : pago.getTotal(),
                pago == null ? null : pago.getEstado(),
                tokenGestion);
    }

    private ReservaGestionResponse crearRespuestaGestion(Reserva reserva, Pago pago) {
        return new ReservaGestionResponse(
                reserva.getIdReserva(),
                reserva.getFecha(),
                reserva.getCancha().getIdCancha(),
                reserva.getCancha().getNumeroCancha(),
                reserva.getHorario().getIdHorario(),
                reserva.getHorario().getHora().toString(),
                pago == null ? reserva.getPrecio() : pago.getTotal(),
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
            if (reserva.getPrecio() == null) {
                reserva.setPrecio(horario.getPrecio());
            }
        }
        return reserva;
    }

    // --- POSTs ---
    public Reserva guardar(Reserva reserva) {
        return reservaRepository.save(resolverRelaciones(reserva));
    }

    @Transactional
    public List<Reserva> guardarVarias(List<Reserva> reservas) {
        java.util.Set<String> nuevasOcupaciones = new java.util.HashSet<>();
        reservas.stream()
                .sorted(java.util.Comparator.comparing(
                        reserva -> reserva.getHorario().getIdHorario()))
                .forEach(reserva -> {
                    Long idHorario = reserva.getHorario().getIdHorario();
                    bloquearYValidarDisponibilidad(
                            reserva.getFecha(),
                            reserva.getCancha().getIdCancha(),
                            idHorario,
                            null);
                    String clave = reserva.getFecha() + ":" + reserva.getCancha().getIdCancha() + ":" + idHorario;
                    if (!nuevasOcupaciones.add(clave)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "El lote contiene horarios repetidos.");
                    }
                    resolverRelaciones(reserva);
                });
        return reservaRepository.saveAll(reservas);
    }

    // --- PUTs ---
    @Transactional
    public Reserva actualizar(Long id, Reserva detalles) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con id: " + id));
        Pago pago = pagoRepository.findByReservaIdReserva(id).orElse(null);
        Reserva detallesResueltos = resolverRelaciones(detalles);
        if (pago == null || pago.getEstado() == EstadoPago.PENDIENTE_VERIFICACION) {
            bloquearYValidarDisponibilidad(
                    detalles.getFecha(),
                    detallesResueltos.getCancha().getIdCancha(),
                    detallesResueltos.getHorario().getIdHorario(),
                    id);
            reserva.setPrecio(detallesResueltos.getHorario().getPrecio());
            if (pago != null) {
                pago.setTotal(reserva.getPrecio());
                pagoRepository.save(pago);
            }
        }
        reserva.setCliente(detallesResueltos.getCliente());
        reserva.setCancha(detallesResueltos.getCancha());
        reserva.setHorario(detallesResueltos.getHorario());
        reserva.setFecha(detalles.getFecha());
        return reservaRepository.save(reserva);
    }

    @Transactional
    public Reserva actualizarFecha(Long id, LocalDate nuevaFecha) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con id: " + id));
        Pago pago = pagoRepository.findByReservaIdReserva(id).orElse(null);
        if (pago == null || pago.getEstado() == EstadoPago.PENDIENTE_VERIFICACION) {
            bloquearYValidarDisponibilidad(
                    nuevaFecha,
                    reserva.getCancha().getIdCancha(),
                    reserva.getHorario().getIdHorario(),
                    id);
        }
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