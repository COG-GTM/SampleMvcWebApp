package com.northwind.scheduling.characterization.service;

import com.northwind.scheduling.characterization.spi.Model.SeatDrift;
import com.northwind.scheduling.characterization.spi.SeatDriftCheck;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** services/scheduling-service/db/checks/seat-drift.sql (db/checks/seat-drift.sql ported to the Scheduling schema), run verbatim. */
final class ServiceSeatDriftCheck implements SeatDriftCheck {

    private final SchedulingServiceTarget t;
    private final Path script;

    ServiceSeatDriftCheck(SchedulingServiceTarget t, Path script) {
        this.t = t;
        this.script = script;
    }

    @Override
    public String description() {
        return "services/scheduling-service/db/checks/seat-drift.sql";
    }

    @Override
    public List<SeatDrift> findDrift() {
        List<SeatDrift> rows = new ArrayList<>();
        try (Connection c = t.connectScheduling(); Statement st = c.createStatement()) {
            for (String batch : batches(Files.readString(script))) {
                boolean isResult = st.execute(batch);
                while (true) {
                    if (isResult) {
                        try (ResultSet rs = st.getResultSet()) {
                            while (rs.next()) {
                                rows.add(new SeatDrift(rs.getInt("SessionId"), rs.getInt("Capacity"), rs.getInt("SeatsAvailable"),
                                        rs.getInt("ActiveBookings"), rs.getInt("Drift")));
                            }
                        }
                    } else if (st.getUpdateCount() == -1) {
                        break;
                    }
                    isResult = st.getMoreResults();
                }
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException(e);
        }
        return rows;
    }

    private static List<String> batches(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (line.trim().equalsIgnoreCase("GO")) {
                if (!cur.toString().isBlank()) {
                    out.add(cur.toString());
                }
                cur.setLength(0);
            } else {
                cur.append(line).append('\n');
            }
        }
        if (!cur.toString().isBlank()) {
            out.add(cur.toString());
        }
        return out;
    }
}
