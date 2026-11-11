package com.northwind.routing.policy;

import com.northwind.routing.config.RoutingProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Polls {@code routing.config-file} (mtime + size, default every 1 s; polling behaves the same on Windows and
 * Linux) and triggers the same refresh as {@code POST /actuator/refresh} when it changes.
 */
@Component
public class ConfigFileWatcher implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileWatcher.class);

    private final ContextRefresher refresher;
    private final Path file;
    private final long intervalMs;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "routing-config-watcher");
        t.setDaemon(true);
        return t;
    });
    private volatile String stamp;

    public ConfigFileWatcher(ContextRefresher refresher, RoutingProperties props) {
        this.refresher = refresher;
        this.file = Path.of(props.getConfigFile()).toAbsolutePath().normalize();
        this.intervalMs = Math.max(100, props.getConfigPollInterval().toMillis());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        stamp = stamp();
        log.info("Watching {} for allow-list changes every {} ms", file, intervalMs);
        executor.scheduleWithFixedDelay(this::poll, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    void poll() {
        String now = stamp();
        if (Objects.equals(now, stamp)) {
            return;
        }
        stamp = now;
        try {
            log.info("{} changed; refreshed config keys {}", file, refresher.refresh());
        } catch (RuntimeException e) {
            log.error("Config refresh after {} change failed", file, e);
        }
    }

    private String stamp() {
        try {
            return Files.getLastModifiedTime(file).toMillis() + ":" + Files.size(file);
        } catch (IOException e) {
            return "missing";
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
