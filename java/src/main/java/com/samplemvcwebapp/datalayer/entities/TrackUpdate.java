package com.samplemvcwebapp.datalayer.entities;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;

/** Mirrors DataLayer.DataClasses.Concrete.Helpers.TrackUpdate: LastUpdated (UTC) is set on create and on every save. */
@MappedSuperclass
public abstract class TrackUpdate {

    @Column(name = "LastUpdated", nullable = false)
    protected LocalDateTime lastUpdated;

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
}
