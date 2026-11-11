package com.northwind.routing.policy;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Live allow-list. Validated at construction (an invalid list refuses startup) and re-read on every
 * {@link EnvironmentChangeEvent} ({@code POST /actuator/refresh} or {@link ConfigFileWatcher}); an invalid
 * list on refresh is rejected and the previous one kept.
 */
@Component
public class AllowList implements ApplicationListener<EnvironmentChangeEvent> {

    private static final Logger log = LoggerFactory.getLogger(AllowList.class);

    private final Environment env;
    private volatile Set<String> centers;

    public AllowList(Environment env) {
        this.env = env;
        this.centers = load();
        log.info("routing.modern-centers={} coexistence-mode={}", centers, mode());
    }

    public Set<String> centers() {
        return centers;
    }

    public boolean isEmpty() {
        return centers.isEmpty();
    }

    /** {@code *} (only permitted with {@code routing.coexistence-mode=relay}, MBA-3823) matches every centre. */
    public boolean contains(String centerCode) {
        return centerCode != null && (centers.contains(AllowListPolicy.ALL)
                || centers.contains(centerCode.trim().toUpperCase(Locale.ROOT)));
    }

    @Override
    public void onApplicationEvent(EnvironmentChangeEvent event) {
        try {
            Set<String> next = load();
            if (!next.equals(centers)) {
                log.info("routing.modern-centers reloaded: {} -> {}", centers, next);
            }
            centers = next;
        } catch (IllegalStateException e) {
            log.error("Rejected allow-list reload, keeping {}: {}", centers, e.getMessage());
        }
    }

    private Set<String> load() {
        Binder binder = Binder.get(env);
        List<String> listed = binder.bind("routing.modern-centers", Bindable.listOf(String.class)).orElse(List.of());
        List<String> test = binder.bind("routing.test-centers", Bindable.listOf(String.class)).orElse(List.of());
        return AllowListPolicy.validate(listed, test, mode());
    }

    private String mode() {
        return env.getProperty("routing.coexistence-mode", "none");
    }
}
