package com.northwind.assessment.gateway.resolve;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.northwind.assessment.gateway.config.RoutingProperties;

import reactor.core.publisher.Mono;

/**
 * Cached client for scheduling-service's {@code /internal/routing/resolve}. Entries live for the response's
 * {@code Cache-Control: max-age} (contract: 60 s for center/session lookups); responses without max-age
 * (booking lookups) use {@code routing.resolve-default-ttl} (0 = not cached). 404 = "not known to modern"
 * and is never cached.
 */
@Component
public class CenterResolver {

    private static final Logger log = LoggerFactory.getLogger(CenterResolver.class);
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

    private final WebClient client;
    private final Duration timeout;
    private final Duration defaultTtl;
    private final Clock clock;
    private final ConcurrentHashMap<ResolveKey, Entry> cache = new ConcurrentHashMap<>();

    private record Entry(ResolvedCenter value, Instant expires) { }

    @org.springframework.beans.factory.annotation.Autowired
    public CenterResolver(WebClient.Builder builder, RoutingProperties props) {
        this(builder.baseUrl(props.getSchedulingServiceUrl().toString()).build(),
                props.getResolveTimeout(), props.getResolveDefaultTtl(), Clock.systemUTC());
    }

    CenterResolver(WebClient client, Duration timeout, Duration defaultTtl, Clock clock) {
        this.client = client;
        this.timeout = timeout;
        this.defaultTtl = defaultTtl;
        this.clock = clock;
    }

    /**
     * @return the resolved center, empty when scheduling-service answers 404; errors (timeout, 5xx, connection
     *         refused) are signalled as {@link ResolveUnavailableException}
     */
    public Mono<Optional<ResolvedCenter>> resolve(ResolveKey key) {
        Entry hit = cache.get(key);
        if (hit != null && hit.expires().isAfter(clock.instant())) {
            return Mono.just(Optional.of(hit.value()));
        }
        return client.get()
                .uri(b -> b.path("/internal/routing/resolve").queryParam(key.param(), key.value()).build())
                .retrieve()
                .toEntity(ResolvedCenter.class)
                .timeout(timeout)
                .map(entity -> {
                    store(key, entity);
                    return Optional.ofNullable(entity.getBody());
                })
                .onErrorResume(WebClientResponseException.class, e -> e.getStatusCode() == HttpStatus.NOT_FOUND
                        ? Mono.just(Optional.empty())
                        : Mono.error(new ResolveUnavailableException(key, e)))
                .onErrorMap(e -> !(e instanceof ResolveUnavailableException), e -> new ResolveUnavailableException(key, e))
                .doOnError(e -> log.warn("resolve {}={} failed: {}", key.param(), key.value(), e.getMessage()));
    }

    public void clear() {
        cache.clear();
    }

    private void store(ResolveKey key, ResponseEntity<ResolvedCenter> entity) {
        if (entity.getBody() == null) {
            return;
        }
        Duration ttl = defaultTtl;
        String cc = entity.getHeaders().getCacheControl();
        if (cc != null) {
            Matcher m = MAX_AGE.matcher(cc);
            if (m.find()) {
                ttl = Duration.ofSeconds(Long.parseLong(m.group(1)));
            }
        }
        if (!ttl.isZero() && !ttl.isNegative()) {
            cache.put(key, new Entry(entity.getBody(), clock.instant().plus(ttl)));
        }
    }
}
