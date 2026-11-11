package com.northwind.routing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Minimal upstream stub (JDK HttpServer) that records what it received. */
final class Stub {

    record Call(String method, String uri, String body) {
    }

    record Answer(int status, String contentType, String body, String location) {
        Answer(int status, String contentType, String body) {
            this(status, contentType, body, null);
        }

        static Answer json(int status, String body) {
            return new Answer(status, "application/json", body);
        }

        static Answer redirect(int status, String location) {
            return new Answer(status, "text/plain", "", location);
        }
    }

    final HttpServer server;
    final List<Call> calls = new CopyOnWriteArrayList<>();

    Stub(Function<Call, Answer> handler) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", ex -> respond(ex, handler));
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Call> calls(String method, String uriPrefix) {
        return calls.stream().filter(c -> c.method().equals(method) && c.uri().startsWith(uriPrefix)).toList();
    }

    private void respond(HttpExchange ex, Function<Call, Answer> handler) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Call call = new Call(ex.getRequestMethod(), ex.getRequestURI().toString(), body);
        calls.add(call);
        Answer a = handler.apply(call);
        byte[] out = a.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", a.contentType());
        if (a.location() != null) {
            ex.getResponseHeaders().set("Location", a.location());
        }
        ex.sendResponseHeaders(a.status(), out.length == 0 ? -1 : out.length);
        if (out.length > 0) {
            ex.getResponseBody().write(out);
        }
        ex.close();
    }
}
