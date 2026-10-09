package com.canchavoley.backend.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResendEmailSenderTest {

    @Test
    void postsEmailAsJsonUsingBearerAuthentication() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();

        try {
            ResendEmailSender sender = new ResendEmailSender(
                    HttpClient.newHttpClient(),
                    "test-api-key",
                    "CanchaVoley <reservas@example.com>",
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails"));

            sender.enviar("cliente@example.com", "Código", "Primera línea\nSegunda \"línea\"");

            assertEquals("Bearer test-api-key", authorization.get());
            assertEquals("application/json", contentType.get());
            assertEquals(
                    "{\"from\":\"CanchaVoley <reservas@example.com>\","
                            + "\"to\":[\"cliente@example.com\"],"
                            + "\"subject\":\"Código\","
                            + "\"text\":\"Primera línea\\nSegunda \\\"línea\\\"\"}",
                    requestBody.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsProviderStatusWithoutExposingResponseBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            byte[] response = "private provider details".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            ResendEmailSender sender = new ResendEmailSender(
                    HttpClient.newHttpClient(),
                    "test-api-key",
                    "reservas@example.com",
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails"));

            ResendEmailSender.DeliveryException error = assertThrows(
                    ResendEmailSender.DeliveryException.class,
                    () -> sender.enviar("cliente@example.com", "Código", "123456"));

            assertEquals(403, error.statusCode());
            assertEquals("Resend respondió HTTP 403", CorreoRecuperacionService.diagnosticoSeguro(error));
        } finally {
            server.stop(0);
        }
    }
}
