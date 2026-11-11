package com.northwind.ad.results.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UploadReport {

    private String fileName;
    private int rowsRead;
    private int imported;
    private int skipped;
    private List<Map<String, Object>> errors = new ArrayList<Map<String, Object>>();

    public void addError(int line, String message) {
        Map<String, Object> e = new LinkedHashMap<String, Object>();
        e.put("line", line);
        e.put("message", message);
        errors.add(e);
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

    public int getRowsRead() {
        return rowsRead;
    }

    public int getImported() {
        return imported;
    }

    public int getSkipped() {
        return skipped;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }
}
