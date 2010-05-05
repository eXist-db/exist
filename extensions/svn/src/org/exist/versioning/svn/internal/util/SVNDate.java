/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.internal.util;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.FieldPosition;

import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class SVNDate extends Date {

    private static final long serialVersionUID = 4845L;

    public static final SVNDate NULL = new SVNDate(0, 0);

    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"), new Locale("en", "US"));

    static final DateFormat SVN_FORMAT = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS");

    private static final DateFormat ISO8601_FORMAT = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'000Z'");

    private static final DateFormat RFC1123_FORMAT = new SimpleDateFormat(
            "EEE, d MMM yyyy HH:mm:ss z", Locale.US);

    private static final DateFormat CUSTOM_FORMAT = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss Z (EE, d MMM yyyy)", Locale.getDefault());

    private static final DateFormat HUMAN_FORMAT = new SimpleDateFormat(
            "yyyy-MM-dd' 'HH:mm:ss' 'ZZZZ' ('E', 'dd' 'MMM' 'yyyy')'");

    private static final DateFormat SHORT_FORMAT = new SimpleDateFormat(
            "yyyy-MM-dd' 'HH:mm:ss'Z'");

    private static final DateFormat CONSOLE_DIFF_DATE_FORMAT = new SimpleDateFormat(
            "EEE' 'MMM' 'dd' 'HH:mm:ss' 'yyyy");

    private static final DateFormat CONSOLE_LONG_DATE_FORMAT = new SimpleDateFormat(
            "MM' 'dd'  'yyyy");

    private static final DateFormat CONSOLE_SHORT_DATE_FORMAT = new SimpleDateFormat(
            "MM' 'dd'  'HH:mm");

    public static final char[] DATE_SEPARATORS = {'-', '-', 'T', ':', ':', '.', 'Z'};

    static {
        SVN_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        ISO8601_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        RFC1123_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        HUMAN_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        SHORT_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        CUSTOM_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private int myMicroSeconds;

    private SVNDate(long time, int micro) {
        super((1000 * time + micro) / 1000);
        myMicroSeconds = micro >= 0 ? micro % 1000 : 1000 + (micro % 1000);
    }

    public String format() {
        StringBuffer formatted = new StringBuffer();
        synchronized (SVN_FORMAT) {
            SVN_FORMAT.format(this, formatted, new FieldPosition(0));
        }
        int m1 = myMicroSeconds % 10;
        int m2 = (myMicroSeconds / 10) % 10;
        int m3 = (myMicroSeconds) / 100;
        formatted.append(m3);
        formatted.append(m2);
        formatted.append(m1);
        formatted.append('Z');
        return formatted.toString();
    }

    public static String formatDate(Date date) {
        return formatDate(date, false);
    }

    public static String formatDate(Date date, boolean formatZeroDate) {
        if (date == null) {
            return null;
        } else if (!formatZeroDate && date.getTime() == 0) {
            return null;
        }
        if (date instanceof SVNDate) {
            SVNDate extendedDate = (SVNDate) date;
            return extendedDate.format();
        }
        synchronized (ISO8601_FORMAT) {
            return ISO8601_FORMAT.format(date);
        }
    }

    public static String formatRFC1123Date(Date date) {
        if (date == null) {
            return null;
        }
        synchronized (RFC1123_FORMAT) {
            return RFC1123_FORMAT.format(date);
        }
    }

    public static String formatHumanDate(Date date, ISVNOptions options) {
        DateFormat df = HUMAN_FORMAT;
        if (options != null && options.getKeywordDateFormat() != null) {
            df = options.getKeywordDateFormat();
        }
        synchronized (df) {
            return df.format(date != null ? date : NULL);
        }
    }

    public static String formatShortDate(Date date) {
        synchronized (SHORT_FORMAT) {
            return SHORT_FORMAT.format(date != null ? date : NULL);
        }
    }

    public static String formatCustomDate(Date date) {
        if (date == null) {
            return null;
        }
        synchronized (CUSTOM_FORMAT) {
            return CUSTOM_FORMAT.format(date);
        }
    }

    public static String formatConsoleDiffDate(Date date){
        if (date == null) {
            return null;
        }
        synchronized (CONSOLE_DIFF_DATE_FORMAT) {
            return CONSOLE_DIFF_DATE_FORMAT.format(date);
        }
    }

    public static String formatConsoleLongDate(Date date){
        if (date == null) {
            return null;
        }
        synchronized (CONSOLE_LONG_DATE_FORMAT) {
            return CONSOLE_LONG_DATE_FORMAT.format(date);
        }
    }

    public static String formatConsoleShortDate(Date date){
        if (date == null) {
            return null;
        }
        synchronized (CONSOLE_SHORT_DATE_FORMAT) {
            return CONSOLE_SHORT_DATE_FORMAT.format(date);
        }
    }

    public static SVNDate parseDate(String str) {
        if (str == null) {
            return NULL;
        }
        try {
            return parseDatestamp(str);
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, th);
        }
        return NULL;
    }

    public static Date parseDateString(String str) throws SVNException {
        try {
            return parseDatestamp(str);
        } catch (SVNException svne) {
            throw svne;
        } catch (Throwable th) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_DATE);
            SVNErrorManager.error(err, th, SVNLogType.DEFAULT);
        }
        return NULL;
    }

    private static SVNDate parseDatestamp(String str) throws SVNException {
        if (str == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_DATE);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }

        int index = 0;
        int charIndex = 0;
        int startIndex = 0;
        int[] result = new int[7];
        int microseconds = 0;
        int timeZoneInd = -1;
        while (index < DATE_SEPARATORS.length && charIndex < str.length()) {
            if (str.charAt(charIndex) == '-') {
                if (index > 1) {
                    timeZoneInd = charIndex;
                }
            } else if (str.charAt(charIndex) == '+') {
                timeZoneInd = charIndex;
            }
            if (str.charAt(charIndex) == DATE_SEPARATORS[index] ||
                    (index == 5 && str.charAt(charIndex) == DATE_SEPARATORS[index + 1])) {
                if (index == 5 && str.charAt(charIndex) == DATE_SEPARATORS[index + 1]) {
                    index++;
                }
                String segment = str.substring(startIndex, charIndex);
                if (segment.length() == 0) {
                    result[index] = 0;
                } else if (index + 1 < DATE_SEPARATORS.length) {
                    result[index] = Integer.parseInt(segment);
                } else {
                    result[index] = Integer.parseInt(segment.substring(0, Math.min(3, segment.length())));
                    if (segment.length() > 3) {
                        microseconds = Integer.parseInt(segment.substring(3));
                    }
                }
                startIndex = charIndex + 1;
                index++;
            }
            charIndex++;
        }
        if (index < DATE_SEPARATORS.length) {
            String segment = str.substring(startIndex);
            if (segment.length() == 0) {
                result[index] = 0;
            } else {
                result[index] = Integer.parseInt(segment);
            }
        }

        int year = result[0];
        int month = result[1];
        int date = result[2];

        int hour = result[3];
        int min = result[4];
        int sec = result[5];
        int ms = result[6];

        String timeZoneId = null;
        if (timeZoneInd != -1 && timeZoneInd < str.length() - 1 && str.indexOf('Z') == -1 && str.indexOf('z') == -1) {
            timeZoneId = "GMT" + str.substring(timeZoneInd);
        }
        synchronized (CALENDAR) {
            CALENDAR.clear();
            TimeZone oldTimeZone = null;
            if (timeZoneId != null) {
                oldTimeZone = CALENDAR.getTimeZone();
                CALENDAR.setTimeZone(TimeZone.getTimeZone(timeZoneId));
            } else if (str.indexOf('Z') == -1 && str.indexOf('z') == -1) {
                oldTimeZone = CALENDAR.getTimeZone();
                CALENDAR.setTimeZone(TimeZone.getDefault());
            }

            CALENDAR.set(year, month - 1, date, hour, min, sec);
            CALENDAR.set(Calendar.MILLISECOND, ms);
            SVNDate resultDate = new SVNDate(CALENDAR.getTimeInMillis(), microseconds);
            if (oldTimeZone != null) {
                CALENDAR.setTimeZone(oldTimeZone);
            }
            return resultDate;
        }
    }

    public static long parseDateAsMilliseconds(String str) {
        if (str == null) {
            return -1;
        }
        int index = 0;
        int charIndex = 0;
        int startIndex = 0;
        int[] result = new int[7];
        while (index < DATE_SEPARATORS.length && charIndex < str.length()) {
            if (str.charAt(charIndex) == DATE_SEPARATORS[index]) {
                String segment = str.substring(startIndex, charIndex);
                if (segment.length() == 0) {
                    result[index] = 0;
                } else if (index + 1 < DATE_SEPARATORS.length) {
                    try {
                        result[index] = Integer.parseInt(segment);
                    } catch (NumberFormatException nfe) {
                        return -1;
                    }
                } else {
                    try {
                        result[index] = Integer.parseInt(segment.substring(0, Math.min(3, segment.length())));
                    } catch (NumberFormatException nfe) {
                        return -1;
                    }
                }
                startIndex = charIndex + 1;
                index++;
            }
            charIndex++;
        }
        int year = result[0];
        int month = result[1];
        int date = result[2];

        int hour = result[3];
        int min = result[4];
        int sec = result[5];
        int ms = result[6];

        synchronized (CALENDAR) {
            CALENDAR.clear();
            CALENDAR.set(year, month - 1, date, hour, min, sec);
            CALENDAR.set(Calendar.MILLISECOND, ms);
            return CALENDAR.getTimeInMillis();
        }
    }

    public int hashCode() {
        return 31 * super.hashCode() + myMicroSeconds;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof SVNDate) {
            SVNDate date = (SVNDate) obj;
            return getTime() == date.getTime() && myMicroSeconds == date.myMicroSeconds;
        }
        return super.equals(obj);
    }

    public boolean before(Date when) {
        if (super.equals(when) && when instanceof SVNDate) {
            return myMicroSeconds < ((SVNDate) when).myMicroSeconds;
        }
        return super.before(when);
    }

    public boolean after(Date when) {
        if (super.equals(when) && when instanceof SVNDate) {
            return myMicroSeconds > ((SVNDate) when).myMicroSeconds;
        }
        return super.after(when);
    }

    public int compareTo(Date anotherDate) {
        int result = super.compareTo(anotherDate);
        if (result == 0 && anotherDate instanceof SVNDate) {
            SVNDate date = (SVNDate) anotherDate;
            return (myMicroSeconds < date.myMicroSeconds ? -1 : (myMicroSeconds == date.myMicroSeconds ? 0 : 1));
        }
        return result;
    }

    public long getTimeInMicros() {
        return 1000 * getTime() + myMicroSeconds;
    }
}
