package com.northwind.ad.results.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.northwind.ad.results.model.CsvRow;
import com.northwind.ad.results.model.UploadReport;
import com.northwind.ad.results.web.ApiException;

/**
 * Imports a partner score file row by row. A bad row is skipped and reported,
 * it does not stop the rest of the file (each row commits on its own, HB-19).
 * Rows whose result is already stored are counted as duplicates (same score) or
 * conflicts (different score) instead of errors. A file whose exact content
 * (SHA-256, dbo.ResultUpload) was already imported is only re-checked, never inserted.
 */
@Component
public class ScoreFileImporter {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreFileImporter.class);

    private final CsvRowParser parser = new CsvRowParser();

    @Autowired
    private ResultIngestService ingestService;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${results.audit-actor:results-service}")
    private String actor;

    public UploadReport importFile(String fileName, byte[] content) throws IOException {
        UploadReport report = new UploadReport();
        report.setFileName(fileName);
        String sha256 = sha256(content);
        report.setSha256(sha256);
        Integer previousUpload = findUpload(sha256);
        boolean replay = previousUpload != null;
        report.setAlreadyUploaded(replay);
        report.setUploadId(previousUpload);

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
                    IngestOutcome outcome = ingestService.ingest(bookingId.intValue(), row.score, row.scoredAt,
                            fileName, !replay);
                    switch (outcome.getStatus()) {
                        case CREATED:
                            report.incImported();
                            break;
                        case DUPLICATE:
                            report.addDuplicate();
                            break;
                        case CONFLICT:
                            report.addConflict(lineNo, bookingId.intValue(), outcome.getResultId(),
                                    outcome.getStoredScore(), row.score);
                            break;
                        default:
                            report.addError(lineNo, "not imported: file already uploaded as upload "
                                    + previousUpload + ", send new rows in a new file");
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
        if (!replay) {
            report.setUploadId(recordUpload(report));
        }
        LOG.info("Score file {} sha256={} replay={}: read={} imported={} duplicates={} conflicts={} errors={}",
                fileName, sha256, replay, report.getRowsRead(), report.getImported(), report.getDuplicates(),
                report.getConflicts(), report.getErrors().size());
        return report;
    }

    private Integer findUpload(String sha256) {
        List<Integer> ids = jdbc.queryForList("SELECT UploadId FROM dbo.ResultUpload WHERE Sha256 = ?",
                Integer.class, sha256);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Integer recordUpload(UploadReport r) {
        try {
            return jdbc.queryForObject(
                    "SET NOCOUNT ON; "
                  + "INSERT INTO dbo.ResultUpload (FileName, Sha256, RowsRead, Imported, Duplicates, Conflicts, Errors, Actor) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                    Integer.class, r.getFileName(), r.getSha256(), r.getRowsRead(), r.getImported(),
                    r.getDuplicates(), r.getConflicts(), r.getErrors().size(), actor);
        } catch (DuplicateKeyException e) {
            // the same file was imported concurrently and recorded first: rows this request inserted were
            // counted as duplicates there, so move them to Imported to keep the stored totals right
            if (r.getImported() > 0) {
                jdbc.update("UPDATE dbo.ResultUpload SET Imported = Imported + ?, Duplicates = Duplicates - ? "
                        + "WHERE Sha256 = ?", r.getImported(), r.getImported(), r.getSha256());
            }
            return findUpload(r.getSha256());
        }
    }

    static String sha256(byte[] content) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder(64);
            for (byte x : d) {
                sb.append(String.format("%02x", x & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
