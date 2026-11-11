package com.northwind.ad.results.model;

import java.util.Date;

/** One data line of a score file: candidateNumber,sessionId,score,scoredAt */
public class CsvRow {

    public int lineNumber;
    public String candidateNumber;
    public int sessionId;
    public int score;
    public Date scoredAt;
}
