package com.northwind.scheduling.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** AuditLog writer. A null actor falls back to the column default SUSER_SNAME(), as ISNULL(@Actor, SUSER_SNAME()) did (HB-17). */
@Component
public class Audit {

    private final JdbcTemplate jdbc;

    public Audit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void write(String entity, int entityId, String action, String detail, String actor) {
        if (actor == null) {
            jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail) VALUES (?, ?, ?, ?)",
                    entity, entityId, action, detail);
        } else {
            jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                    entity, entityId, action, detail, actor);
        }
    }
}
