package com.northwind.scheduling.characterization.legacy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.northwind.scheduling.characterization.spi.Model.SeatDrift;
import com.northwind.scheduling.characterization.spi.SeatDriftCheck;

/** Executes the repo's db/checks/seat-drift.sql verbatim (batch by batch) and maps its rows. */
final class LegacySeatDriftSqlCheck implements SeatDriftCheck {

    private final LegacyProcTarget target;
    private final Path script;

    LegacySeatDriftSqlCheck(LegacyProcTarget target, Path script) {
        this.target = target;
        this.script = script;
    }

    @Override
    public String description() {
        return "db/checks/seat-drift.sql";
    }

    @Override
    public List<SeatDrift> findDrift() {
        List<String> batches = SqlScript.batches(script);
        return target.withConnection(c -> run(c, batches));
    }

    private static List<SeatDrift> run(Connection c, List<String> batches) throws SQLException {
        List<SeatDrift> rows = new ArrayList<>();
        try (Statement st = c.createStatement()) {
            for (String batch : batches) {
                boolean isResult = st.execute(batch);
                while (true) {
                    if (isResult) {
                        try (ResultSet rs = st.getResultSet()) {
                            while (rs.next()) {
                                rows.add(new SeatDrift(rs.getInt("SessionId"), rs.getInt("Capacity"),
                                        rs.getInt("SeatsAvailable"), rs.getInt("ActiveBookings"), rs.getInt("Drift")));
                            }
                        }
                    } else if (st.getUpdateCount() == -1) {
                        break;
                    }
                    isResult = st.getMoreResults();
                }
            }
        }
        return rows;
    }
}
