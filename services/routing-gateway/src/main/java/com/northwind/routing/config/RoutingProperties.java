package com.northwind.routing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Static gateway settings (upstreams, timings). The allow-list itself ({@code routing.modern-centers},
 * {@code routing.test-centers}, {@code routing.coexistence-mode}) is read live from the Environment by
 * {@link com.northwind.routing.policy.AllowList} so a refresh takes effect immediately.
 */
@ConfigurationProperties("routing")
public class RoutingProperties {

    private String portalUrl = "http://localhost:8080";
    private String bookingApiUrl = "http://localhost:8081";
    private String resultsServiceUrl = "http://localhost:8082";
    private String capacitySyncUrl = "http://localhost:8083";
    private String schedulingServiceUrl = "http://localhost:8090";
    private String configFile = "./config/routing.properties";
    private Duration configPollInterval = Duration.ofSeconds(1);
    private Duration resolveCacheTtl = Duration.ofSeconds(30);
    private Duration resolveTimeout = Duration.ofSeconds(3);
    private long modernBookingIdFloor = 50_000_000L;

    public String getPortalUrl() { return portalUrl; }
    public void setPortalUrl(String v) { portalUrl = v; }
    public String getBookingApiUrl() { return bookingApiUrl; }
    public void setBookingApiUrl(String v) { bookingApiUrl = v; }
    public String getResultsServiceUrl() { return resultsServiceUrl; }
    public void setResultsServiceUrl(String v) { resultsServiceUrl = v; }
    public String getCapacitySyncUrl() { return capacitySyncUrl; }
    public void setCapacitySyncUrl(String v) { capacitySyncUrl = v; }
    public String getSchedulingServiceUrl() { return schedulingServiceUrl; }
    public void setSchedulingServiceUrl(String v) { schedulingServiceUrl = v; }
    public String getConfigFile() { return configFile; }
    public void setConfigFile(String v) { configFile = v; }
    public Duration getConfigPollInterval() { return configPollInterval; }
    public void setConfigPollInterval(Duration v) { configPollInterval = v; }
    public Duration getResolveCacheTtl() { return resolveCacheTtl; }
    public void setResolveCacheTtl(Duration v) { resolveCacheTtl = v; }
    public Duration getResolveTimeout() { return resolveTimeout; }
    public void setResolveTimeout(Duration v) { resolveTimeout = v; }
    public long getModernBookingIdFloor() { return modernBookingIdFloor; }
    public void setModernBookingIdFloor(long v) { modernBookingIdFloor = v; }
}
