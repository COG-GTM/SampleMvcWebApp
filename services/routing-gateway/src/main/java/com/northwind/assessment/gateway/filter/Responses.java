package com.northwind.assessment.gateway.filter;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.northwind.assessment.gateway.translate.V1Shapes;

import reactor.core.publisher.Mono;

final class Responses {

    /** booking-api (Spring Boot 1.5) sends exactly this content type. */
    static final String V1_JSON = "application/json;charset=UTF-8";

    private Responses() {
    }

    static Mono<Void> json(ServerWebExchange exchange, HttpStatusCode status, JsonNode body) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, V1_JSON);
        byte[] bytes = V1Shapes.write(body);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    static Mono<Void> redirect(ServerWebExchange exchange, boolean post, String location) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(post ? HttpStatus.SEE_OTHER : HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(location));
        return response.setComplete();
    }
}
