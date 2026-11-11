package com.northwind.ad.results.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.northwind.ad.results.model.CsvRow;
import com.northwind.ad.results.model.IngestOutcome;
import com.northwind.ad.results.model.UploadReport;
import com.northwind.ad.results.web.ApiException;

/**
 * Imports a partner score file row by row, through the same idempotent path as REST posts.
 *
 * <p>Row-level commits, not all-or-nothing: each row is its own transaction. New rows are
 * inserted, rows identical to a stored result are counted as {@code duplicates} and skipped,
 * rows that differ from a stored result are reported in {@code conflictRows} by line number (and
 * audited as DUPLICATE_REJECTED), and bad rows go to {@code errors}. None of these roll back the
 * rows that were imported.
 *
 * <p>Every upload is recorded in dbo.ResultUpload with the SHA-256 of the file bytes; a file
 * whose content was uploaded before is flagged {@code previouslyUploaded} and, being all
 * duplicates, imports nothing.
 */
@Component
public class ScoreFileImporter {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreFileImporter.class);

    /** Result.SourceFile and ResultUpload.FileName are NVARCHAR(200). */
    static final int MAX_FILE_NAME = 200;

    private final CsvRowParser parser = new CsvRowParser();

    @Autowired
    private ResultIngestService ingestService;

    @Autowired
    private ResultStore store;

    public UploadReport importFile(String fileName, InputStream in) throws IOException {
        try {
            return importFile(fileName, StreamUtils.copyToByteArray(in));
        } finally {
            in.close();
        }
    }

    public UploadReport importFile(String fileName, byte[] content) throws IOException {
        if (fileName != null && fileName.length() > MAX_FILE_NAME) {
            fileName = fileName.substring(0, MAX_FILE_NAME);
        }
        UploadReport report = new UploadReport();
        report.setFileName(fileName);
        report.setSha256(FileHash.sha256Hex(content));
        report.setPreviousUploadId(store.findLatestUploadId(report.getSha256()));
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content), "UTF-8"));
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
                    IngestOutcome outcome = ingestService.ingest(bookingId.intValue(), row.score, row.scoredAt, fileName, lineNo);
                    switch (outcome.getStatus()) {
                        case CREATED:
                            report.incImported();
                            break;
                        case DUPLICATE:
                            report.incDuplicates();
                            break;
                        default:
                            report.addConflict(lineNo, outcome.getBookingId(), outcome.getResultId(), outcome.getScore(),
                                    outcome.getSubmittedScore());
                    }
                } catch (IllegalArgumentException e) {
                    report.addError(lineNo, e.getMessage());
                } catch (ApiException e) {
                    report.addError(lineNo, e.getMessage());
                }
            }
        } finally {
            reader.close();
        }
        report.setUploadId(store.recordUpload(report));
        LOG.info("Score file {} (upload {}, sha256 {}, previous upload {}): read={} imported={} duplicates={} conflicts={} skipped={}",
                fileName, report.getUploadId(), report.getSha256(), report.getPreviousUploadId(), report.getRowsRead(),
                report.getImported(), report.getDuplicates(), report.getConflicts(), report.getSkipped());
        return report;
    }
}
