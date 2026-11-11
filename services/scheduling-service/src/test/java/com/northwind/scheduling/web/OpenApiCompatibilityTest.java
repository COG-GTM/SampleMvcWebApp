package com.northwind.scheduling.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.openapitools.openapidiff.core.model.Endpoint;
import org.openapitools.openapidiff.core.output.MarkdownRender;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

/**
 * MBA-3833 breaking-change gate: api/openapi.yaml (kept equal to the running service by OpenApiSpecIT) must stay
 * backward compatible (openapi-diff) with every published baseline in api/baseline/. Removing an endpoint, removing a
 * response field, or adding a required request field fails the build. A deliberate break needs a new major version:
 * a new baseline file plus partner sign-off, never an edit of an existing baseline.
 */
class OpenApiCompatibilityTest {

    static final Path CURRENT = Path.of("api/openapi.yaml");
    static final Path BASELINES = Path.of("api/baseline");

    /**
     * SHA-256 (LF line endings) of every published baseline. Editing a baseline together with api/openapi.yaml would
     * hide a break from the diff, so a baseline is immutable once listed here; add a new file for a new major version.
     */
    static final Map<String, String> PUBLISHED = Map.of(
            "openapi-v2.0.0.yaml", "3a2d2cda65301784e5d15f170459221dadfbe3e7253330d3e5e38072964c59c8");

    static OpenAPI parse(Path spec) throws Exception {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readContents(Files.readString(spec), null, options);
        assertThat(result.getMessages()).as("parse messages of " + spec).isEmpty();
        return result.getOpenAPI();
    }

    static String report(ChangedOpenApi diff) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStreamWriter out = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
            new MarkdownRender().render(diff, out);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    static List<Path> baselines() throws Exception {
        try (Stream<Path> files = Files.list(BASELINES)) {
            return files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
        }
    }

    static ChangedOpenApi againstCurrent(Consumer<OpenAPI> change) throws Exception {
        OpenAPI changed = parse(CURRENT);
        change.accept(changed);
        return OpenApiCompare.fromSpecifications(parse(CURRENT), changed);
    }

    @Test
    void publishedBaselinesAreUnchanged() throws Exception {
        List<String> files = baselines().stream().map(p -> p.getFileName().toString()).toList();
        assertThat(files).as("every baseline file is pinned in PUBLISHED").containsExactlyInAnyOrderElementsOf(PUBLISHED.keySet());
        for (String file : files) {
            byte[] lf = Files.readString(BASELINES.resolve(file)).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(lf));
            assertThat(sha).as("published baseline " + file + " was edited; add a new baseline instead").isEqualTo(PUBLISHED.get(file));
        }
    }

    @Test
    void committedSpecIsBackwardCompatibleWithEveryPublishedBaseline() throws Exception {
        List<Path> baselines = baselines();
        assertThat(baselines).as("published baselines in api/baseline").isNotEmpty();
        for (Path baseline : baselines) {
            ChangedOpenApi diff = OpenApiCompare.fromSpecifications(parse(baseline), parse(CURRENT));
            assertThat(diff.isChanged().isIncompatible())
                    .as("api/openapi.yaml breaks " + baseline + ":\n" + report(diff))
                    .isFalse();
        }
    }

    @Test
    void unchangedAndAdditiveChangesAreAccepted() throws Exception {
        assertThat(againstCurrent(api -> { }).isChanged().isIncompatible()).isFalse();
        ChangedOpenApi additive = againstCurrent(api -> api.getComponents().getSchemas().get("CapacityResultDto")
                .addProperty("note", new StringSchema()));
        assertThat(additive.isChanged().isIncompatible()).isFalse();
    }

    @Test
    void removingAnEndpointIsDetectedAsBreaking() throws Exception {
        ChangedOpenApi diff = againstCurrent(api -> api.getPaths().remove("/api/v2/candidates/{candidateNumber}/eligibility"));
        assertThat(diff.isChanged().isIncompatible()).isTrue();
        assertThat(diff.getMissingEndpoints()).extracting(Endpoint::getPathUrl)
                .containsExactly("/api/v2/candidates/{candidateNumber}/eligibility");
    }

    @Test
    void removingAnOperationIsDetectedAsBreaking() throws Exception {
        ChangedOpenApi diff = againstCurrent(api -> api.getPaths().get("/api/v2/bookings/{bookingId}").setDelete(null));
        assertThat(diff.isChanged().isIncompatible()).isTrue();
    }

    @Test
    void removingARequiredResponseFieldIsDetectedAsBreaking() throws Exception {
        ChangedOpenApi diff = againstCurrent(api -> {
            var eligibility = api.getComponents().getSchemas().get("EligibilityDto");
            eligibility.getProperties().remove("earliestRetakeDate");
            eligibility.setRequired(new ArrayList<>(eligibility.getRequired()));
            eligibility.getRequired().remove("earliestRetakeDate");
        });
        assertThat(diff.isChanged().isIncompatible()).as(diff.isChanged() + report(diff)).isTrue();
    }

    @Test
    void newRequiredRequestFieldIsDetectedAsBreaking() throws Exception {
        ChangedOpenApi diff = againstCurrent(api -> {
            var book = api.getComponents().getSchemas().get("BookRequest");
            book.setRequired(new ArrayList<>(book.getRequired()));
            book.getRequired().add("externalRef");
        });
        assertThat(diff.isChanged().isIncompatible()).as(diff.isChanged() + report(diff)).isTrue();
    }
}
