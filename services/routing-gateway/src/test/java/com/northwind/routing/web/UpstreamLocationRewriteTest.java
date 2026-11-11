package com.northwind.routing.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UpstreamLocationRewriteTest {

    @Test
    void absoluteUpstreamLocationsBecomeOriginRelative() {
        assertThat(UpstreamLocationRewrite.relative("http://localhost:8090/app/")).isEqualTo("/app/");
        assertThat(UpstreamLocationRewrite.relative("http://127.0.0.1:8090/app/?center=BOS01&exam=AZ-104#top"))
                .isEqualTo("/app/?center=BOS01&exam=AZ-104#top");
        assertThat(UpstreamLocationRewrite.relative("https://scheduling.internal")).isEqualTo("/");
    }

    @Test
    void relativeAndNonHttpLocationsAreKept() {
        assertThat(UpstreamLocationRewrite.relative("/app/my-bookings")).isEqualTo("/app/my-bookings");
        assertThat(UpstreamLocationRewrite.relative("app/")).isEqualTo("app/");
        assertThat(UpstreamLocationRewrite.relative("mailto:ops@northwind.test")).isEqualTo("mailto:ops@northwind.test");
        assertThat(UpstreamLocationRewrite.relative("http://bad host/x")).isEqualTo("http://bad host/x");
    }
}
