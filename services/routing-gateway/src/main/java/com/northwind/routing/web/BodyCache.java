package com.northwind.routing.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Reads a request body once so the strangler can inspect it and still forward it unchanged. */
final class BodyCache {

    static final String ATTR = BodyCache.class.getName() + ".bytes";

    private BodyCache() {
    }

    static Mono<ServerWebExchange> cache(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(buf -> {
                    byte[] bytes = new byte[buf.readableByteCount()];
                    buf.read(bytes);
                    DataBufferUtils.release(buf);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0])
                .map(bytes -> {
                    ServerHttpRequest replay = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<org.springframework.core.io.buffer.DataBuffer> getBody() {
                            return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(bytes)));
                        }
                    };
                    ServerWebExchange next = exchange.mutate().request(replay).build();
                    next.getAttributes().put(ATTR, bytes);
                    return next;
                });
    }

    static byte[] bytes(ServerWebExchange exchange) {
        byte[] b = exchange.getAttribute(ATTR);
        return b == null ? new byte[0] : b;
    }

    /** Value of an application/x-www-form-urlencoded field (name matched case-insensitively, as MVC binding does). */
    static String formField(byte[] body, String name) {
        String text = new String(body, StandardCharsets.UTF_8);
        for (String pair : text.split("&")) {
            int eq = pair.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            if (k.equalsIgnoreCase(name)) {
                String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8).trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return null;
    }
}
