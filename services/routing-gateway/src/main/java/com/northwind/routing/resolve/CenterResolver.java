package com.northwind.routing.resolve;

import com.northwind.routing.config.RoutingProperties;
import com.northwind.routing.route.Lookup;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Resolves a request's test centre through scheduling-service
 * ({@code GET /api/v2/internal/routing/resolve?centerCode=|centerId=|sessionId=|bookingId=}, which reads its
 * snapshots). Answers are cached for {@code routing.resolve-cache-ttl}; failures are not cached and resolve to
 * "unknown", which routes to legacy (fail safe).
 */
@Component
public class CenterResolver {

    private static final Logger log = LoggerFactory.getLogger(CenterResolver.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private record Entry(Optional<String> center, long expiresAt) {
    }

    private final WebClient client;
    private final Duration ttl;
    private final Duration timeout;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public CenterResolver(WebClient.Builder builder, RoutingProperties props) {
        this.client = builder.baseUrl(props.getSchedulingServiceUrl()).build();
        this.ttl = props.getResolveCacheTtl();
        this.timeout = props.getResolveTimeout();
    }

    public Mono<Optional<String>> resolve(Lookup lookup) {
        if (lookup == null || !lookup.present()) {
            return Mono.just(Optional.empty());
        }
        String key = lookup.kind().param() + "=" + lookup.value();
        Entry hit = cache.get(key);
        if (hit != null && hit.expiresAt() > System.currentTimeMillis()) {
            return Mono.just(hit.center());
        }
        return client.get()
                .uri(u -> u.path("/api/v2/internal/routing/resolve").queryParam(lookup.kind().param(), lookup.value()).build())
                .retrieve()
                .bodyToMono(MAP)
                .map(body -> Optional.ofNullable((String) body.get("centerCode")).filter(s -> !s.isBlank()))
                .timeout(timeout)
                .doOnNext(c -> cache.put(key, new Entry(c, System.currentTimeMillis() + ttl.toMillis())))
                .onErrorResume(WebClientResponseException.NotFound.class, e -> {
                    Optional<String> none = Optional.empty();
                    cache.put(key, new Entry(none, System.currentTimeMillis() + ttl.toMillis()));
                    return Mono.just(none);
                })
                .onErrorResume(e -> {
                    log.warn("Centre lookup {} failed ({}); routing to legacy", key, e.toString());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty());
    }

    public void clear() {
        cache.clear();
    }
}
