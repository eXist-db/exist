package org.exist.xquery.util;

/**
 * German language formatting of numbers and dates.
 *
 * @author Wolfgang
 */
public class NumberFormatter_de extends NumberFormatter {

    public final static String[] MONTHS = { "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli",
            "August", "September", "Oktober", "November", "Dezember" };
    public final static String[] DAYS = { "Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag",
            "Freitag", "Samstag" };

    @Override
    public String getMonth(int month) {
        return MONTHS[month - 1];
    }

    @Override
    public String getDay(int day) {
        return DAYS[day - 1];
    }

    @Override
    public String getAmPm(int hour) {
        return "";
    }

    @Override
    public String getOrdinalSuffix(long number) {
        return ".";
    }
}
