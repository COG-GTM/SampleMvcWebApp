package com.northwind.assessment.gateway.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import com.northwind.assessment.gateway.routing.AllowListHolder;

/**
 * Polls {@code routing.config-file} and reloads the allow-list when its content changes, so editing the file is
 * enough to roll a center forward or back (no restart). {@code POST /actuator/refresh} has the same effect.
 */
@Component
public class ConfigFileWatcher implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileWatcher.class);

    private final AllowListHolder allowList;
    private final Path file;
    private final java.time.Duration interval;
    private volatile String lastContent;

    public ConfigFileWatcher(AllowListHolder allowList, RoutingProperties props) {
        this.allowList = allowList;
        this.file = Paths.get(props.getConfigFile()).toAbsolutePath();
        this.interval = props.getWatchInterval();
        this.lastContent = read();
        log.info("watching {} for allow-list changes ({})", file, lastContent == null ? "absent" : "present");
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new org.springframework.scheduling.config.FixedDelayTask(this::poll, interval, interval));
    }

    public void poll() {
        String now = read();
        if (!java.util.Objects.equals(now, lastContent)) {
            lastContent = now;
            boolean applied = allowList.reload();
            log.info("{} changed, allow-list {}", file, applied ? "reloaded" : "rejected (previous list kept)");
        }
    }

    private String read() {
        try {
            return Files.exists(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            log.warn("cannot read {}: {}", file, e.getMessage());
            return lastContent;
        }
    }
}
