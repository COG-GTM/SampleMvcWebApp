package com.northwind.scheduling.characterization;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.northwind.scheduling.characterization.support.Scenario;

/** Keeps README.md's scenario catalogue rows (id, branch/outcome, refs, method) identical to the @Scenario annotations. */
class CatalogueConsistencyTest {

    private static final Pattern ROW = Pattern.compile("^\\|\\s*([A-Z0-9]+-\\d{2})\\s*\\|", Pattern.MULTILINE);

    @Test
    void readmeCataloguesEveryScenario() throws Exception {
        Set<String> code = new TreeSet<>();
        List<String> dupes = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        for (Class<?> c : scenarioClasses()) {
            for (Method m : c.getDeclaredMethods()) {
                Scenario s = m.getAnnotation(Scenario.class);
                if (s != null) {
                    if (!code.add(s.id())) {
                        dupes.add(s.id());
                    }
                    rows.add("| " + s.id() + " | " + s.covers().replace("|", "\\|") + " | " + s.refs() + " | `"
                            + c.getSimpleName() + "#" + m.getName() + "` |");
                }
            }
        }
        String readme = Files.readString(Path.of("README.md"));
        Set<String> readmeLines = Set.copyOf(readme.lines().toList());
        List<String> missingRows = rows.stream().filter(r -> !readmeLines.contains(r)).sorted().toList();
        Set<String> documented = new TreeSet<>();
        Matcher m = ROW.matcher(readme);
        while (m.find()) {
            documented.add(m.group(1));
        }
        assertThat(dupes).as("duplicate scenario ids").isEmpty();
        assertThat(code).as("scenario ids").hasSizeGreaterThan(80);
        assertThat(documented).as("README catalogue vs @Scenario ids").isEqualTo(code);
        assertThat(missingRows).as("README rows that differ from the @Scenario annotation (covers / refs / method)").isEmpty();
    }

    private static List<Class<?>> scenarioClasses() throws IOException, URISyntaxException, ClassNotFoundException {
        Path root = Path.of(CatalogueConsistencyTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path dir = root.resolve("com/northwind/scheduling/characterization/scenarios");
        List<Class<?>> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith("Test.class")).toList()) {
                String name = "com.northwind.scheduling.characterization.scenarios." + p.getFileName().toString().replace(".class", "");
                out.add(Class.forName(name));
            }
        }
        return out;
    }
}
