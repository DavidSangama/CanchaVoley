package com.canchavoley.backend.service;

import com.canchavoley.backend.model.Horario;
import com.canchavoley.backend.model.Pago;
import com.canchavoley.backend.model.EstadoPago;
import com.canchavoley.backend.model.Reserva;
import com.canchavoley.backend.repository.HorarioRepository;
import com.canchavoley.backend.repository.PagoRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class HorarioService {

    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    // --- GETs ---
    public List<Horario> obtenerTodos() {
        return horarioRepository.findAll();
    }

    public Optional<Horario> obtenerPorId(Long id) {
        return horarioRepository.findById(id);
    }

    public Optional<Horario> obtenerPorHora(LocalTime hora) {
        return horarioRepository.findByHora(hora);
    }

    public List<Horario> obtenerPorPrecioMaximo(BigDecimal precioMax) {
        return horarioRepository.findByPrecioLessThanEqual(precioMax);
    }

    public long contarTodos() {
        return horarioRepository.count();
    }

    // --- POSTs ---
    public Horario guardar(Horario horario) {
        return horarioRepository.save(horario);
    }

    public List<Horario> guardarVarios(List<Horario> horarios) {
        return horarioRepository.saveAll(horarios);
    }

    // --- PUTs ---
    @Transactional
    public Horario actualizar(Long id, Horario detalles) {
        Horario horario = horarioRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Horario no encontrado con id: " + id));
        horario.setHora(detalles.getHora());
        horario.setPrecio(detalles.getPrecio());
        actualizarPreciosPendientes(id, detalles.getPrecio());
        return horarioRepository.save(horario);
    }

    @Transactional
    public Horario actualizarPrecio(Long id, BigDecimal nuevoPrecio) {
        Horario horario = horarioRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Horario no encontrado con id: " + id));
        horario.setPrecio(nuevoPrecio);
        actualizarPreciosPendientes(id, nuevoPrecio);
        return horarioRepository.save(horario);
    }

    private void actualizarPreciosPendientes(Long idHorario, BigDecimal nuevoPrecio) {
        List<Pago> pagosPendientes = pagoRepository.findByReserva_Horario_IdHorarioAndEstado(
                idHorario,
                EstadoPago.PENDIENTE_VERIFICACION);
        List<Reserva> reservasActualizadas = pagosPendientes.stream()
                .map(Pago::getReserva)
                .peek(reserva -> reserva.setPrecio(nuevoPrecio))
                .toList();
        pagosPendientes.forEach(pago -> pago.setTotal(nuevoPrecio));
        reservaRepository.saveAll(reservasActualizadas);
        pagoRepository.saveAll(pagosPendientes);
    }

    // --- DELETEs ---
    public void eliminarPorId(Long id) {
        horarioRepository.deleteById(id);
    }

    @Transactional
    public void eliminarPorHora(LocalTime hora) {
        horarioRepository.deleteByHora(hora);
    }
}