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

    public ConfigFileWatcher(AllowListHolder allowList, RoutingProperties props) {
        this.allowList = allowList;
        this.file = Paths.get(props.getConfigFile()).toAbsolutePath();
        this.interval = props.getWatchInterval();
        log.info("watching {} for allow-list changes ({})", file, Files.exists(file) ? "present" : "absent");
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new org.springframework.scheduling.config.FixedDelayTask(this::poll, interval, interval));
    }

    public void poll() {
        // compare with what the holder last loaded (not what this watcher last saw), so a refresh in between
        // cannot mask a later edit that restores the earlier content
        String now;
        try {
            now = Files.exists(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            log.warn("cannot read {}: {}", file, e.getMessage());
            return;
        }
        if (!java.util.Objects.equals(now, allowList.getLoadedFileContent())) {
            boolean applied = allowList.reload();
            log.info("{} changed, allow-list {}", file, applied ? "reloaded" : "rejected (previous list kept)");
        }
    }
}
