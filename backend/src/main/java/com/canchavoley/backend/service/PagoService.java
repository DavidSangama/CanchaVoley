package com.canchavoley.backend.service;

import com.canchavoley.backend.model.EstadoPago;
import com.canchavoley.backend.model.Pago;
import com.canchavoley.backend.model.Reserva;
import com.canchavoley.backend.model.Horario;
import com.canchavoley.backend.dto.VaciadoDatosResponse;
import com.canchavoley.backend.repository.PagoRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import com.canchavoley.backend.repository.HorarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class PagoService {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    private ReiniciarIdentidadesService reiniciarIdentidadesService;

    // --- GETs ---
    public List<Pago> obtenerTodos() {
        return pagoRepository.findAll();
    }

    public Optional<Pago> obtenerPorId(Long id) {
        return pagoRepository.findById(id);
    }

    public Optional<Pago> obtenerPorReserva(Long idReserva) {
        return pagoRepository.findByReservaIdReserva(idReserva);
    }

    public BigDecimal obtenerSumaTotal() {
        BigDecimal total = pagoRepository.sumarTotalPorEstado(EstadoPago.REALIZADO);
        return total != null ? total : BigDecimal.ZERO;
    }

    public long contarTodos() {
        return pagoRepository.count();
    }

    private Pago resolverReserva(Pago pago) {
        if (pago.getReserva() != null && pago.getReserva().getIdReserva() != null) {
            Reserva reserva = reservaRepository.findById(pago.getReserva().getIdReserva())
                    .orElseThrow(() -> new RuntimeException("Reserva no encontrada con id: " + pago.getReserva().getIdReserva()));
            pago.setReserva(reserva);
        }
        return pago;
    }

    // --- POSTs ---
    public Pago guardar(Pago pago) {
        pago.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
        Pago pagoResuelto = resolverReserva(pago);
        pagoResuelto.setTotal(pagoResuelto.getReserva().getPrecio());
        return pagoRepository.save(pagoResuelto);
    }

    public List<Pago> guardarVarios(List<Pago> pagos) {
        pagos.forEach(pago -> {
            pago.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
            resolverReserva(pago);
        });
        return pagoRepository.saveAll(pagos);
    }

    // --- PUTs ---
    @Transactional
    public Pago actualizar(Long id, Pago detalles) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado con id: " + id));
        Pago detallesResueltos = resolverReserva(detalles);
        bloquearHorarios(
                pago.getReserva().getHorario().getIdHorario(),
                detallesResueltos.getReserva().getHorario().getIdHorario());
        pago.setReserva(detallesResueltos.getReserva());
        pago.setTotal(detalles.getTotal());
        if (detalles.getEstado() != null) {
            pago.setEstado(detalles.getEstado());
        }
        return pagoRepository.save(pago);
    }

    public Pago actualizarMonto(Long id, BigDecimal nuevoTotal) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado con id: " + id));
        pago.setTotal(nuevoTotal);
        return pagoRepository.save(pago);
    }

    @Transactional
    public Pago actualizarEstado(Long id, EstadoPago nuevoEstado) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado con id: " + id));
        bloquearHorarios(pago.getReserva().getHorario().getIdHorario());
        pago.setEstado(nuevoEstado);
        return pagoRepository.save(pago);
    }

    @Transactional
    public int actualizarEstadoDeTodos(EstadoPago nuevoEstado) {
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("Selecciona un estado de pago válido.");
        }
        List<Pago> pagos = pagoRepository.findAll();
        pagos.stream()
                .map(pago -> pago.getReserva().getHorario().getIdHorario())
                .distinct()
                .sorted()
                .forEach(this::bloquearHorarios);
        pagos.forEach(pago -> pago.setEstado(nuevoEstado));
        pagoRepository.saveAll(pagos);
        return pagos.size();
    }

    private void bloquearHorarios(Long... idsHorario) {
        java.util.Arrays.stream(idsHorario)
                .distinct()
                .sorted()
                .forEach(idHorario -> horarioRepository.findByIdForUpdate(idHorario)
                        .orElseThrow(() -> new RuntimeException("Horario no encontrado con id: " + idHorario)));
    }

    @Transactional
    public VaciadoDatosResponse vaciarPagos() {
        long eliminados = pagoRepository.count();
        pagoRepository.deleteAllInBatch();
        reiniciarIdentidadesService.reiniciarPagos();
        return new VaciadoDatosResponse(0, 0, eliminados);
    }

    // --- DELETEs ---
    public void eliminarPorId(Long id) {
        pagoRepository.deleteById(id);
    }

    @Transactional
    public void eliminarPorReserva(Long idReserva) {
        pagoRepository.deleteByReservaIdReserva(idReserva);
    }
}