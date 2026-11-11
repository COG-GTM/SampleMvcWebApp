package com.northwind.assessment.gateway.routing;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Component;

import com.northwind.assessment.gateway.config.RoutingProperties;

/**
 * Holds the current allow-list. Each (re)load reads {@code routing.modern-centers}, {@code routing.test-centers}
 * and {@code routing.coexistence-mode} from the allow-list file ({@code routing.config-file}) when it exists and
 * defines them, otherwise from the Spring Environment. Reloads happen on {@code POST /actuator/refresh}
 * ({@link EnvironmentChangeEvent}) and when {@link com.northwind.assessment.gateway.config.ConfigFileWatcher}
 * sees the file change.
 * <p>
 * At startup an invalid list (see {@link AllowListPolicy}) stops the application. On a refresh an invalid list is
 * rejected: the previous list stays in force and the rejection is logged at ERROR.
 */
@Component
public class AllowListHolder {

    private static final Logger log = LoggerFactory.getLogger(AllowListHolder.class);

    private final Environment environment;
    private final java.nio.file.Path file;
    private final AtomicReference<AllowList> current = new AtomicReference<>();
    private volatile String lastRejection;

    public AllowListHolder(Environment environment, RoutingProperties props) {
        this.environment = environment;
        this.file = java.nio.file.Paths.get(props.getConfigFile()).toAbsolutePath();
        AllowList initial = read();
        List<String> errors = AllowListPolicy.violations(initial.modernCenters(), initial.testCenters(),
                initial.coexistenceMode());
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Refusing to start: " + String.join("; ", errors));
        }
        current.set(initial);
        log.info("routing allow-list modern-centers={} test-centers={} coexistence-mode={}",
                initial.modernCenters(), initial.testCenters(), initial.coexistenceMode());
    }

    public AllowList get() {
        return current.get();
    }

    public String getLastRejection() {
        return lastRejection;
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange() {
        reload();
    }

    /** @return true when the new list was applied */
    public synchronized boolean reload() {
        AllowList next;
        try {
            next = read();
        } catch (RuntimeException e) {
            lastRejection = e.getMessage();
            log.error("routing allow-list refresh REJECTED, keeping modern-centers={}: {}",
                    current.get().modernCenters(), lastRejection);
            return false;
        }
        List<String> errors = AllowListPolicy.violations(next.modernCenters(), next.testCenters(),
                next.coexistenceMode());
        if (!errors.isEmpty()) {
            lastRejection = String.join("; ", errors);
            log.error("routing allow-list refresh REJECTED, keeping modern-centers={}: {}",
                    current.get().modernCenters(), lastRejection);
            return false;
        }
        lastRejection = null;
        AllowList prev = current.getAndSet(next);
        if (!prev.equals(next)) {
            log.info("routing allow-list changed modern-centers={} -> {} (coexistence-mode={})",
                    prev.modernCenters(), next.modernCenters(), next.coexistenceMode());
        }
        return true;
    }

    private AllowList read() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        java.util.Properties fromFile = readFile();
        if (!fromFile.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("routing-file", toMap(fromFile)));
        }
        env.getPropertySources().addLast(new org.springframework.core.env.PropertySource<Environment>("spring", environment) {
            @Override
            public Object getProperty(String name) {
                return getSource().getProperty(name);
            }
        });
        Binder binder = Binder.get(env);
        Set<String> modern = AllowListPolicy.normalize(
                binder.bind("routing.modern-centers", Bindable.listOf(String.class)).orElse(List.of()));
        Set<String> test = AllowListPolicy.normalize(
                binder.bind("routing.test-centers", Bindable.listOf(String.class)).orElse(List.of("BOS01", "SEA01", "AKL01")));
        String mode = binder.bind("routing.coexistence-mode", String.class).orElse("none");
        return new AllowList(modern, test, mode);
    }

    private java.util.Properties readFile() {
        java.util.Properties p = new java.util.Properties();
        if (java.nio.file.Files.isRegularFile(file)) {
            try (java.io.Reader r = java.nio.file.Files.newBufferedReader(file)) {
                p.load(r);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("cannot read allow-list file " + file + ": " + e.getMessage(), e);
            }
        }
        return p;
    }

    private static java.util.Map<String, Object> toMap(java.util.Properties p) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        p.forEach((k, v) -> m.put(String.valueOf(k), String.valueOf(v).trim()));
        return m;
    }
}
