package com.northwind.scheduling.contract;

import com.northwind.scheduling.contract.support.LegacyEstate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tripwire: every cross-boundary access cited in docs/to-be/scheduling-boundary.md (by file:line) is still at that
 * line with the same text. No database needed. Phase 2 rewiring is expected to fail rows here; update the
 * inventory and inventory-guard.psv in the same change.
 */
@DisplayName("Inventory guard: cited cross-boundary SQL/LINQ lines are unchanged")
class InventoryGuardTest {

    record Entry(String id, String file, int line, String text) {
        @Override
        public String toString() {
            return id + " " + file.substring(file.lastIndexOf('/') + 1) + ":" + line;
        }
    }

    static List<Entry> entries() throws IOException {
        try (InputStream in = InventoryGuardTest.class.getResourceAsStream("/inventory-guard.psv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .map(l -> l.split("\\|", 4))
                    .map(p -> new Entry(p[0], p[1], Integer.parseInt(p[2]), p[3]))
                    .toList();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entries")
    void citedLineUnchanged(Entry e) throws IOException {
        Path file = LegacyEstate.repoRoot().resolve(e.file());
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertThat(lines.size()).as(e.file()).isGreaterThanOrEqualTo(e.line());
        assertThat(lines.get(e.line() - 1).strip()).as(e.toString()).isEqualTo(e.text());
    }
}
