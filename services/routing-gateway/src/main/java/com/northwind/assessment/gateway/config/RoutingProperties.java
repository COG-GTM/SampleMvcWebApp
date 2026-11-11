package com.northwind.assessment.gateway.config;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Static gateway settings (upstream URLs, timeouts). The allow-list itself ({@code routing.modern-centers},
 * {@code routing.test-centers}, {@code routing.coexistence-mode}) is re-read from the Environment by
 * {@link com.northwind.assessment.gateway.routing.AllowListHolder} on every refresh.
 */
@ConfigurationProperties("routing")
public class RoutingProperties {

    private URI portalUrl = URI.create("http://localhost:8080");
    private URI bookingApiUrl = URI.create("http://localhost:8081");
    private URI resultsServiceUrl = URI.create("http://localhost:8082");
    private URI capacitySyncUrl = URI.create("http://localhost:8083");
    private URI schedulingServiceUrl = URI.create("http://localhost:8090");

    private List<String> modernCenters = new ArrayList<>();
    private List<String> testCenters = new ArrayList<>(List.of("BOS01", "SEA01", "AKL01"));
    private String coexistenceMode = "none";

    private long modernBookingIdFloor = 50_000_000L;
    private Duration resolveTimeout = Duration.ofSeconds(2);
    private Duration resolveDefaultTtl = Duration.ZERO;

    /** Watched allow-list file; a change triggers a config refresh (same effect as POST /actuator/refresh). */
    private String configFile = "config/routing.properties";
    private Duration watchInterval = Duration.ofSeconds(1);

    public URI getPortalUrl() { return portalUrl; }
    public void setPortalUrl(URI v) { this.portalUrl = v; }
    public URI getBookingApiUrl() { return bookingApiUrl; }
    public void setBookingApiUrl(URI v) { this.bookingApiUrl = v; }
    public URI getResultsServiceUrl() { return resultsServiceUrl; }
    public void setResultsServiceUrl(URI v) { this.resultsServiceUrl = v; }
    public URI getCapacitySyncUrl() { return capacitySyncUrl; }
    public void setCapacitySyncUrl(URI v) { this.capacitySyncUrl = v; }
    public URI getSchedulingServiceUrl() { return schedulingServiceUrl; }
    public void setSchedulingServiceUrl(URI v) { this.schedulingServiceUrl = v; }
    public List<String> getModernCenters() { return modernCenters; }
    public void setModernCenters(List<String> v) { this.modernCenters = v; }
    public List<String> getTestCenters() { return testCenters; }
    public void setTestCenters(List<String> v) { this.testCenters = v; }
    public String getCoexistenceMode() { return coexistenceMode; }
    public void setCoexistenceMode(String v) { this.coexistenceMode = v; }
    public long getModernBookingIdFloor() { return modernBookingIdFloor; }
    public void setModernBookingIdFloor(long v) { this.modernBookingIdFloor = v; }
    public Duration getResolveTimeout() { return resolveTimeout; }
    public void setResolveTimeout(Duration v) { this.resolveTimeout = v; }
    public Duration getResolveDefaultTtl() { return resolveDefaultTtl; }
    public void setResolveDefaultTtl(Duration v) { this.resolveDefaultTtl = v; }
    public String getConfigFile() { return configFile; }
    public void setConfigFile(String v) { this.configFile = v; }
    public Duration getWatchInterval() { return watchInterval; }
    public void setWatchInterval(Duration v) { this.watchInterval = v; }
}
