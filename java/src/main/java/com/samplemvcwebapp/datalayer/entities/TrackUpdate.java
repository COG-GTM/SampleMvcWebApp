package com.samplemvcwebapp.datalayer.entities;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Mirrors DataLayer.DataClasses.Concrete.Helpers.TrackUpdate: LastUpdated (UTC) is set on create and on every save. */
@MappedSuperclass
public abstract class TrackUpdate {

    @Column(name = "LastUpdated", nullable = false)
    protected LocalDateTime lastUpdated;

    protected TrackUpdate() {
        updateTrackingInfo();
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    /** Legacy UpdateTrackingInfo(): called by SampleWebAppDb.SaveChanges for every added/modified entity. */
    @PrePersist
    @PreUpdate
    public void updateTrackingInfo() {
        lastUpdated = LocalDateTime.now(ZoneOffset.UTC);
    }
}
