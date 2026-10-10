package com.canchavoley.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Component
public class GmailApiEmailSender {

    private static final URI TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    private static final URI EMAILS_URI = URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String fromEmail;
    private final URI tokenUri;
    private final URI emailsUri;

    public GmailApiEmailSender(
            @Value("${GMAIL_OAUTH_CLIENT_ID:}") String clientId,
            @Value("${GMAIL_OAUTH_CLIENT_SECRET:}") String clientSecret,
            @Value("${GMAIL_OAUTH_REFRESH_TOKEN:}") String refreshToken,
            @Value("${GMAIL_API_FROM_EMAIL:}") String fromEmail) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                clientId,
                clientSecret,
                refreshToken,
                fromEmail,
                TOKEN_URI,
                EMAILS_URI);
    }

    GmailApiEmailSender(
            HttpClient httpClient,
            String clientId,
            String clientSecret,
            String refreshToken,
            String fromEmail,
            URI tokenUri,
            URI emailsUri) {
        this.httpClient = httpClient;
        this.clientId = clean(clientId);
        this.clientSecret = clean(clientSecret);
        this.refreshToken = clean(refreshToken);
        this.fromEmail = clean(fromEmail);
        this.tokenUri = tokenUri;
        this.emailsUri = emailsUri;
    }

    boolean estaConfigurado() {
        return !clientId.isBlank()
                && !clientSecret.isBlank()
                && !refreshToken.isBlank()
                && fromEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    void enviar(String destinatario, String asunto, String texto) {
        String accessToken = obtenerAccessToken();
        String rawMessage = crearMensajeMime(destinatario, asunto, texto);
        try {
            String body = OBJECT_MAPPER.writeValueAsString(Map.of("raw", rawMessage));
            HttpRequest request = HttpRequest.newBuilder(emailsUri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DeliveryException("Gmail API rechazó el correo.", Stage.GMAIL_API, response.statusCode(), null);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DeliveryException("Se interrumpió el envío del correo.", Stage.GMAIL_API, null, error);
        } catch (IOException error) {
            throw new DeliveryException("No se pudo conectar con Gmail API.", Stage.GMAIL_API, null, error);
        }
    }

    private String obtenerAccessToken() {
        String form = "client_id=" + encodeForm(clientId)
                + "&client_secret=" + encodeForm(clientSecret)
                + "&refresh_token=" + encodeForm(refreshToken)
                + "&grant_type=refresh_token";
        HttpRequest request = HttpRequest.newBuilder(tokenUri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DeliveryException("Google OAuth rechazó el token.", Stage.OAUTH, response.statusCode(), null);
            }
            JsonNode accessToken = OBJECT_MAPPER.readTree(response.body()).get("access_token");
            if (accessToken == null || !accessToken.isTextual() || accessToken.asText().isBlank()) {
                throw new DeliveryException("Google OAuth no devolvió un token de acceso.", Stage.OAUTH, null, null);
            }
            return accessToken.asText();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DeliveryException("Se interrumpió la autorización con Google.", Stage.OAUTH, null, error);
        } catch (IOException error) {
            throw new DeliveryException("No se pudo completar la autorización con Google.", Stage.OAUTH, null, error);
        }
    }

    private String crearMensajeMime(String destinatario, String asunto, String texto) {
        String asuntoCodificado = Base64.getEncoder().encodeToString(asunto.getBytes(StandardCharsets.UTF_8));
        String cuerpoCodificado = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(texto.getBytes(StandardCharsets.UTF_8));
        String mime = "From: " + fromEmail + "\r\n"
                + "To: " + destinatario + "\r\n"
                + "Subject: =?UTF-8?B?" + asuntoCodificado + "?=\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + cuerpoCodificado;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mime.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeForm(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    enum Stage {
        OAUTH,
        GMAIL_API
    }

    static final class DeliveryException extends RuntimeException {
        private final Stage stage;
        private final Integer statusCode;

        DeliveryException(String message, Stage stage, Integer statusCode, Throwable cause) {
            super(message, cause);
            this.stage = stage;
            this.statusCode = statusCode;
        }

        Stage stage() {
            return stage;
        }

        Integer statusCode() {
            return statusCode;
        }
    }
}
