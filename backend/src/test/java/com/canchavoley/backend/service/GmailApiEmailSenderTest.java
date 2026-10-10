package com.canchavoley.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GmailApiEmailSenderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void refreshesOauthTokenAndSendsMimeMessageThroughGmailApi() throws Exception {
        AtomicReference<String> tokenForm = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> rawMessage = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            tokenForm.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"access_token\":\"test-access-token\",\"token_type\":\"Bearer\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/send", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            JsonNode requestBody = objectMapper.readTree(exchange.getRequestBody());
            rawMessage.set(requestBody.get("raw").asText());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        try {
            GmailApiEmailSender sender = createSender(server);
            sender.enviar("cliente@example.com", "Código de recuperación", "Tu código es: 123456");

            assertTrue(tokenForm.get().contains("client_id=client-id"));
            assertTrue(tokenForm.get().contains("client_secret=client-secret"));
            assertTrue(tokenForm.get().contains("refresh_token=refresh-token"));
            assertTrue(tokenForm.get().contains("grant_type=refresh_token"));
            assertEquals("Bearer test-access-token", authorization.get());

            String mimeMessage = new String(
                    Base64.getUrlDecoder().decode(rawMessage.get()),
                    StandardCharsets.UTF_8);
            assertTrue(mimeMessage.contains("From: canchavoleyservice@gmail.com\r\n"));
            assertTrue(mimeMessage.contains("To: cliente@example.com\r\n"));
            assertTrue(mimeMessage.contains("Subject: =?UTF-8?B?"));
            assertTrue(mimeMessage.contains("Content-Type: text/plain; charset=UTF-8\r\n"));
            String encodedBody = mimeMessage.substring(mimeMessage.indexOf("\r\n\r\n") + 4);
            assertEquals(
                    "Tu código es: 123456",
                    new String(Base64.getMimeDecoder().decode(encodedBody), StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsOAuthStatusWithoutExposingResponseBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            byte[] response = "{\"error\":\"invalid_grant\",\"description\":\"private details\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            GmailApiEmailSender sender = createSender(server);
            GmailApiEmailSender.DeliveryException error = assertThrows(
                    GmailApiEmailSender.DeliveryException.class,
                    () -> sender.enviar("cliente@example.com", "Código", "123456"));

            assertEquals(GmailApiEmailSender.Stage.OAUTH, error.stage());
            assertEquals(400, error.statusCode());
            assertEquals("Google OAuth respondió HTTP 400", CorreoRecuperacionService.diagnosticoSeguro(error));
            assertTrue(!CorreoRecuperacionService.diagnosticoSeguro(error).contains("private"));
        } finally {
            server.stop(0);
        }
    }

    private GmailApiEmailSender createSender(HttpServer server) {
        int port = server.getAddress().getPort();
        return new GmailApiEmailSender(
                HttpClient.newHttpClient(),
                objectMapper,
                "client-id",
                "client-secret",
                "refresh-token",
                "canchavoleyservice@gmail.com",
                URI.create("http://127.0.0.1:" + port + "/token"),
                URI.create("http://127.0.0.1:" + port + "/send"));
    }
}
