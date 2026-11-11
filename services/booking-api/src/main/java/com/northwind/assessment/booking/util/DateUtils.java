package com.northwind.assessment.booking.util;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Date helpers. SimpleDateFormat is not thread safe so a new one is created per call.
 */
public final class DateUtils {

    public static final String ISO_DATE = "yyyy-MM-dd";
    public static final String ISO_DATE_TIME = "yyyy-MM-dd'T'HH:mm:ss";

    private DateUtils() {
    }

    public static Date parseIsoDate(String s) {
        if (s == null || s.trim().length() == 0) {
            return null;
        }
        SimpleDateFormat f = new SimpleDateFormat(ISO_DATE);
        f.setLenient(false);
        String t = s.trim();
        ParsePosition pos = new ParsePosition(0);
        Date d = f.parse(t, pos);
        if (d == null || pos.getIndex() != t.length()) {
            throw new IllegalArgumentException("Invalid date '" + s + "', expected " + ISO_DATE);
        }
        return d;
    }

    public static String formatIsoDate(Date d) {
        return d == null ? null : new SimpleDateFormat(ISO_DATE).format(d);
    }

    public static String formatIsoDateTime(Date d) {
        return d == null ? null : new SimpleDateFormat(ISO_DATE_TIME).format(d);
    }

    /** Strips the time portion (midnight, JVM default time zone). */
    public static Date truncate(Date d) {
        if (d == null) {
            return null;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    public static Date today() {
        return truncate(new Date());
    }

    public static Date addDays(Date d, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(truncate(d));
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTime();
    }

    public static Date addWorkingDays(Date d, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(truncate(d));
        int added = 0;
        while (added < days) {
            c.add(Calendar.DAY_OF_MONTH, 1);
            int dow = c.get(Calendar.DAY_OF_WEEK);
            if (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY) {
                added++;
            }
        }
        return c.getTime();
    }

    public static boolean isBefore(Date a, Date b) {
        return truncate(a).before(truncate(b));
    }
}
