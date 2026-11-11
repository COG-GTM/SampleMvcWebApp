package com.northwind.ad.results.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class UploadReportTest {

    @Test
    public void countersKeepBucketsSeparate() {
        UploadReport r = new UploadReport();
        for (int i = 0; i < 5; i++) {
            r.incRowsRead();
        }
        r.incImported();
        r.incDuplicates();
        r.incDuplicates();
        r.addConflict(4, 70, 900, 612, 640);
        r.addError(5, "bad score 'x'");

        assertEquals(5, r.getRowsRead());
        assertEquals(1, r.getImported());
        assertEquals(2, r.getDuplicates());
        assertEquals(1, r.getConflicts());
        assertEquals(1, r.getSkipped());
        assertEquals(1, r.getErrors().size());
        assertEquals(r.getRowsRead(), r.getImported() + r.getDuplicates() + r.getConflicts() + r.getSkipped());
        assertEquals(4, r.getConflictRows().get(0).get("line"));
    }

    @Test
    public void previousUploadIdDrivesPreviouslyUploaded() {
        UploadReport r = new UploadReport();
        r.setPreviousUploadId(null);
        assertFalse(r.isPreviouslyUploaded());
        r.setPreviousUploadId(12);
        assertTrue(r.isPreviouslyUploaded());
        assertEquals(Integer.valueOf(12), r.getPreviousUploadId());
    }

    @Test
    public void serialisesAllFieldsInOrder() throws Exception {
        UploadReport r = new UploadReport();
        r.setFileName("NWLON01_20260910.csv");
        r.setSha256("ab");
        r.setUploadId(3);
        r.setPreviousUploadId(2);
        r.incRowsRead();
        r.incRowsRead();
        r.incDuplicates();
        r.addConflict(3, 70, 900, 612, 640);

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(r));
        List<String> names = new ArrayList<String>();
        for (Iterator<String> it = json.fieldNames(); it.hasNext();) {
            names.add(it.next());
        }
        assertEquals("[fileName, sha256, uploadId, previouslyUploaded, previousUploadId, rowsRead, imported, "
                + "duplicates, conflicts, skipped, errors, conflictRows]", names.toString());
        assertEquals(2, json.get("rowsRead").asInt());
        assertEquals(0, json.get("imported").asInt());
        assertEquals(1, json.get("duplicates").asInt());
        assertEquals(1, json.get("conflicts").asInt());
        assertEquals(0, json.get("skipped").asInt());
        assertTrue(json.get("previouslyUploaded").asBoolean());
        assertEquals(0, json.get("errors").size());
        JsonNode c = json.get("conflictRows").get(0);
        assertEquals(3, c.get("line").asInt());
        assertEquals(70, c.get("bookingId").asInt());
        assertEquals(612, c.get("storedScore").asInt());
        assertEquals(640, c.get("submittedScore").asInt());
    }
}
