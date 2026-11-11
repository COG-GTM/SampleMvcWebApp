package com.northwind.ad.results.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.northwind.ad.results.model.CsvRow;
import com.northwind.ad.results.model.UploadReport;
import com.northwind.ad.results.web.ApiException;

/**
 * Imports a partner score file row by row. A bad row is skipped and reported,
 * it does not stop the rest of the file (each row commits on its own).
 */
@Component
public class ScoreFileImporter {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreFileImporter.class);

    private final CsvRowParser parser = new CsvRowParser();

    @Autowired
    private ResultIngestService ingestService;

    public UploadReport importFile(String fileName, InputStream in) throws IOException {
        UploadReport report = new UploadReport();
        report.setFileName(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        try {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1); // Excel BOM
                }
                if (lineNo == 1 && parser.isHeader(line)) {
                    continue;
                }
                if (line.trim().length() == 0) {
                    continue;
                }
                report.incRowsRead();
                try {
                    CsvRow row = parser.parse(line, lineNo);
                    Integer bookingId = ingestService.findBookingId(row.candidateNumber, row.sessionId);
                    if (bookingId == null) {
                        report.addError(lineNo, "no active booking for " + row.candidateNumber + " on session " + row.sessionId);
                        continue;
                    }
                    ingestService.ingest(bookingId.intValue(), row.score, row.scoredAt, fileName);
                    report.incImported();
                } catch (IllegalArgumentException e) {
                    report.addError(lineNo, e.getMessage());
                } catch (ApiException e) {
                    report.addError(lineNo, e.getMessage());
                }
            }
        } finally {
            reader.close();
        }
        LOG.info("Score file {}: read={} imported={} skipped={}", fileName, report.getRowsRead(),
                report.getImported(), report.getSkipped());
        return report;
    }
}
