package com.northwind.ad.results.service;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.northwind.ad.results.model.CsvRow;

/**
 * Parses score file lines. Format agreed with the delivery partners in 2015:
 * header row, then candidateNumber,sessionId,score,scoredAt (no quoting).
 */
public class CsvRowParser {

    public static final String EXPECTED_HEADER = "candidateNumber,sessionId,score,scoredAt";

    // partners send any of these; the first one is the documented format
    private static final String[] DATE_FORMATS = {"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};

    public boolean isHeader(String line) {
        return line != null && line.trim().toLowerCase().startsWith("candidatenumber");
    }

    public CsvRow parse(String line, int lineNumber) {
        if (line == null || line.trim().length() == 0) {
            throw new IllegalArgumentException("empty line");
        }
        String[] parts = line.split(",", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("expected 4 fields but found " + parts.length);
        }
        CsvRow row = new CsvRow();
        row.lineNumber = lineNumber;
        row.candidateNumber = parts[0].trim();
        if (!row.candidateNumber.matches("NW\\d{8}")) {
            throw new IllegalArgumentException("bad candidateNumber '" + row.candidateNumber + "'");
        }
        try {
            row.sessionId = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad sessionId '" + parts[1].trim() + "'");
        }
        try {
            row.score = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad score '" + parts[2].trim() + "'");
        }
        if (row.score < 0 || row.score > 1000) {
            throw new IllegalArgumentException("score out of range: " + row.score);
        }
        row.scoredAt = parseDate(parts[3].trim());
        return row;
    }

    public static Date parseDate(String s) {
        for (String fmt : DATE_FORMATS) {
            SimpleDateFormat f = new SimpleDateFormat(fmt);
            f.setLenient(false);
            ParsePosition pos = new ParsePosition(0);
            Date d = f.parse(s, pos);
            if (d != null && pos.getIndex() == s.length()) {
                return d;
            }
        }
        throw new IllegalArgumentException("bad scoredAt '" + s + "'");
    }
}
