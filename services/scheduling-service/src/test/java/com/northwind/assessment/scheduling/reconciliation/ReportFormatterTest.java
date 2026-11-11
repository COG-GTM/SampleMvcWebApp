package com.northwind.assessment.scheduling.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.northwind.assessment.scheduling.reconciliation.ReconciliationService.Mismatch;
import com.northwind.assessment.scheduling.reconciliation.ReconciliationService.RelayStatus;
import com.northwind.assessment.scheduling.reconciliation.ReconciliationService.Report;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportFormatterTest {

    private static final RelayStatus RELAY = new RelayStatus(0, 10L, 10L, 120L, 400L, 10, null);
    private static final List<String> COLS = List.of("SessionId", "Drift");

    @Test
    void cleanRun_reportsZeroMismatchesAndZeroDriftRows() {
        Report r = new Report(LocalDateTime.of(2026, 11, 10, 9, 0), 462, 3600, 0, List.of(), COLS, List.of(), RELAY, null);
        String md = ReportFormatter.markdown(r);
        assertThat(md).contains("| **Mismatches** | **0** |").contains("| **db/checks/seat-drift.sql rows** | **0** |")
                .contains("## Mismatches\n\nNone.").contains("| Relay lag p95 / max (ms) | 120 / 400 |");
    }

    @Test
    void mismatchesAndDrift_areListedAndPipesEscaped() {
        Report r = new Report(LocalDateTime.of(2026, 11, 10, 9, 0), 2, 5, 1,
                List.of(new Mismatch(7, null, "SeatsAvailable", "3", "2"), new Mismatch(7, "1234", "Status", "BOOKED", "a|b")),
                COLS, List.of(List.of("7", "-1")), RELAY, null);
        String md = ReportFormatter.markdown(r);
        assertThat(md).contains("| **Mismatches** | **2** |").contains("| 7 |  | SeatsAvailable | 3 | 2 |")
                .contains("| 7 | 1234 | Status | BOOKED | a\\|b |").contains("| SessionId | Drift |").contains("| 7 | -1 |");
    }

    @Test
    void bundledSeatDriftQuery_isTheRepositoryCheck() throws Exception {
        String bundled = new String(getClass().getResourceAsStream("/reconciliation/seat-drift.sql").readAllBytes()).strip();
        String repo = Files.readString(Path.of("..", "..", "db", "checks", "seat-drift.sql"));
        assertThat(repo.replace("\r", "")).contains(bundled.replace("\r", ""));
    }
}
