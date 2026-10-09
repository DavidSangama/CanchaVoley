package com.canchavoley.backend.service;

import com.canchavoley.backend.dto.RecuperacionCorreoResponse;
import com.canchavoley.backend.dto.TokenGestionResponse;
import com.canchavoley.backend.model.Cliente;
import com.canchavoley.backend.model.TokenGestionCliente;
import com.canchavoley.backend.repository.CodigoRecuperacionRepository;
import com.canchavoley.backend.repository.ClienteRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import com.canchavoley.backend.repository.TokenGestionClienteRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final ReservaRepository reservaRepository;
    private final TokenGestionClienteRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String gmailUsername;
    private final String gmailAppPassword;

    public CorreoRecuperacionService(
            ClienteRepository clienteRepository,
            CodigoRecuperacionRepository codigoRepository,
            ReservaRepository reservaRepository,
            TokenGestionClienteRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            @Value("${GMAIL_SMTP_USERNAME:}") String gmailUsername,
            @Value("${GMAIL_SMTP_APP_PASSWORD:}") String gmailAppPassword) {
        this.clienteRepository = clienteRepository;
        this.codigoRepository = codigoRepository;
        this.reservaRepository = reservaRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.gmailUsername = gmailUsername;
        this.gmailAppPassword = gmailAppPassword;
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
    public TokenGestionResponse verificarCodigo(String correo, String codigo) {
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
        reservaRepository.revocarTokensDeCliente(idCliente);
        tokenRepository.deleteAllByClienteIdCliente(idCliente);
        String tokenGestion = TokenGestionUtils.generarToken();
        tokenRepository.save(new TokenGestionCliente(cliente, TokenGestionUtils.hashToken(tokenGestion)));
        return new TokenGestionResponse(tokenGestion);
    }

    private void enviarCodigo(String correo, String codigo) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(mensaje, false, "UTF-8");
            helper.setFrom(gmailUsername);
            helper.setTo(correo);
            helper.setSubject("Código para recuperar tus reservas");
            helper.setText("""
                    Tu código de recuperación es: %s

                    El código vence en 10 minutos. Si no solicitaste este código, puedes ignorar este mensaje.
                    """.formatted(codigo));
            mailSender.send(mensaje);
        } catch (MailException | MessagingException error) {
            LOGGER.error("No se pudo enviar un código de recuperación por correo.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo enviar el correo de recuperación. Intenta más tarde.");
        }
    }

    private void validarConfiguracion() {
        if (gmailUsername.isBlank() || gmailAppPassword.isBlank()) {
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

    private static ResponseStatusException codigoInvalido() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, MENSAJE_CODIGO_INVALIDO);
    }
}
