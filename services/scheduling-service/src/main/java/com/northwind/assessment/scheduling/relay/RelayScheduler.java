package com.northwind.assessment.scheduling.relay;

import com.northwind.assessment.scheduling.config.SchedulingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the outbox every {@code scheduling.relay.poll-interval-ms} when {@code scheduling.relay.enabled=true}. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.relay.enabled", havingValue = "true")
public class RelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(RelayScheduler.class);

    private final OutboxRelay relay;
    private final int batchSize;

    public RelayScheduler(OutboxRelay relay, SchedulingProperties props) {
        this.relay = relay;
        this.batchSize = props.relay().batchSize();
        log.info("outbox relay enabled: {} as {}, every {} ms", props.relay().url(), props.relay().username(),
                props.relay().pollIntervalMs());
    }

    @Scheduled(fixedDelayString = "${scheduling.relay.poll-interval-ms:200}", initialDelay = 1000)
    public void poll() {
        OutboxRelay.RunResult r = relay.drain(batchSize);
        if (r.applied() + r.replayed() > 0) {
            log.info("relay: applied={} replayed={} failed={}", r.applied(), r.replayed(), r.failed());
        }
    }
}
