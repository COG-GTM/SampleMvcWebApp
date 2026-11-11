package com.northwind.ad.results.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Score file upload outcome. Every data row lands in exactly one bucket:
 * imported, duplicates (identical result already stored), conflicts (a different
 * result is stored, see conflictDetails) or errors (bad or unmatched rows).
 * skipped = rowsRead - imported.
 */
public class UploadReport {

    private String fileName;
    private String sha256;
    private Integer uploadId;
    private boolean alreadyUploaded;
    private int rowsRead;
    private int imported;
    private int duplicates;
    private int conflicts;
    private int skipped;
    private List<Map<String, Object>> errors = new ArrayList<Map<String, Object>>();
    private List<Map<String, Object>> conflictDetails = new ArrayList<Map<String, Object>>();

    public void addError(int line, String message) {
        Map<String, Object> e = new LinkedHashMap<String, Object>();
        e.put("line", line);
        e.put("message", message);
        errors.add(e);
        skipped++;
    }

    public void addDuplicate() {
        duplicates++;
        skipped++;
    }

    public void addConflict(int line, int bookingId, Integer resultId, Integer storedScore, int submittedScore) {
        Map<String, Object> c = new LinkedHashMap<String, Object>();
        c.put("line", line);
        c.put("bookingId", bookingId);
        c.put("resultId", resultId);
        c.put("storedScore", storedScore);
        c.put("submittedScore", submittedScore);
        conflictDetails.add(c);
        conflicts++;
        skipped++;
    }

    public void incImported() {
        imported++;
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

    /** ResultUpload row of this upload, or of the earlier upload when alreadyUploaded */
    public Integer getUploadId() {
        return uploadId;
    }

    public void setUploadId(Integer uploadId) {
        this.uploadId = uploadId;
    }

    /** true when the same file content (SHA-256) was already imported; nothing is inserted then */
    public boolean isAlreadyUploaded() {
        return alreadyUploaded;
    }

    public void setAlreadyUploaded(boolean alreadyUploaded) {
        this.alreadyUploaded = alreadyUploaded;
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
        return conflicts;
    }

    public int getSkipped() {
        return skipped;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }

    public List<Map<String, Object>> getConflictDetails() {
        return conflictDetails;
    }
}
