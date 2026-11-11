package com.northwind.scheduling.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.northwind.scheduling.domain.CapacityImpact;

class LoggingNotificationAdapterTest {

    static CandidateCapacityImpactNotified event(UUID id, String candidate, int bookingId) {
        return new CandidateCapacityImpactNotified(id, 154, "SFO01", "MHL-RN1", LocalDateTime.of(2026, 11, 12, 13, 30), 20, 8,
                candidate, bookingId, "BOOKED", "WAITLISTED", CapacityImpact.Kind.STATUS_CHANGED);
    }

    @Test
    void sameEventIdDeliveredTwiceIsRecordedOnce() {
        LoggingNotificationAdapter stub = new LoggingNotificationAdapter(100);
        CandidateCapacityImpactNotified n = event(UUID.randomUUID(), "NW00000909", 9);

        assertThat(stub.notify(n)).isTrue();
        assertThat(stub.notify(n)).isFalse();

        assertThat(stub.sent("NW00000909")).containsExactly(n);
    }

    @Test
    void filtersByCandidateNumberCaseInsensitivelyAndKeepsDeliveryOrder() {
        LoggingNotificationAdapter stub = new LoggingNotificationAdapter(100);
        CandidateCapacityImpactNotified a = event(UUID.randomUUID(), "NW00000901", 1);
        CandidateCapacityImpactNotified b = event(UUID.randomUUID(), "NW00000902", 2);
        CandidateCapacityImpactNotified c = event(UUID.randomUUID(), "NW00000901", 3);
        stub.notify(a);
        stub.notify(b);
        stub.notify(c);

        assertThat(stub.sent(" nw00000901 ")).containsExactly(a, c);
        assertThat(stub.sent(null)).containsExactly(a, b, c);
        assertThat(stub.sent("")).hasSize(3);
        assertThat(stub.sent("NW00000999")).isEmpty();
    }

    @Test
    void keepsOnlyTheNewestMaxRecords() {
        LoggingNotificationAdapter stub = new LoggingNotificationAdapter(2);
        CandidateCapacityImpactNotified a = event(UUID.randomUUID(), "NW00000901", 1);
        CandidateCapacityImpactNotified b = event(UUID.randomUUID(), "NW00000902", 2);
        CandidateCapacityImpactNotified c = event(UUID.randomUUID(), "NW00000903", 3);
        stub.notify(a);
        stub.notify(b);
        stub.notify(c);

        assertThat(stub.sent(null)).containsExactly(b, c);
    }
}
