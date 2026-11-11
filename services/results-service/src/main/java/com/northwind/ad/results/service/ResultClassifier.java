package com.northwind.ad.results.service;

import java.util.Date;

import com.northwind.ad.results.model.StoredResult;

/**
 * Decides whether a submitted result is a replay of the stored one (DUPLICATE) or a different
 * result for the same booking (CONFLICT).
 *
 * <p>Same score and same scoredAt is a duplicate. scoredAt is compared at second precision after
 * applying SQL Server DATETIME rounding (1/300 s ticks, so .999 becomes the next second) to both
 * sides, because the stored value has already been rounded by the column type.
 *
 * <p>When the caller sent no scoredAt (REST posts default it to "now"), only the score is
 * compared: a retried post without a timestamp can never carry the original "now".
 */
public final class ResultClassifier {

    private ResultClassifier() {
    }

    /** @param submittedScoredAt null when the caller did not send one */
    public static boolean isDuplicate(StoredResult stored, int submittedScore, Date submittedScoredAt) {
        if (stored.getScore() != submittedScore) {
            return false;
        }
        if (submittedScoredAt == null) {
            return true;
        }
        return stored.getScoredAt() != null
                && datetimeSecond(stored.getScoredAt()) == datetimeSecond(submittedScoredAt);
    }

    /** Whole second a timestamp falls into once stored in a SQL Server DATETIME column. */
    static long datetimeSecond(Date d) {
        long ticks = Math.floorDiv(d.getTime() * 3L + 5L, 10L); // 300 ticks per second, half up
        return Math.floorDiv(ticks, 300L);
    }
}
