package com.canchavoley.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class ResendEmailSender {

    private static final URI API_URI = URI.create("https://api.resend.com/emails");

    private final HttpClient httpClient;
    private final String apiKey;
    private final String fromEmail;
    private final URI apiUri;

    public ResendEmailSender(
            @Value("${RESEND_API_KEY:}") String apiKey,
            @Value("${RESEND_FROM_EMAIL:}") String fromEmail) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                apiKey,
                fromEmail,
                API_URI);
    }

    ResendEmailSender(
            HttpClient httpClient,
            String apiKey,
            String fromEmail,
            URI apiUri) {
        this.httpClient = httpClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.apiUri = apiUri;
    }

    boolean estaConfigurado() {
        return !apiKey.isBlank() && !fromEmail.isBlank();
    }

    void enviar(String destinatario, String asunto, String texto) {
        try {
            String body = "{\"from\":" + jsonString(fromEmail)
                    + ",\"to\":[" + jsonString(destinatario)
                    + "],\"subject\":" + jsonString(asunto)
                    + ",\"text\":" + jsonString(texto) + "}";
            HttpRequest request = HttpRequest.newBuilder(apiUri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DeliveryException("Resend rechazó el correo.", response.statusCode(), null);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DeliveryException("Se interrumpió el envío del correo.", null, error);
        } catch (IOException error) {
            throw new DeliveryException("No se pudo conectar con Resend.", null, error);
        }
    }

    private static String jsonString(String value) {
        StringBuilder json = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append("\\u");
                        String hex = Integer.toHexString(character);
                        json.append("0".repeat(4 - hex.length())).append(hex);
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    static final class DeliveryException extends RuntimeException {
        private final Integer statusCode;

        DeliveryException(String message, Integer statusCode, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
        }

        Integer statusCode() {
            return statusCode;
        }
    }
}
