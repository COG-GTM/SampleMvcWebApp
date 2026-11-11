package com.northwind.routing.translate;

import com.northwind.routing.config.RoutingProperties;
import com.northwind.routing.route.ApiRoutes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Serves an allow-listed booking-api v1 call from scheduling-service {@code /api/v2} and converts the answer
 * back to the v1 shape and status code (booking-api: POST /bookings 201, everything else 200).
 */
@Component
public class ModernV1Client {

    private static final Logger log = LoggerFactory.getLogger(ModernV1Client.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    public record V1Response(int status, Object body) {
    }

    private final WebClient client;

    public ModernV1Client(WebClient.Builder builder, RoutingProperties props) {
        this.client = builder.baseUrl(props.getSchedulingServiceUrl()).build();
    }

    public Mono<V1Response> call(ApiRoutes.V1 m, MultiValueMap<String, String> query, Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Mono<V1Response> out = switch (m.op()) {
            case SESSIONS -> client.get().uri(u -> u.path("/api/v2/centers/{c}/sessions")
                            .queryParamIfPresent("examCode", opt(query, "examCode"))
                            .queryParamIfPresent("from", opt(query, "from"))
                            .queryParamIfPresent("to", opt(query, "to"))
                            .build(m.lookup().value()))
                    .exchangeToMono(r -> list(r, V1Shapes::sessions));
            case SESSION -> client.get().uri("/api/v2/sessions/{id}", m.id())
                    .exchangeToMono(r -> one(r, 200, V1Shapes::session));
            case GET_BOOKING -> client.get().uri("/api/v2/bookings/{id}", m.id())
                    .exchangeToMono(r -> one(r, 200, V1Shapes::booking));
            case BOOK -> {
                Map<String, Object> req = new LinkedHashMap<>();
                req.put("sessionId", b.get("sessionId"));
                req.put("candidateNumber", b.get("candidateNumber"));
                req.put("accommodationCode", b.get("accommodationCode"));
                req.put("externalRef", b.get("externalRef"));
                req.put("channel", "PARTNER");
                yield client.post().uri("/api/v2/bookings").contentType(MediaType.APPLICATION_JSON).bodyValue(req)
                        .exchangeToMono(r -> one(r, 201, V1Shapes::outcome));
            }
            case CANCEL -> {
                String reason = opt(query, "reason").orElse("Cancelled by partner");
                yield client.delete().uri(u -> u.path("/api/v2/bookings/{id}").queryParam("reason", reason).build(m.id()))
                        .exchangeToMono(r -> one(r, 200, V1Shapes::booking));
            }
            case RESCHEDULE -> {
                Map<String, Object> req = new LinkedHashMap<>();
                req.put("newSessionId", b.get("newSessionId"));
                yield client.post().uri("/api/v2/bookings/{id}/reschedule", m.id()).contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(req).exchangeToMono(r -> one(r, 200, V1Shapes::outcome));
            }
        };
        return out.onErrorResume(e -> {
            log.error("scheduling-service call for {} failed", m, e);
            return Mono.just(new V1Response(HttpStatus.BAD_GATEWAY.value(),
                    V1Shapes.error("UPSTREAM_UNAVAILABLE", "scheduling-service unavailable: " + e.getMessage())));
        });
    }

    private static Mono<V1Response> one(ClientResponse r, int okStatus, java.util.function.Function<Map<String, Object>, Object> shape) {
        if (r.statusCode().is2xxSuccessful()) {
            return r.bodyToMono(MAP).map(body -> new V1Response(okStatus, shape.apply(body)));
        }
        return error(r);
    }

    private static Mono<V1Response> list(ClientResponse r, java.util.function.Function<List<Map<String, Object>>, Object> shape) {
        if (r.statusCode().is2xxSuccessful()) {
            return r.bodyToMono(LIST).defaultIfEmpty(List.of()).map(body -> new V1Response(200, shape.apply(body)));
        }
        return error(r);
    }

    private static Mono<V1Response> error(ClientResponse r) {
        int status = r.statusCode().value();
        return r.bodyToMono(MAP).onErrorResume(e -> Mono.empty()).defaultIfEmpty(Map.of())
                .map(body -> new V1Response(status, V1Shapes.error(body)));
    }

    private static java.util.Optional<String> opt(MultiValueMap<String, String> query, String name) {
        return java.util.Optional.ofNullable(com.northwind.routing.route.PortalRoutes.param(query, name));
    }
}
