package com.northwind.scheduling.core;

import java.util.HexFormat;

/** Session ETag = the row's ROWVERSION. */
public final class ETags {
    private ETags() {
    }

    public static String of(byte[] rowVersion) {
        return rowVersion == null ? null : "\"" + HexFormat.of().formatHex(rowVersion) + "\"";
    }

    public static boolean matches(String ifMatch, String etag) {
        if ("*".equals(ifMatch.trim())) {
            return true;
        }
        String v = ifMatch.trim();
        if (v.startsWith("W/")) {
            v = v.substring(2);
        }
        return v.equals(etag);
    }
}
