package com.northwind.ad.results.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;

import com.northwind.ad.results.model.IngestOutcome;
import com.northwind.ad.results.model.StoredResult;
import com.northwind.ad.results.model.UploadReport;
import com.northwind.ad.results.web.ApiException;

@RunWith(MockitoJUnitRunner.class)
public class ScoreFileImporterTest {

    private static final String CSV = "candidateNumber,sessionId,score,scoredAt\n"
            + "NW00000001,1,612,2026-09-10T12:30:00\n"   // line 2 new
            + "NW00000002,1,500,2026-09-10T12:30:00\n"   // line 3 duplicate
            + "NW00000003,1,640,2026-09-10T12:30:00\n"   // line 4 conflict
            + "NW123,x\n"                                // line 5 malformed
            + "NW00000004,1,700,2026-09-10T12:30:00\n"   // line 6 booking not active
            + "NW00000999,1,700,2026-09-10T12:30:00\n";  // line 7 no booking

    @Mock
    private ResultIngestService ingestService;

    @Mock
    private ResultStore store;

    @InjectMocks
    private ScoreFileImporter importer;

    @Test
    public void classifiesEveryRowIntoOneBucket() throws Exception {
        byte[] bytes = CSV.getBytes(StandardCharsets.UTF_8);
        String sha = FileHash.sha256Hex(bytes);
        when(store.findLatestUploadId(sha)).thenReturn(null);
        when(store.recordUpload(any(UploadReport.class))).thenReturn(5);
        when(ingestService.findBookingId("NW00000001", 1)).thenReturn(11);
        when(ingestService.findBookingId("NW00000002", 1)).thenReturn(12);
        when(ingestService.findBookingId("NW00000003", 1)).thenReturn(13);
        when(ingestService.findBookingId("NW00000004", 1)).thenReturn(14);
        when(ingestService.findBookingId("NW00000999", 1)).thenReturn(null);
        Date at = CsvRowParser.parseDate("2026-09-10T12:30:00");
        when(ingestService.ingest(11, 612, at, "scores.csv", 2))
                .thenReturn(IngestOutcome.created(101, 11, 612, true));
        when(ingestService.ingest(12, 500, at, "scores.csv", 3))
                .thenReturn(IngestOutcome.duplicate(new StoredResult(102, 12, 500, false, at)));
        when(ingestService.ingest(13, 640, at, "scores.csv", 4))
                .thenReturn(IngestOutcome.conflict(new StoredResult(103, 13, 610, true, at), 640, at));
        when(ingestService.ingest(14, 700, at, "scores.csv", 6))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_ACTIVE", "Booking 14 is CANCELLED"));

        UploadReport r = importer.importFile("scores.csv", bytes);

        assertEquals("scores.csv", r.getFileName());
        assertEquals(sha, r.getSha256());
        assertEquals(Integer.valueOf(5), r.getUploadId());
        assertFalse(r.isPreviouslyUploaded());
        assertEquals(6, r.getRowsRead());
        assertEquals(1, r.getImported());
        assertEquals(1, r.getDuplicates());
        assertEquals(1, r.getConflicts());
        assertEquals(3, r.getSkipped());
        assertEquals(4, r.getConflictRows().get(0).get("line"));
        assertEquals(13, r.getConflictRows().get(0).get("bookingId"));
        assertEquals(610, r.getConflictRows().get(0).get("storedScore"));
        assertEquals(640, r.getConflictRows().get(0).get("submittedScore"));
        assertEquals(5, r.getErrors().get(0).get("line"));
        assertEquals(6, r.getErrors().get(1).get("line"));
        assertEquals(7, r.getErrors().get(2).get("line"));
        verify(ingestService).ingest(11, 612, at, "scores.csv", 2);
        verify(ingestService).ingest(13, 640, at, "scores.csv", 4);
        verify(store).recordUpload(r);
    }

    @Test
    public void flagsPreviouslyUploadedContent() throws Exception {
        byte[] bytes = "candidateNumber,sessionId,score,scoredAt\n".getBytes(StandardCharsets.UTF_8);
        when(store.findLatestUploadId(FileHash.sha256Hex(bytes))).thenReturn(4);
        when(store.recordUpload(any(UploadReport.class))).thenReturn(5);
        UploadReport r = importer.importFile("again.csv", bytes);
        assertTrue(r.isPreviouslyUploaded());
        assertEquals(Integer.valueOf(4), r.getPreviousUploadId());
        assertEquals(0, r.getRowsRead());
        verify(ingestService, never()).ingest(anyInt(), anyInt(), any(Date.class), anyString(), any(Integer.class));
    }

    @Test
    public void longFileNameIsCutToTheColumnWidth() throws Exception {
        byte[] bytes = "candidateNumber,sessionId,score,scoredAt\n".getBytes(StandardCharsets.UTF_8);
        StringBuilder name = new StringBuilder();
        while (name.length() < 250) {
            name.append("NW_SCORES_");
        }
        UploadReport r = importer.importFile(name.append(".csv").toString(), bytes);
        assertEquals(ScoreFileImporter.MAX_FILE_NAME, r.getFileName().length());
        assertTrue(name.toString().startsWith(r.getFileName()));
    }
}
