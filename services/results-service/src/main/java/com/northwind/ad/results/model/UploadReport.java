package com.northwind.ad.results.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Result of one score file upload. Every data row lands in exactly one bucket:
 * rowsRead = imported + duplicates + conflicts + skipped. {@code skipped} keeps its original
 * meaning (rows rejected as bad, one entry each in {@code errors}); duplicates and conflicts are
 * counted separately and never appear in {@code errors}.
 */
@JsonPropertyOrder({"fileName", "sha256", "uploadId", "previouslyUploaded", "previousUploadId", "rowsRead",
        "imported", "duplicates", "conflicts", "skipped", "errors", "conflictRows"})
public class UploadReport {

    private String fileName;
    private String sha256;
    private Integer uploadId;
    private boolean previouslyUploaded;
    private Integer previousUploadId;
    private int rowsRead;
    private int imported;
    private int duplicates;
    private int skipped;
    private List<Map<String, Object>> errors = new ArrayList<Map<String, Object>>();
    private List<Map<String, Object>> conflictRows = new ArrayList<Map<String, Object>>();

    public void addError(int line, String message) {
        Map<String, Object> e = new LinkedHashMap<String, Object>();
        e.put("line", line);
        e.put("message", message);
        errors.add(e);
        skipped++;
    }

    public void addConflict(int line, int bookingId, int resultId, int storedScore, int submittedScore) {
        Map<String, Object> c = new LinkedHashMap<String, Object>();
        c.put("line", line);
        c.put("bookingId", bookingId);
        c.put("resultId", resultId);
        c.put("storedScore", storedScore);
        c.put("submittedScore", submittedScore);
        conflictRows.add(c);
    }

    public void incImported() {
        imported++;
    }

    public void incDuplicates() {
        duplicates++;
    }

    public void incRowsRead() {
        rowsRead++;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Integer getUploadId() {
        return uploadId;
    }

    public void setUploadId(Integer uploadId) {
        this.uploadId = uploadId;
    }

    public boolean isPreviouslyUploaded() {
        return previouslyUploaded;
    }

    public Integer getPreviousUploadId() {
        return previousUploadId;
    }

    /** Marks this file content as seen before (null = first time). */
    public void setPreviousUploadId(Integer previousUploadId) {
        this.previousUploadId = previousUploadId;
        this.previouslyUploaded = previousUploadId != null;
    }

    public int getRowsRead() {
        return rowsRead;
    }

    public int getImported() {
        return imported;
    }

    public int getDuplicates() {
        return duplicates;
    }

    public int getConflicts() {
        return conflictRows.size();
    }

    public int getSkipped() {
        return skipped;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }

    public List<Map<String, Object>> getConflictRows() {
        return conflictRows;
    }
}
