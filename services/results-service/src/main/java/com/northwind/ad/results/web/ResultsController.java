package com.northwind.ad.results.web;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.northwind.ad.results.model.IngestOutcome;
import com.northwind.ad.results.model.ResultRequest;
import com.northwind.ad.results.model.UploadReport;
import com.northwind.ad.results.service.CsvRowParser;
import com.northwind.ad.results.service.ResultIngestService;
import com.northwind.ad.results.service.ScoreFileImporter;

@RestController
@RequestMapping("/api/v1/results")
public class ResultsController {

    @Autowired
    private ResultIngestService ingestService;

    @Autowired
    private ScoreFileImporter importer;

    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<Map<String, Object>> post(@RequestBody ResultRequest req) {
        if (req == null || req.getBookingId() == null || req.getScore() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "bookingId and score are required");
        }
        Date scoredAt = null; // not sent: stored as now, replays matched on score only
        if (req.getScoredAt() != null && req.getScoredAt().trim().length() > 0) {
            try {
                scoredAt = CsvRowParser.parseDate(req.getScoredAt().trim());
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
            }
        }
        IngestOutcome outcome = ingestService.ingest(req.getBookingId(), req.getScore(), scoredAt, null);
        switch (outcome.getStatus()) {
            case CREATED:
                return new ResponseEntity<Map<String, Object>>(outcome.toBody(), HttpStatus.CREATED);
            case DUPLICATE:
                return new ResponseEntity<Map<String, Object>>(outcome.toBody(), HttpStatus.OK);
            default:
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESULT", outcome.conflictMessage(),
                        outcome.conflictDetails());
        }
    }

    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public UploadReport upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "file is empty");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.length() == 0) {
            name = "upload.csv";
        }
        return importer.importFile(name, file.getBytes());
    }

    @RequestMapping(value = "/candidate/{candidateNumber}", method = RequestMethod.GET)
    public List<Map<String, Object>> byCandidate(@PathVariable("candidateNumber") String candidateNumber) {
        Integer candidateId = ingestService.findCandidateId(candidateNumber);
        if (candidateId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CANDIDATE_NOT_FOUND",
                    "Candidate " + candidateNumber + " not found");
        }
        return ingestService.findByCandidate(candidateId);
    }
}
