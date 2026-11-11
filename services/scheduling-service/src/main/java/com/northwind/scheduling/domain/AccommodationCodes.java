package com.northwind.scheduling.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Accommodation codes, ported from AssessmentDelivery.Business/Rules/AccommodationCodes.cs and
 * booking-api BookingService.ACCOMMODATION_CODES / EXTRA_TIME_CODES.
 * ET25/ET50 (extra time) consume one of the session's AccommodationCapacity seats; SR/RB do not
 * (docs/SYSTEM_OVERVIEW.md, HB-07).
 */
public final class AccommodationCodes {
    public static final String EXTRA_TIME_25 = "ET25";
    public static final String EXTRA_TIME_50 = "ET50";
    public static final String SEPARATE_ROOM = "SR";
    public static final String REST_BREAKS = "RB";

    public static final Set<String> ALL = Set.of(EXTRA_TIME_25, EXTRA_TIME_50, SEPARATE_ROOM, REST_BREAKS);
    public static final Set<String> EXTRA_TIME = Set.of(EXTRA_TIME_25, EXTRA_TIME_50);

    private AccommodationCodes() {
    }

    /**
     * Blank means "no accommodation" (booking-api StringUtils.trimToNull). The portal instead falls back
     * to the candidate's profile default - ADR 0002 ODD-07 keeps the explicit API meaning and moves the
     * default into the UI, which pre-selects it.
     *
     * @throws SchedulingException VALIDATION for an unknown code
     */
    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String c = code.trim().toUpperCase(Locale.ROOT);
        if (!ALL.contains(c)) {
            throw new SchedulingException(ErrorCode.VALIDATION, "Unknown accommodationCode " + c);
        }
        return c;
    }

    public static boolean isExtraTime(String code) {
        return code != null && EXTRA_TIME.contains(code);
    }
}
