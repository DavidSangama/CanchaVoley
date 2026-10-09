package com.canchavoley.backend.service;

import com.canchavoley.backend.model.Cliente;
import com.canchavoley.backend.dto.VaciadoDatosResponse;
import com.canchavoley.backend.repository.ClienteRepository;
import com.canchavoley.backend.repository.PagoRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import com.canchavoley.backend.repository.TokenGestionClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ClienteService {

    private static final Pattern FORMATO_CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private TokenGestionClienteRepository tokenGestionClienteRepository;

    @Autowired
    private ReiniciarIdentidadesService reiniciarIdentidadesService;

    // --- GETs ---
    public List<Cliente> obtenerTodos() {
        return clienteRepository.findAll();
    }

    public Optional<Cliente> obtenerPorId(Long id) {
        return clienteRepository.findById(id);
    }

    public Optional<Cliente> obtenerPorDni(String dni) {
        return clienteRepository.findByDni(dni);
    }

    public boolean existePorDni(String dni) {
        return clienteRepository.existsByDni(dni);
    }

    public long contarTodos() {
        return clienteRepository.count();
    }

    // --- POSTs ---
    public Cliente guardar(Cliente cliente) {
        cliente.setCorreo(validarCorreo(cliente.getCorreo()));
        return clienteRepository.save(cliente);
    }

    public List<Cliente> guardarVarios(List<Cliente> clientes) {
        return clienteRepository.saveAll(clientes);
    }

    // --- PUTs ---
    public Cliente actualizar(Long id, Cliente clienteDetalles) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id: " + id));
        cliente.setNombre(clienteDetalles.getNombre());
        cliente.setApellido(clienteDetalles.getApellido());
        cliente.setTelefono(clienteDetalles.getTelefono());
        cliente.setDni(clienteDetalles.getDni());
        cliente.setCorreo(validarCorreo(clienteDetalles.getCorreo()));
        return clienteRepository.save(cliente);
    }

    private String validarCorreo(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo electrónico es obligatorio.");
        }
        String normalizado = correo.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalizado.length() > 254 || !FORMATO_CORREO.matcher(normalizado).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un correo electrónico válido.");
        }
        return normalizado;
    }

    public Cliente actualizarTelefono(Long id, String nuevoTelefono) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id: " + id));
        cliente.setTelefono(nuevoTelefono);
        return clienteRepository.save(cliente);
    }

    // --- DELETEs ---
    public void eliminarPorId(Long id) {
        clienteRepository.deleteById(id);
    }

    @Transactional
    public void eliminarPorDni(String dni) {
        clienteRepository.deleteByDni(dni);
    }

    @Transactional
    public VaciadoDatosResponse vaciarClientes() {
        long clientesEliminados = clienteRepository.count();
        long reservasEliminadas = reservaRepository.count();
        long pagosEliminados = pagoRepository.count();
        tokenGestionClienteRepository.deleteAllInBatch();
        pagoRepository.deleteAllInBatch();
        reservaRepository.deleteAllInBatch();
        clienteRepository.deleteAllInBatch();
        reiniciarIdentidadesService.reiniciarTokensGestion();
        reiniciarIdentidadesService.reiniciarPagos();
        reiniciarIdentidadesService.reiniciarReservas();
        reiniciarIdentidadesService.reiniciarClientes();
        return new VaciadoDatosResponse(clientesEliminados, reservasEliminadas, pagosEliminados);
    }
}