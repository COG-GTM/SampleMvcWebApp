package com.northwind.scheduling.characterization.legacy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Splits a sqlcmd script into batches on {@code GO} lines (what sqlcmd does before sending them). */
final class SqlScript {
    private SqlScript() {
    }

    static List<String> batches(Path file) {
        try {
            return batches(Files.readString(file));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    static List<String> batches(String script) {
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
