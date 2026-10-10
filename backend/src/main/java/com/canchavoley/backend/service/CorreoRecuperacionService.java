package com.canchavoley.backend.service;

import com.canchavoley.backend.dto.RecuperacionCorreoResponse;
import com.canchavoley.backend.dto.AccesoReservasResponse;
import com.canchavoley.backend.model.Cliente;
import com.canchavoley.backend.repository.CodigoRecuperacionRepository;
import com.canchavoley.backend.repository.ClienteRepository;
import com.canchavoley.backend.repository.TokenGestionClienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

@Service
public class CorreoRecuperacionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorreoRecuperacionService.class);
    private static final String MENSAJE_SOLICITUD =
            "Si el correo corresponde a una cuenta, recibirás un código para recuperar tu acceso.";
    private static final String MENSAJE_CODIGO_INVALIDO =
            "El código es incorrecto o venció. Solicita uno nuevo.";
    private static final String FORMATO_CORREO = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClienteRepository clienteRepository;
    private final CodigoRecuperacionRepository codigoRepository;
    private final TokenGestionClienteRepository tokenRepository;
    private final ReservaService reservaService;
    private final PasswordEncoder passwordEncoder;
    private final GmailApiEmailSender emailSender;

    public CorreoRecuperacionService(
            ClienteRepository clienteRepository,
            CodigoRecuperacionRepository codigoRepository,
            TokenGestionClienteRepository tokenRepository,
            ReservaService reservaService,
            PasswordEncoder passwordEncoder,
            GmailApiEmailSender emailSender) {
        this.clienteRepository = clienteRepository;
        this.codigoRepository = codigoRepository;
        this.tokenRepository = tokenRepository;
        this.reservaService = reservaService;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
    }

    public RecuperacionCorreoResponse solicitarCodigo(String correo) {
        validarConfiguracion();
        String correoNormalizado = normalizarCorreo(correo);
        if (correoNormalizado == null) {
            return new RecuperacionCorreoResponse(MENSAJE_SOLICITUD);
        }

        Optional<Cliente> clienteEncontrado = clienteRepository.findByCorreoIgnoreCase(correoNormalizado);
        if (clienteEncontrado.isEmpty()) {
            return new RecuperacionCorreoResponse(MENSAJE_SOLICITUD);
        }

        Cliente cliente = clienteEncontrado.get();
        String codigo = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        if (!codigoRepository.registrarSolicitud(cliente.getIdCliente(), passwordEncoder.encode(codigo))) {
            return new RecuperacionCorreoResponse(MENSAJE_SOLICITUD);
        }

        enviarCodigo(correoNormalizado, codigo);
        return new RecuperacionCorreoResponse(MENSAJE_SOLICITUD);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AccesoReservasResponse verificarCodigo(String correo, String codigo) {
        validarConfiguracion();
        String correoNormalizado = normalizarCorreo(correo);
        if (correoNormalizado == null || codigo == null || !codigo.matches("\\d{6}")) {
            throw codigoInvalido();
        }

        Cliente cliente = clienteRepository.findByCorreoIgnoreCase(correoNormalizado)
                .orElseThrow(CorreoRecuperacionService::codigoInvalido);
        Long idCliente = cliente.getIdCliente();
        CodigoRecuperacionRepository.CodigoPendiente codigoPendiente = codigoRepository
                .buscarParaActualizar(idCliente)
                .orElseThrow(CorreoRecuperacionService::codigoInvalido);

        if (!codigoPendiente.vigente() || codigoPendiente.intentos() >= 5) {
            codigoRepository.eliminar(idCliente);
            throw codigoInvalido();
        }
        if (!passwordEncoder.matches(codigo, codigoPendiente.codigoHash())) {
            codigoRepository.registrarIntentoFallido(idCliente);
            throw codigoInvalido();
        }

        codigoRepository.eliminar(idCliente);
        tokenRepository.deleteAllByClienteIdCliente(idCliente);
        return new AccesoReservasResponse(reservaService.generarTokensGestionCliente(idCliente));
    }

    private void enviarCodigo(String correo, String codigo) {
        try {
            emailSender.enviar(correo, "Código para recuperar tus reservas", """
                    Tu código de recuperación es: %s

                    El código vence en 10 minutos. Si no solicitaste este código, puedes ignorar este mensaje.
                    """.formatted(codigo));
        } catch (GmailApiEmailSender.DeliveryException error) {
            LOGGER.error(
                    "No se pudo enviar un código de recuperación por correo. Diagnóstico Gmail API: {}",
                    diagnosticoSeguro(error));
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo enviar el correo de recuperación. Intenta más tarde.");
        }
    }

    private void validarConfiguracion() {
        if (!emailSender.estaConfigurado()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "La recuperación por correo todavía no está configurada.");
        }
    }

    private static String normalizarCorreo(String correo) {
        if (correo == null) {
            return null;
        }
        String normalizado = correo.trim().toLowerCase(Locale.ROOT);
        return normalizado.length() <= 254 && normalizado.matches(FORMATO_CORREO)
                ? normalizado
                : null;
    }

    static String diagnosticoSeguro(Throwable error) {
        if (error instanceof GmailApiEmailSender.DeliveryException gmailError
                && gmailError.statusCode() != null) {
            return (gmailError.stage() == GmailApiEmailSender.Stage.OAUTH
                    ? "Google OAuth"
                    : "Gmail API") + " respondió HTTP " + gmailError.statusCode();
        }

        StringBuilder tipos = new StringBuilder();
        Throwable actual = error;
        boolean falloConexion = false;
        int profundidad = 0;
        while (actual != null && profundidad < 5) {
            if (!tipos.isEmpty()) {
                tipos.append(" -> ");
            }
            String tipo = actual.getClass().getSimpleName();
            tipos.append(tipo);
            falloConexion |= actual instanceof ConnectException
                    || actual instanceof SocketTimeoutException
                    || actual instanceof UnknownHostException;
            Throwable causa = actual.getCause();
            if (causa == actual) {
                break;
            }
            actual = causa;
            profundidad++;
        }

        String categoria = falloConexion ? "conexión con Gmail API fallida" : "error de Gmail API";
        return categoria + " (" + tipos + ")";
    }

    private static ResponseStatusException codigoInvalido() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, MENSAJE_CODIGO_INVALIDO);
    }
}
