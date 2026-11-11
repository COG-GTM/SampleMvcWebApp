package com.northwind.assessment.scheduling.importer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Batched upserts keyed by the legacy id. {@code source_version} is a checksum of the copied
 * legacy columns (legacy tables carry no rowversion); upserts make re-imports idempotent.
 */
@Component
class SnapshotWriter {

    private static final int BATCH = 500;

    private final JdbcTemplate jdbc;

    SnapshotWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static long sourceVersion(Object... values) {
        CRC32 crc = new CRC32();
        crc.update(Arrays.stream(values).map(String::valueOf).collect(Collectors.joining("\u001f"))
                .getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }

    /**
     * MERGE rows into {@code table}. {@code columns[0]} is the key; the last two columns must be
     * {@code source_version, synced_at}.
     */
    int upsertSnapshot(String table, String[] columns, List<Object[]> rows) {
        String key = columns[0];
        String colList = String.join(", ", columns);
        String placeholders = String.join(", ", java.util.Collections.nCopies(columns.length, "?"));
        String updates = Arrays.stream(columns).skip(1).map(c -> "t." + c + " = s." + c).collect(Collectors.joining(", "));
        String sql = "MERGE " + table + " WITH (HOLDLOCK) AS t "
                + "USING (VALUES (" + placeholders + ")) AS s (" + colList + ") ON t." + key + " = s." + key + " "
                + "WHEN MATCHED THEN UPDATE SET " + updates + " "
                + "WHEN NOT MATCHED THEN INSERT (" + colList + ") VALUES ("
                + Arrays.stream(columns).map(c -> "s." + c).collect(Collectors.joining(", ")) + ");";
        return batch(sql, rows);
    }

    int batch(String sql, List<Object[]> rows) {
        int n = 0;
        for (int i = 0; i < rows.size(); i += BATCH) {
            List<Object[]> chunk = new ArrayList<>(rows.subList(i, Math.min(rows.size(), i + BATCH)));
            jdbc.batchUpdate(sql, chunk);
            n += chunk.size();
        }
        return n;
    }

    static Object[] row(Object... values) {
        return Objects.requireNonNull(values);
    }
}
