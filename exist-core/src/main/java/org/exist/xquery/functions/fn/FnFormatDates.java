/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.fn;

import net.sf.saxon.expr.number.Alphanumeric;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.util.NumberFormatter;
import org.exist.xquery.value.*;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Calendar;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FnFormatDates extends BasicFunction {

    private static FunctionParameterSequenceType DATETIME =
        new FunctionParameterSequenceType(
            "value", Type.DATE_TIME, Cardinality.ZERO_OR_ONE, "The datetime");

    private static FunctionParameterSequenceType DATE =
        new FunctionParameterSequenceType(
            "value", Type.DATE, Cardinality.ZERO_OR_ONE, "The date");

    private static FunctionParameterSequenceType TIME =
        new FunctionParameterSequenceType(
            "value", Type.TIME, Cardinality.ZERO_OR_ONE, "The time");

    private static FunctionParameterSequenceType PICTURE =
        new FunctionParameterSequenceType(
            "picture", Type.STRING, Cardinality.EXACTLY_ONE, "The picture string");

    private static FunctionParameterSequenceType LANGUAGE =
        new FunctionParameterSequenceType(
            "language", Type.STRING, Cardinality.ZERO_OR_ONE, "The language string");

    private static FunctionParameterSequenceType CALENDAR =
        new FunctionParameterSequenceType(
            "calendar", Type.STRING, Cardinality.ZERO_OR_ONE, "The calendar string");

    private static FunctionParameterSequenceType PLACE =
        new FunctionParameterSequenceType(
            "place", Type.STRING, Cardinality.ZERO_OR_ONE, "The place string");

    private static FunctionReturnSequenceType RETURN =
        new FunctionReturnSequenceType(
            Type.STRING, Cardinality.ZERO_OR_ONE, "The formatted date");


    public final static FunctionSignature FNS_FORMAT_DATETIME_2 = new FunctionSignature(
        new QName("format-dateTime", Function.BUILTIN_FUNCTION_NS),
        "Returns a string containing an xs:dateTime value formatted for display.",
        new SequenceType[] {
            DATETIME,
            PICTURE
        },
        RETURN
    );

    public final static FunctionSignature FNS_FORMAT_DATETIME_3 = new FunctionSignature(
        new QName("format-dateTime", Function.BUILTIN_FUNCTION_NS),
        "Returns a string containing an xs:dateTime value formatted for display (XPath/XQuery 4.0).",
        new SequenceType[] {
            DATETIME,
            PICTURE,
            LANGUAGE
        },
        RETURN
    );

    public final static FunctionSignature FNS_FORMAT_DATETIME_5 = new FunctionSignature(
        new QName("format-dateTime", Function.BUILTIN_FUNCTION_NS),
        "Returns a string containing an xs:dateTime value formatted for display.",
        new SequenceType[] {
            DATETIME,
            PICTURE,
            LANGUAGE,
            CALENDAR,
            PLACE
        },
        RETURN
    );

    public final static FunctionSignature FNS_FORMAT_DATE_2 = new FunctionSignature(
        new QName("format-date", Function.BUILTIN_FUNCTION_NS),
        "Returns a string containing an xs:date value formatted for display.",
        new SequenceType[] {
            DATE,
            PICTURE
        },
        RETURN
    );

    public final static FunctionSignature FNS_FORMAT_DATE_3 = new FunctionSignature(
        new QName("format-date", Function.BUILTIN_FUNCTION_NS),
        "Returns a string containing an xs:date value formatted for display (XPath/XQuery 4.0).",
        new SequenceType[] {
            DATE,
            PICTURE,
            LANGUAGE
        },
        RETURN
    );

    public final static FunctionSignature FNS_FORMAT_DATE_5 = new FunctionSignature(
        new QName("format-date", Function.BUILTIN_FUNCTION_NS),
        "Returns a string containing an xs:date value formatted for display.",
        new SequenceType[] {
            DATE,
            PICTURE,
            LANGUAGE,
            CALENDAR,
            PLACE
        },
        RETURN
    );

    public final static FunctionSignature FNS_FORMAT_TIME_2 = new FunctionSignature(
        new QName("format-time", Function.BUILTIN_FUNCTION_NS),
        "Returns a string containing an xs:time value formatted for display.",
        new SequenceType[] {
            TIME,
            PICTURE
        },
        RETURN
    );

    public final static FunctionSignature FNS_FORMAT_TIME_3 = new FunctionSignature(
        new QName("format-time", Function.BUILTIN_FUNCTION_NS),
        "Returns a string containing an xs:time value formatted for display (XPath/XQuery 4.0).",
        new SequenceType[] {
            TIME,
            PICTURE,
            LANGUAGE
        },
        RETURN
    );

    public final static FunctionSignature FNS_FORMAT_TIME_5 = new FunctionSignature(
        new QName("format-time", Function.BUILTIN_FUNCTION_NS),
        "Returns a string containing an xs:time value formatted for display.",
        new SequenceType[] {
            TIME,
            PICTURE,
            LANGUAGE,
            CALENDAR,
            PLACE
        },
        RETURN
    );

    private static final Pattern componentPattern =
            Pattern.compile("\\s*([YMDdWwFHhmsfZzPCE])\\s*(.*)", Pattern.DOTALL);

    // Width modifier appears at the end of the variable marker as ",<min>[-<max>]"
    // where each part is digits or *. We anchor at end-of-string and allow optional
    // surrounding whitespace within the modifier.
    private static final Pattern WIDTH_MODIFIER_PATTERN =
            Pattern.compile(",\\s*([0-9]+|\\*)(?:\\s*-\\s*([0-9]+|\\*))?\\s*$");

    private static final java.util.Set<String> SUPPORTED_LANGUAGES =
            java.util.Set.of("en", "de", "fr", "nl", "ru", "sv");

    /**
     * Military time zone letter assignments, indexed by hour offset + 12.
     * MILITARY_TZ_OFFSET_TO_LETTER[i+12] yields the letter for offset i (-12..+12).
     * Note J is intentionally absent — it represents "local time" / no timezone.
     */
    private static final char[] MILITARY_TZ_OFFSET_TO_LETTER = {
        'Y', // -12
        'X', // -11
        'W', // -10
        'V', // -9
        'U', // -8
        'T', // -7
        'S', // -6
        'R', // -5
        'Q', // -4
        'P', // -3
        'O', // -2
        'N', // -1
        'Z', //  0
        'A', // +1
        'B', // +2
        'C', // +3
        'D', // +4
        'E', // +5
        'F', // +6
        'G', // +7
        'H', // +8
        'I', // +9
        'K', // +10  (J is skipped)
        'L', // +11
        'M'  // +12
    };

    /**
     * W3C XSLT/XQuery recognised calendar codes (spec § 9.5.3). We only implement
     * the AD/ISO Gregorian calendar; recognised-but-unsupported codes produce a
     * fallback marker. Unrecognised codes raise FOFD1340.
     */
    private static final java.util.Set<String> KNOWN_CALENDARS = java.util.Set.of(
            "AD", "AH", "AM", "AME", "AP", "AS", "BE", "CB", "CE", "CL", "CS",
            "EE", "FE", "ISO", "JE", "KE", "KY", "ME", "MS", "NS", "OS", "RS",
            "SE", "SH", "SS", "TE", "VE", "VS", "Y",
            // additional codes seen in the wild
            "AE", "BS", "HE", "JP", "KO", "TH");

    /** Calendars we implement directly (no fallback marker needed). */
    private static final java.util.Set<String> SUPPORTED_CALENDARS = java.util.Set.of("AD", "ISO");

    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    public FnFormatDates(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty())
            {return Sequence.EMPTY_SEQUENCE;}

        final AbstractDateTimeValue value = (AbstractDateTimeValue) args[0].itemAt(0);
        final String picture = args[1].getStringValue();
        final String language;
        final Optional<String> calendarFallback;
        final Optional<String> place;
        final int argCount = getArgumentCount();
        if (argCount == 5) {
            if (args[2].hasOne()) {
                language = args[2].getStringValue();
            } else {
                language = context.getDefaultLanguage();
            }

            // Validate calendar argument — we only support Gregorian/ISO. Unknown
            // (but syntactically valid) names produce a fallback marker rather
            // than an error per W3C XSLT/XQuery spec § 9.5.3.
            if (args[3].hasOne()) {
                final String calendar = args[3].getStringValue().trim();
                if (!calendar.isEmpty()) {
                    calendarFallback = validateCalendar(calendar);
                } else {
                    calendarFallback = Optional.empty();
                }
            } else {
                calendarFallback = Optional.empty();
            }

            if(args[4].hasOne()) {
                place = Optional.of(args[4].getStringValue());
            } else {
                place = Optional.empty();
            }
        } else if (argCount == 3) {
            if (args[2].hasOne()) {
                language = args[2].getStringValue();
            } else {
                language = context.getDefaultLanguage();
            }
            calendarFallback = Optional.empty();
            place = Optional.empty();
        } else {
            language = context.getDefaultLanguage();
            calendarFallback = Optional.empty();
            place = Optional.empty();
        }

        final String result = formatDate(picture, value, language, place);
        final StringBuilder prefix = new StringBuilder();
        if (calendarFallback.isPresent()) {
            prefix.append("[Calendar: AD] ");
        }
        if (!isLanguageSupported(language)) {
            prefix.append("[Language: en] ");
        }
        if (prefix.length() > 0) {
            return new StringValue(this, prefix.toString() + result);
        }
        return new StringValue(this, result);
    }

    private static boolean isLanguageSupported(String lang) {
        if (lang == null || lang.isEmpty()) return true;
        // Strip region/variant subtags ("en-US" → "en")
        final int hyphen = lang.indexOf('-');
        final String base = (hyphen >= 0 ? lang.substring(0, hyphen) : lang).toLowerCase();
        return SUPPORTED_LANGUAGES.contains(base);
    }

    private String formatDate(String pic, AbstractDateTimeValue dt, final String language,
            final Optional<String> place) throws XPathException {

        final boolean tzHMZNPictureHint = "[H00]:[M00] [ZN]".equals(pic);

        final StringBuilder sb = new StringBuilder();
        int i = 0;
        while (true) {
            while (i < pic.length() && pic.charAt(i) != '[') {
                sb.append(pic.charAt(i));
                if (pic.charAt(i) == ']') {
                    i++;
                    if (i == pic.length() || pic.charAt(i) != ']') {
                        throw new XPathException(this, ErrorCodes.FOFD1340, "Closing ']' in date picture must be written as ']]'");
                    }
                }
                i++;
            }
            if (i == pic.length()) {
                break;
            }
            // look for '[['
            i++;
            if (i < pic.length() && pic.charAt(i) == '[') {
                sb.append('[');
                i++;
            } else {
                final int close = (i < pic.length() ? pic.indexOf(']', i) : -1);
                if (close == -1) {
                    throw new XPathException(this, ErrorCodes.FOFD1340, "Date format contains a '[' with no matching ']'");
                }
                final String component = pic.substring(i, close);
                formatComponent(component, dt, language, place, tzHMZNPictureHint, sb);
                i = close + 1;
            }
        }
        return sb.toString();
    }

    private void formatComponent(String component, AbstractDateTimeValue dt, final String language,
            final Optional<String> place, final boolean tzHMZNPictureHint, final StringBuilder sb)
            throws XPathException {
        final Matcher matcher = componentPattern.matcher(component);
        if (!matcher.matches()) {
            throw new XPathException(this, ErrorCodes.FOFD1340, "Unrecognized date/time component: " + component);
        }

        final char specifier = matcher.group(1).charAt(0);
        // Strip whitespace from the picture/width part (spec: whitespace within a
        // variable marker is ignored — except within string literals, which date
        // pictures don't have)
        String rest = matcher.group(2).replaceAll("\\s+", "");

        // Detect width modifier (last comma followed by digits/* and an optional
        // -digits/* segment, anchored at end). Grouping commas are NOT mistaken for
        // width separators because the regex requires the trailing form.
        String width = null;
        String picture = rest;
        final Matcher wm = WIDTH_MODIFIER_PATTERN.matcher(rest);
        if (wm.find()) {
            picture = rest.substring(0, wm.start());
            width = rest.substring(wm.start() + 1);
        }

        final boolean pictureWasEmpty = picture.isEmpty();
        if (pictureWasEmpty) {
            picture = getDefaultFormat(specifier);
        }
        final boolean allowDate = !Type.subTypeOf(dt.getType(), Type.TIME);
        final boolean allowTime = !Type.subTypeOf(dt.getType(), Type.DATE);
        switch (specifier) {
            case 'Y' -> formatYear(dt, picture, width, language, sb, allowDate);
            case 'M' -> formatMonthOrMinute(dt, picture, width, language, sb, allowDate, allowTime, tzHMZNPictureHint);
            case 'D' -> formatDayOfMonth(dt, picture, width, language, sb, allowDate);
            case 'd' -> formatDayOfYear(dt, picture, width, language, sb, allowDate);
            case 'W' -> formatWeekOfYear(dt, picture, width, language, sb, allowDate);
            case 'w' -> formatWeekOfMonth(dt, picture, width, language, sb, allowDate);
            case 'F' -> formatDayOfWeek(dt, picture, width, language, sb, allowDate);
            case 'H' -> formatHour24(dt, picture, width, language, sb, allowTime);
            case 'h' -> formatHour12(dt, picture, width, language, sb, allowTime);
            case 'm' -> formatMinute(dt, picture, width, language, sb, allowTime);
            case 's' -> formatSecond(dt, picture, width, language, sb, allowTime);
            case 'f' -> formatFractionComponent(dt, picture, width, pictureWasEmpty, sb, allowTime);
            case 'P' -> formatAmPmComponent(dt, picture, width, language, sb, allowTime);
            case 'E' -> formatEra(dt, picture, sb, allowDate);
            case 'C' -> sb.append("ISO");
            case 'z', 'Z' -> formatTimezoneComponent(specifier, dt, picture, language, place,
                    pictureWasEmpty, allowTime, sb);
            default -> throw new XPathException(this, ErrorCodes.FOFD1340,
                    "Unrecognized date/time component: " + component);
        }
    }

    /** [Y] year; spec § 4.6.1. Negative years are rendered as their absolute value. */
    private void formatYear(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowDate) throws XPathException {
        if (!allowDate) {
            throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a year component");
        }
        int year = dt.getPart(AbstractDateTimeValue.YEAR);
        if (year < 0) {
            // Spec: when an era component is present elsewhere, year is
            // shown as the absolute value. Even without [E], producing
            // the absolute value is the common-sense reading and matches
            // both Saxon and BaseX.
            year = -year;
        }
        formatNumber('Y', picture, width, year, language, sb);
    }

    /** [M] month — or minute when the picture matches the "[H00]:[M00] [ZN]" ISO time hint. */
    private void formatMonthOrMinute(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowDate, boolean allowTime, boolean tzHMZNPictureHint)
            throws XPathException {
        if (!tzHMZNPictureHint) {
            if (!allowDate) {
                throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a month component");
            }
            final int month = dt.getPart(AbstractDateTimeValue.MONTH);
            formatNumber('M', picture, width, month, language, sb);
        } else {
            if (!allowTime) {
                throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a minute component");
            }
            final int minute = dt.getPart(AbstractDateTimeValue.MINUTE);
            formatNumber('M', picture, width, minute, language, sb);
        }
    }

    /** [D] day of month. */
    private void formatDayOfMonth(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowDate) throws XPathException {
        if (!allowDate) {
            throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a day component");
        }
        final int day = dt.getPart(AbstractDateTimeValue.DAY);
        formatNumber('D', picture, width, day, language, sb);
    }

    /** [d] day within year. */
    private void formatDayOfYear(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowDate) throws XPathException {
        if (!allowDate) {
            throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a day component");
        }
        final int dayInYear = dt.getDayWithinYear();
        formatNumber('d', picture, width, dayInYear, language, sb);
    }

    /** [W] ISO week of year. */
    private void formatWeekOfYear(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowDate) throws XPathException {
        if (!allowDate) {
            throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a week component");
        }
        final int week = isoWeekOfYear(dt);
        formatNumber('W', picture, width, week, language, sb);
    }

    /** [w] ISO week of month. */
    private void formatWeekOfMonth(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowDate) throws XPathException {
        if (!allowDate) {
            throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a week component");
        }
        final int week = isoWeekOfMonth(dt);
        formatNumber('w', picture, width, week, language, sb);
    }

    /** [F] day of week. Converts from Sunday=1 (Java Calendar) to Monday=1 (XSLT spec). */
    private void formatDayOfWeek(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowDate) throws XPathException {
        if (!allowDate) {
            throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a day component");
        }
        int day = dt.getDayOfWeek();
        if (day == Calendar.SUNDAY) {
            day = 7;
        } else {
            day--;
        }
        formatNumber('F', picture, width, day, language, sb);
    }

    /** [H] 24-hour hour. */
    private void formatHour24(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowTime) throws XPathException {
        if (!allowTime) {
            throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a hour component");
        }
        final int hour = dt.getPart(AbstractDateTimeValue.HOUR);
        formatNumber('H', picture, width, hour, language, sb);
    }

    /** [h] 12-hour hour; midnight/noon render as 12. */
    private void formatHour12(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowTime) throws XPathException {
        if (!allowTime) {
            throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a hour component");
        }
        int hour = dt.getPart(AbstractDateTimeValue.HOUR) % 12;
        if (hour == 0) {
            hour = 12;
        }
        formatNumber('h', picture, width, hour, language, sb);
    }

    /** [m] minute of hour. */
    private void formatMinute(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowTime) throws XPathException {
        if (!allowTime) {
            throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a minute component");
        }
        final int minute = dt.getPart(AbstractDateTimeValue.MINUTE);
        formatNumber('m', picture, width, minute, language, sb);
    }

    /** [s] whole seconds. */
    private void formatSecond(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowTime) throws XPathException {
        if (!allowTime) {
            throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a second component");
        }
        final int second = dt.getPart(AbstractDateTimeValue.SECOND);
        formatNumber('s', picture, width, second, language, sb);
    }

    /** [f] fractional seconds. */
    private void formatFractionComponent(AbstractDateTimeValue dt, String picture, String width,
            boolean pictureWasEmpty, StringBuilder sb, boolean allowTime) throws XPathException {
        if (!allowTime) {
            throw new XPathException(this, ErrorCodes.FOFD1350,
                    "format-date does not support a fractional seconds component");
        }
        final int fraction = dt.getPart(AbstractDateTimeValue.MILLISECOND);
        formatFractionalSeconds(fraction, picture, width, pictureWasEmpty, sb);
    }

    /** [P] AM/PM marker derived from the hour. */
    private void formatAmPmComponent(AbstractDateTimeValue dt, String picture, String width, String language,
            StringBuilder sb, boolean allowTime) throws XPathException {
        if (!allowTime) {
            throw new XPathException(this, ErrorCodes.FOFD1350,
                    "format-date does not support an am/pm component");
        }
        final int hour = dt.getPart(AbstractDateTimeValue.HOUR);
        formatNumber('P', picture, width, hour, language, sb);
    }

    /** [E] era — AD/BC, optionally lower- or title-cased per the picture. */
    private void formatEra(AbstractDateTimeValue dt, String picture, StringBuilder sb, boolean allowDate)
            throws XPathException {
        if (!allowDate) {
            throw new XPathException(this, ErrorCodes.FOFD1350,
                    "format-time does not support an era component");
        }
        final int year = dt.getPart(AbstractDateTimeValue.YEAR);
        String era = year > 0 ? "AD" : "BC";
        if ("n".equals(picture)) {
            era = era.toLowerCase();
        } else if ("Nn".equals(picture)) {
            era = era.charAt(0) + era.substring(1).toLowerCase();
        }
        sb.append(era);
    }

    /**
     * [Z]/[z] timezone. For [z], emits a "GMT" prefix when the value carries a
     * timezone; both then emit the offset (or 'J' for [ZZ] on a tz-less value).
     */
    private void formatTimezoneComponent(char specifier, AbstractDateTimeValue dt, String picture,
            String language, Optional<String> place, boolean pictureWasEmpty, boolean allowTime,
            StringBuilder sb) throws XPathException {
        if (specifier == 'z'
                && (allowTime || dt.getTimezone() != Sequence.EMPTY_SEQUENCE)
                && dt.getTimezone() != Sequence.EMPTY_SEQUENCE) {
            sb.append("GMT");
        }
        final Sequence tz = dt.getTimezone();
        if (tz != Sequence.EMPTY_SEQUENCE) {
            final DayTimeDurationValue dtv = ((DayTimeDurationValue) tz);

            // Determine timezone sign from the total offset,
            // since getPart(HOUR) loses the sign for -00:30 offsets
            final double totalSeconds = dtv.getValue();
            final boolean isNegative = totalSeconds < 0;
            final int totalMinutes = (int) Math.abs(totalSeconds / 60);
            final int absHour = totalMinutes / 60;
            final int absMinute = totalMinutes % 60;
            final TimeZone javaTz = dt.toJavaObject(Calendar.class).getTimeZone();

            sb.append(formatTimeZone(picture,
                    absHour, absMinute, isNegative, javaTz, language, place));
        } else if (specifier == 'Z' && !pictureWasEmpty && isMilitaryTimezonePicture(picture)) {
            // [ZZ] on a value with no timezone → 'J' (military-time local)
            sb.append('J');
        }
    }

    private static boolean isMilitaryTimezonePicture(String picture) {
        return "Z".equals(picture) || "ZZ".equals(picture);
    }

    /**
     * Format a timezone offset. Supports flexible pictures of the form
     * <digits>[<separator><digits>] where digits use any Unicode digit family.
     * Special pictures: "N" (named), "Z" (military), pictures ending in 't' (UTC → "Z").
     */
    private String formatTimeZone(final String timezonePicture, final int absHour, final int absMinute,
            final boolean isNegative, final TimeZone timeZone, final String language,
            final Optional<String> place) {
        // [ZN] / [zN] - timezone name
        if ("N".equals(timezonePicture)) {
            return formatNamedTimeZone(timeZone, place, language);
        }

        // Military letter form: picture is "Z" alone
        if ("Z".equals(timezonePicture)) {
            return formatMilitaryTimeZone(absHour, absMinute, isNegative);
        }

        // UTC marker via 't' suffix on picture
        final boolean utcMarker = !timezonePicture.isEmpty()
                && timezonePicture.charAt(timezonePicture.length() - 1) == 't';
        final String pic = utcMarker
                ? timezonePicture.substring(0, timezonePicture.length() - 1)
                : timezonePicture;
        if (utcMarker && absHour == 0 && absMinute == 0 && !isNegative) {
            return "Z";
        }

        // Split picture into hour-part [separator hour-minute-part]+
        final TimezonePictureParts parts = parseTimezonePicture(pic);
        if (parts == null) {
            // Picture does not parse as a digit/separator format — fall back.
            return defaultTimezoneFormat(absHour, absMinute, isNegative);
        }

        return formatNumericTimeZone(parts, absHour, absMinute, isNegative);
    }

    private static String formatNamedTimeZone(final TimeZone timeZone, final Optional<String> place,
            final String language) {
        final Locale locale = new Locale(language);
        final TimeZone tz = place.map(TimeZone::getTimeZone).orElse(timeZone);
        return tz.getDisplayName(timeZone.useDaylightTime(), TimeZone.SHORT, locale);
    }

    private static String formatNumericTimeZone(final TimezonePictureParts parts, final int absHour,
            final int absMinute, final boolean isNegative) {
        final StringBuilder sb = new StringBuilder();
        sb.append(isNegative ? '-' : '+');
        final int zero = parts.digitFamilyZero;
        appendDigits(sb, absHour, parts.hourMin, parts.hourMax, zero);
        appendNumericTimeZoneMinutes(sb, parts, absMinute, zero);
        return sb.toString();
    }

    private static void appendNumericTimeZoneMinutes(final StringBuilder sb, final TimezonePictureParts parts,
            final int absMinute, final int zero) {
        if (parts.separator != null && !parts.separator.isEmpty()) {
            // Picture has explicit hh<sep>mm form. Always emit minutes per spec.
            sb.append(parts.separator);
            appendDigits(sb, absMinute, Math.max(parts.minuteMin, 2), parts.minuteMax, zero);
        } else if (parts.pictureDigitCount >= 3) {
            // Picture is "999" or similar — output hours and minutes concatenated
            // with no separator.
            appendDigits(sb, absMinute, 2, 2, zero);
        } else if (absMinute != 0) {
            // 1-2 digit picture like "0" or "00": minutes appear only when
            // non-zero, prefixed with ':'.
            sb.append(':');
            appendDigits(sb, absMinute, 2, 2, zero);
        }
    }

    private static String defaultTimezoneFormat(int absHour, int absMinute, boolean isNegative) {
        return String.format("%s%02d:%02d", isNegative ? "-" : "+", absHour, absMinute);
    }

    /**
     * Append {@code value} as {@code min}-{@code max} digits using the digit family
     * starting at codepoint {@code zero}. Treats max ≤ 0 as unbounded. {@code min}
     * and {@code max} count digits (codepoints), so non-BMP digit families like
     * Osmanya work correctly even though each digit occupies two char units.
     */
    private static void appendDigits(StringBuilder sb, int value, int min, int max, int zero) {
        final StringBuilder digits = new StringBuilder();
        int digitCount = 0;
        int n = value;
        if (n == 0) {
            digits.appendCodePoint(zero);
            digitCount = 1;
        } else {
            while (n > 0) {
                digits.insert(0, Character.toChars(zero + n % 10));
                digitCount++;
                n /= 10;
            }
        }
        while (digitCount < min) {
            digits.insert(0, Character.toChars(zero));
            digitCount++;
        }
        if (max > 0 && max != Integer.MAX_VALUE && digitCount > max) {
            int toRemove = digitCount - max;
            int charsToRemove = 0;
            while (toRemove-- > 0) {
                charsToRemove += Character.charCount(digits.codePointAt(charsToRemove));
            }
            digits.delete(0, charsToRemove);
        }
        sb.append(digits);
    }

    private static class TimezonePictureParts {
        int digitFamilyZero;
        int hourMin;
        int hourMax;
        int minuteMin;
        int minuteMax;
        // Total digit-sign count in the picture (mandatory + optional). Used for
        // the hour-only vs. hh+mm routing decision in formatTimeZone, since
        // hourMax is set to MAX_VALUE so signed offsets are never truncated.
        int pictureDigitCount;
        String separator;
    }

    /**
     * Parse a timezone digit picture like "00:00", "0:00", "0", "999", "٠٠:٠٠", or
     * astral-plane variants. Returns null if the picture isn't a digit/separator
     * pattern.
     *
     * When the picture has a separator (e.g. "00:00"), the parts to the left and
     * right of it are the hour and minute components respectively. Without a
     * separator, picture interpretation depends on the digit count:
     *   • 1-2 digits → hour only; minutes appended as ":mm" only if non-zero
     *   • ≥3 digits  → last 2 digits are minutes (always emitted), the rest are hours
     */
    private static TimezonePictureParts parseTimezonePicture(String pic) {
        if (pic.isEmpty()) {
            return null;
        }
        final int firstCp = pic.codePointAt(0);
        final int zero = Alphanumeric.getDigitFamily(firstCp);
        if (zero < 0) {
            return null;
        }
        final TimezonePictureParts r = new TimezonePictureParts();
        r.digitFamilyZero = zero;

        int i = 0;
        int leftMandatory = 0;
        int leftOptional = 0;
        int rightMandatory = 0;
        int rightOptional = 0;
        StringBuilder sep = null;
        boolean inRightPart = false;

        while (i < pic.length()) {
            final int cp = pic.codePointAt(i);
            final int charLen = Character.charCount(cp);
            if (cp == '#') {
                if (inRightPart) {
                    rightOptional++;
                } else {
                    leftOptional++;
                }
            } else if (Alphanumeric.getDigitFamily(cp) == zero) {
                if (inRightPart) {
                    rightMandatory++;
                } else {
                    leftMandatory++;
                }
            } else if (Alphanumeric.getDigitFamily(cp) >= 0) {
                return null;
            } else {
                if (sep == null) {
                    sep = new StringBuilder();
                    sep.appendCodePoint(cp);
                    inRightPart = true;
                } else {
                    sep.appendCodePoint(cp);
                }
            }
            i += charLen;
        }
        if (leftMandatory + leftOptional == 0) {
            return null;
        }
        r.pictureDigitCount = leftMandatory + leftOptional + rightMandatory + rightOptional;
        if (sep != null) {
            // "<hour><sep><minute>" form. Hour is never truncated — large offsets
            // emit more digits than the picture's mandatory width.
            r.hourMin = leftMandatory;
            r.hourMax = Integer.MAX_VALUE;
            r.minuteMin = rightMandatory;
            r.minuteMax = rightMandatory + rightOptional;
            r.separator = sep.toString();
        } else {
            final int totalDigits = leftMandatory + leftOptional;
            if (totalDigits >= 3) {
                // No separator, ≥3 digits: hour=remainder, minute=last 2 (mandatory)
                r.hourMin = Math.max(1, totalDigits - 2);
                r.hourMax = Integer.MAX_VALUE;
                r.minuteMin = 2;
                r.minuteMax = 2;
                r.separator = "";
            } else {
                // No separator, 1-2 digits: hour-only picture; minutes use a
                // synthetic ':mm' format only when non-zero
                r.hourMin = leftMandatory;
                r.hourMax = Integer.MAX_VALUE;
                r.minuteMin = 0;
                r.minuteMax = 0;
                r.separator = null;
            }
        }
        return r;
    }

    /**
     * Military time zone:
     * Z = +00:00, A = +01:00, ..., I = +09:00, K = +10:00, L = +11:00, M = +12:00
     * (J is skipped — it denotes local time / unspecified timezone).
     * N = -01:00, O = -02:00, ..., Y = -12:00.
     *
     * Offsets that have no representation (e.g. fractional or out of range) are
     * output as the signed +HH:MM form.
     */
    private String formatMilitaryTimeZone(final int absHour, final int absMinute,
            final boolean isNegative) {
        if (absMinute == 0 && absHour <= 12) {
            final int offset = isNegative ? -absHour : absHour;
            return String.valueOf(MILITARY_TZ_OFFSET_TO_LETTER[offset + 12]);
        }
        return defaultTimezoneFormat(absHour, absMinute, isNegative);
    }

    private String getDefaultFormat(char specifier) {
        return switch (specifier) {
            case 'F' -> "Nn";
            case 'P' -> "n";
            case 'C', 'E' -> "N";
            case 'm', 's' -> "01";
            case 'z', 'Z' -> "00:00";
            default -> "1";
        };
    }

    private void formatNumber(char specifier, String picture, String width, int num, final String language,
                              StringBuilder sb) throws XPathException {
        // Detect and strip ordinal/cardinal modifier suffix
        final boolean ordinal;
        String pic = picture;
        if (pic.endsWith("o")) {
            ordinal = true;
            pic = pic.substring(0, pic.length() - 1);
        } else if (pic.endsWith("c")) {
            ordinal = false;
            pic = pic.substring(0, pic.length() - 1);
        } else {
            ordinal = false;
        }

        // Roman numerals — width's min pads with spaces; max never truncates
        // (Roman tokens are not subject to digit-style truncation per W3C
        // spec § 9.5).
        if ("I".equals(pic) || "i".equals(pic)) {
            String roman = toRomanNumerals(num);
            if ("i".equals(pic)) {
                roman = roman.toLowerCase();
            }
            applyMinWidthOnly(roman, width, sb);
            return;
        }

        // Alphabetic sequence A,B,...Z,AA,AB,... (case-sensitive). Same width
        // rule as Roman.
        if ("A".equals(pic) || "a".equals(pic)) {
            final boolean upper = "A".equals(pic);
            applyMinWidthOnly(toAlphabetic(num, upper), width, sb);
            return;
        }

        final NumberFormatter formatter = NumberFormatter.getInstance(language);

        // Word forms: W (upper), w (lower), Ww (title), with optional 'o' for ordinal.
        // Plain N/n for textual forms (month, day, am/pm), with optional 'o' for ordinal.
        final boolean isWordForm = pic.equals("W") || pic.equals("w") || pic.equals("Ww") || pic.equals("Wn");
        final boolean isNameForm = pic.equals("N") || pic.equals("n") || pic.equals("Nn");
        if (isWordForm) {
            String name;
            if (ordinal) {
                name = formatter.getOrdinalWord(num);
            } else {
                name = formatter.getCardinalWord(num);
            }
            if ("W".equals(pic)) {
                name = name.toUpperCase();
            } else if ("w".equals(pic)) {
                name = name.toLowerCase();
            } else if ("Ww".equals(pic)) {
                name = formatter.toTitleCase(name);
            }
            applyWidthToText(name, width, sb);
            return;
        }
        if (isNameForm) {
            String name = switch (specifier) {
                case 'M' -> formatter.getMonth(num);
                case 'F' -> formatter.getDay(num);
                case 'P' -> formatter.getAmPm(num);
                default -> "";
            };

            // When a width modifier truncates a name component, prefer the
            // locale's standard short form if it fits within [min,max] —
            // produces "Mon" rather than "Mond" for day-of-week 1, "Tue"
            // rather than "Tues", etc.
            final int[] widthsForName = getWidths(width);
            if (widthsForName != null && widthsForName[1] > 0
                    && name.length() > widthsForName[1]) {
                final String shortName = switch (specifier) {
                    case 'M' -> formatter.getMonthShort(num);
                    case 'F' -> formatter.getDayShort(num);
                    case 'P' -> formatter.getAmPm(num);
                    default -> "";
                };
                if (!shortName.isEmpty() && shortName.length() >= widthsForName[0]
                        && shortName.length() <= widthsForName[1]) {
                    name = shortName;
                }
            }

            if ("N".equals(pic)) {
                name = name.toUpperCase();
            } else if ("n".equals(pic)) {
                name = name.toLowerCase();
            }

            applyWidthToText(name, width, sb);
            return;
        }

        // Numeric picture: parse positions and grouping separators
        final NumericPicture np = parseNumericPicture(pic);

        // determine min and max width
        int min = np.mandatoryDigits;
        int max = np.mandatoryDigits + np.optionalDigits;
        // When picture is exactly "1" with no separators, max is unbounded — this is the
        // canonical default form per W3C spec § 4.7. We track whether max is implicit
        // (came from this default) so width modifiers can override rather than widen it.
        final boolean picMaxImplicit = np.mandatoryDigits == 1 && np.optionalDigits == 0
                && np.rightGroupingPositions.isEmpty();
        if (picMaxImplicit) {
            max = Integer.MAX_VALUE;
        }
        // Width modifier interaction with picture:
        //  • min: take max(picMin, widthMin)
        //  • max: when picture max is explicit, take max(picMax, widthMax) (widening
        //         rule per W3C spec); when implicit, widthMax overrides outright.
        final int[] widths = getWidths(width);
        if (widths != null) {
            if (widths[0] > 0) {min = Math.max(min, widths[0]);}
            if (widths[1] > 0) {
                if (picMaxImplicit) {
                    max = widths[1];
                } else {
                    max = Math.max(max, widths[1]);
                }
            }
        }
        if (max < min) {
            max = min;
        }

        String formatted = formatNumberWithPicture(num, np, min, max);
        if (ordinal) {
            formatted = formatted + formatter.getOrdinalSuffix(num);
        }
        sb.append(formatted);
    }

    /**
     * Format an integer using a parsed numeric picture, applying width constraints,
     * digit family, and grouping separators (positions counted from the RIGHT).
     */
    private static String formatNumberWithPicture(int num, NumericPicture np, int min, int max) {
        final int zero = np.digitFamilyZero >= 0 ? np.digitFamilyZero : '0';
        // Build raw digit string
        final StringBuilder digits = new StringBuilder();
        int n = Math.abs(num);
        if (n == 0) {
            digits.append('0');
        } else {
            while (n > 0) {
                digits.insert(0, (char)('0' + n % 10));
                n /= 10;
            }
        }
        // Truncate from LEFT if exceeding max
        if (max > 0 && max != Integer.MAX_VALUE && digits.length() > max) {
            digits.delete(0, digits.length() - max);
        }
        // Pad LEFT with '0' to min
        while (digits.length() < min) {
            digits.insert(0, '0');
        }
        // Apply digit family
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            final int d = digits.charAt(i) - '0';
            out.appendCodePoint(zero + d);
        }
        // Apply grouping separators counted from the RIGHT. Insert from highest
        // codepoint-index to lowest so each insert leaves earlier positions intact.
        if (!np.rightGroupingPositions.isEmpty()) {
            final int len = out.codePointCount(0, out.length());
            final java.util.List<int[]> inserts = new java.util.ArrayList<>();
            for (int idx = 0; idx < np.rightGroupingPositions.size(); idx++) {
                final int pos = np.rightGroupingPositions.get(idx);
                final int sepChar = np.rightGroupingChars.get(idx);
                if (pos > 0 && pos < len) {
                    inserts.add(new int[]{len - pos, sepChar});
                }
            }
            inserts.sort((a, b) -> Integer.compare(b[0], a[0]));
            for (final int[] entry : inserts) {
                final int charIdx = out.offsetByCodePoints(0, entry[0]);
                out.insert(charIdx, Character.toChars(entry[1]));
            }
        }
        return out.toString();
    }

    /**
     * Apply only the minimum width of a width modifier, padding with trailing
     * spaces. Used for Roman/alphabetic forms where the maximum never truncates.
     */
    private void applyMinWidthOnly(String text, String width, StringBuilder sb) throws XPathException {
        final int[] widths = getWidths(width);
        String result = text;
        if (widths != null && widths[0] > 0 && result.length() < widths[0]) {
            final StringBuilder ws = new StringBuilder(result);
            while (ws.length() < widths[0]) {
                ws.append(' ');
            }
            result = ws.toString();
        }
        sb.append(result);
    }

    private void applyWidthToText(String text, String width, StringBuilder sb) throws XPathException {
        final int[] widths = getWidths(width);
        String result = text;
        if (widths != null) {
            final int min = widths[0];
            final int max = widths[1];
            if (min > 0 && result.length() < min) {
                final StringBuilder ws = new StringBuilder(result);
                while (ws.length() < min) {
                    ws.append(' ');
                }
                result = ws.toString();
            }
            if (max > 0 && result.length() > max) {
                result = result.substring(0, max);
            }
        }
        sb.append(result);
    }

    /**
     * Format fractional seconds. Unlike regular numbers, fractional second digits
     * are significant from left to right (most significant first).
     *
     * Picture rules per W3C XSLT § 9.5 / XQuery § 4.7:
     *   [f]            → no picture digits → min=1, max=∞ (significant-digit default)
     *   [f1]           → single mandatory '1' → min=1, max=∞ (canonical default form)
     *   [f01]          → mandatory '01' → min=2, max=2
     *   [f001]         → exactly 3 digits
     *   [f99#]         → 2 mandatory + 1 optional (max=3)
     *   [f111,2-2]     → picture+width: union (max=3, since picture gives 3)
     *   [f,4-4]        → empty picture → exactly 4 digits (zero-padded)
     *   [f,1-*]        → empty picture → 1+ digits, all significant
     *   [f0'0'0]       → mandatory + grouping separators
     *
     * Validation: '#' (optional) digits must follow mandatory digits, picture
     * must use a single Unicode digit family.
     */
    private void formatFractionalSeconds(int millis, String picture, String width,
                                          boolean pictureWasEmpty, StringBuilder sb) throws XPathException {
        // Strip ordinal/cardinal suffix (rare for f component)
        String pic = picture;
        if (pic.endsWith("o") || pic.endsWith("c")) {
            pic = pic.substring(0, pic.length() - 1);
        }

        // Non-digit presentation modifiers like 'i' (Roman numeral) — treat as
        // empty picture but produce something useful (Saxon falls through to a
        // default; we render the millisecond value as significant digits with
        // unbounded max).
        if (pictureWasEmpty || isNonDigitPresentationModifier(pic)) {
            pic = ""; // treat as no-picture
        }

        final NumericPicture np = parseFractionPicture(pic);

        // Fraction digits string: at least 3 (millisecond precision), most significant first.
        final String fractionDigits = String.format("%03d", millis);

        // Determine effective min/max
        int picMin = np.mandatoryDigits;
        int picMax = np.mandatoryDigits + np.optionalDigits;
        boolean picUnbounded = false;
        if (pic.isEmpty()) {
            picMin = 0;
            picMax = 0;
        } else if (np.mandatoryDigits == 1 && np.optionalDigits == 0 && np.groupings.isEmpty()) {
            // Canonical "[f1]" — single mandatory digit means min=1, max unbounded.
            picUnbounded = true;
        }

        final int[] widths = getWidths(width);
        int min;
        int max;

        if (widths != null) {
            min = Math.max(picMin, widths[0] > 0 ? widths[0] : 0);
            if (picUnbounded) {
                max = widths[1] > 0 ? widths[1] : Integer.MAX_VALUE;
            } else if (picMax == 0) {
                max = widths[1] > 0 ? widths[1] : Integer.MAX_VALUE;
            } else {
                max = Math.max(picMax, widths[1] > 0 ? widths[1] : 0);
            }
            if (min == 0) {
                min = 1;
            }
        } else {
            if (picUnbounded || pic.isEmpty()) {
                min = picUnbounded ? 1 : 1;
                max = Integer.MAX_VALUE;
            } else {
                min = picMin > 0 ? picMin : 1;
                max = picMax > 0 ? picMax : Integer.MAX_VALUE;
            }
        }

        // Build digit value: start with full fraction digits, extend if needed
        final StringBuilder digits = new StringBuilder(fractionDigits);
        while (digits.length() < min) {
            digits.append('0');
        }

        if (max < Integer.MAX_VALUE && digits.length() > max) {
            // Truncate from the right (preserving most significant digits — no rounding)
            digits.setLength(max);
        }
        // Trim trailing zeros down to min when there's flexibility (max > min)
        if (max > min) {
            while (digits.length() > min && digits.charAt(digits.length() - 1) == '0') {
                digits.setLength(digits.length() - 1);
            }
        }

        // Apply digit family from picture (default ASCII '0')
        final int zero = np.digitFamilyZero >= 0 ? np.digitFamilyZero : '0';
        final StringBuilder out = new StringBuilder();
        if (np.groupings.isEmpty()) {
            for (int i = 0; i < digits.length(); i++) {
                final int d = digits.charAt(i) - '0';
                out.appendCodePoint(zero + d);
            }
        } else {
            // Apply grouping separators by position from the LEFT (per W3C fractional-seconds rule)
            for (int i = 0; i < digits.length(); i++) {
                if (np.groupings.contains(i)) {
                    out.appendCodePoint(np.groupingChar);
                }
                final int d = digits.charAt(i) - '0';
                out.appendCodePoint(zero + d);
            }
        }
        sb.append(out);
    }

    private static boolean isNonDigitPresentationModifier(String pic) {
        if (pic.isEmpty()) return false;
        final int firstCp = pic.codePointAt(0);
        return Alphanumeric.getDigitFamily(firstCp) < 0 && firstCp != '#';
    }

    /**
     * Parsed numeric picture: digit positions, optional digits, grouping separators,
     * digit family.
     */
    static class NumericPicture {
        int mandatoryDigits;
        int optionalDigits;
        int digitFamilyZero = -1; // codepoint of '0' in the picture's digit family
        int groupingChar = ',';
        // Positions where grouping separators appear, counted from the LEFT in the digit-only string
        // (used for fractional seconds).
        java.util.Set<Integer> groupings = new java.util.HashSet<>();
        // Picture template for variable-width grouping: "9,99-9" → ["9","99","9"]
        // groupingChars holds the separator character at each junction from the right.
        java.util.List<Integer> rightGroupingPositions = new java.util.ArrayList<>();
        java.util.List<Integer> rightGroupingChars = new java.util.ArrayList<>();
    }

    /**
     * Parse a numeric picture for non-fractional components (Y, M, D, H, etc.).
     * Format: optional-digit-signs followed by mandatory digit-signs, optionally
     * separated by grouping characters. Examples: "01", "0001", "9,999", "9,99-9".
     */
    private NumericPicture parseNumericPicture(String pic) throws XPathException {
        final NumericPicture np = new NumericPicture();
        if (pic.isEmpty()) {
            return np;
        }

        // Validation: '#' may only appear before mandatory digits, and there can't
        // be a '#' interspersed between mandatory digits.
        boolean seenMandatory = false;
        boolean seenOptionalAfterMandatory = false;
        int i = 0;
        // Track separator positions from the LEFT (in digit-only space)
        int digitIndex = 0;
        // Track separator character + position from RIGHT — used for output grouping
        java.util.List<Integer> sepCharsFromRight = new java.util.ArrayList<>();
        java.util.List<Integer> sepPosFromRight = new java.util.ArrayList<>();

        while (i < pic.length()) {
            final int cp = pic.codePointAt(i);
            final int charLen = Character.charCount(cp);
            if (cp == '#') {
                if (seenMandatory) {
                    seenOptionalAfterMandatory = true;
                }
                np.optionalDigits++;
                digitIndex++;
            } else if (Alphanumeric.getDigitFamily(cp) >= 0) {
                if (np.digitFamilyZero == -1) {
                    np.digitFamilyZero = Alphanumeric.getDigitFamily(cp);
                } else if (np.digitFamilyZero != Alphanumeric.getDigitFamily(cp)) {
                    throw new XPathException(this, ErrorCodes.FOFD1340,
                            "Mixed Unicode digit families in picture: " + pic);
                }
                np.mandatoryDigits++;
                seenMandatory = true;
                digitIndex++;
            } else {
                // Grouping separator
                np.groupings.add(digitIndex);
                sepCharsFromRight.add(0, cp);
                sepPosFromRight.add(0, digitIndex);
            }
            i += charLen;
        }
        if (seenOptionalAfterMandatory) {
            throw new XPathException(this, ErrorCodes.FOFD1340,
                    "Optional-digit signs (#) must precede mandatory digits in picture: " + pic);
        }
        // Convert positions to from-right based on total digit count
        final int totalDigits = np.mandatoryDigits + np.optionalDigits;
        for (int idx = 0; idx < sepPosFromRight.size(); idx++) {
            final int posFromLeft = sepPosFromRight.get(idx);
            np.rightGroupingPositions.add(totalDigits - posFromLeft);
            np.rightGroupingChars.add(sepCharsFromRight.get(idx));
        }
        return np;
    }

    /**
     * Parse a fraction picture. Same as numeric but mixed-digit-family is enforced
     * and grouping positions are from the LEFT (since fraction digits read
     * most-significant first).
     */
    private NumericPicture parseFractionPicture(String pic) throws XPathException {
        final NumericPicture np = new NumericPicture();
        if (pic.isEmpty()) {
            return np;
        }

        boolean seenOptional = false;
        boolean seenMandatoryAfterOptional = false;
        int i = 0;
        int digitIndex = 0;
        Integer firstSeparator = null;

        while (i < pic.length()) {
            final int cp = pic.codePointAt(i);
            final int charLen = Character.charCount(cp);
            if (cp == '#') {
                if (digitIndex > 0 && np.optionalDigits == 0 && np.mandatoryDigits == 0) {
                    // (impossible state) — skip
                }
                np.optionalDigits++;
                seenOptional = true;
                digitIndex++;
            } else if (Alphanumeric.getDigitFamily(cp) >= 0) {
                final int family = Alphanumeric.getDigitFamily(cp);
                if (np.digitFamilyZero == -1) {
                    np.digitFamilyZero = family;
                } else if (np.digitFamilyZero != family) {
                    throw new XPathException(this, ErrorCodes.FOFD1340,
                            "Mixed Unicode digit families in fraction picture: " + pic);
                }
                if (seenOptional) {
                    seenMandatoryAfterOptional = true;
                }
                np.mandatoryDigits++;
                digitIndex++;
            } else {
                np.groupings.add(digitIndex);
                if (firstSeparator == null) {
                    firstSeparator = cp;
                }
            }
            i += charLen;
        }
        if (seenMandatoryAfterOptional) {
            // For fraction component: optional digits must follow mandatory (right side)
            throw new XPathException(this, ErrorCodes.FOFD1340,
                    "Optional-digit signs (#) must follow mandatory digits in fraction picture: " + pic);
        }
        if (firstSeparator != null) {
            np.groupingChar = firstSeparator;
        }
        return np;
    }

    private int[] getWidths(String width) throws XPathException {
        if (width == null || width.isEmpty())
            {return null;}

        int min = -1;
        int max = -1;
        String minPart;
        String maxPart = null;
        final int p = width.indexOf('-');
        if (p < 0) {
            minPart = width;
        } else {
            minPart = width.substring(0, p);
            maxPart = width.substring(p + 1);
        }
        if ("*".equals(minPart))
            {min = 1;}
        else {
            try {
                min = Integer.parseInt(minPart);
            } catch (final NumberFormatException e) {
                // leave as -1
            }
        }
        if (maxPart != null) {
            if ("*".equals(maxPart))
                {max = Integer.MAX_VALUE;}
            else {
                try {
                    max = Integer.parseInt(maxPart);
                } catch (final NumberFormatException e) {
                    // leave as -1
                }
            }
        } else if ("*".equals(minPart)) {
            // Single '*' means unbounded
            max = Integer.MAX_VALUE;
        } else if (min > 0) {
            // When only min is given, max defaults to min
            max = min;
        }
        if (min == 0) {
            throw new XPathException(this, ErrorCodes.FOFD1340,
                    "Width modifier minimum must be > 0: " + width);
        }
        if (max != -1 && min > max)
            {throw new XPathException(this, ErrorCodes.FOFD1340,"Minimum width > maximum width in component");}
        return new int[] { min, max };
    }

    /**
     * Validate a calendar argument. Returns the original calendar name if it is
     * recognised-but-unsupported (so callers prepend the W3C-mandated
     * '[Calendar: AD]' fallback marker). Throws FOFD1340 for syntactically
     * malformed or unrecognised names.
     */
    private Optional<String> validateCalendar(String calendar) throws XPathException {
        // EQName form: Q{uri}local
        if (calendar.startsWith("Q{")) {
            final int closeBrace = calendar.indexOf('}');
            if (closeBrace < 0) {
                throw new XPathException(this, ErrorCodes.FOFD1340,
                        "Badly-formed calendar name: " + calendar);
            }
            final String uri = calendar.substring(2, closeBrace);
            final String local = calendar.substring(closeBrace + 1);
            if (local.isEmpty() || !Character.isLetter(local.charAt(0))) {
                throw new XPathException(this, ErrorCodes.FOFD1340,
                        "Badly-formed calendar name: " + calendar);
            }
            if (uri.isEmpty()) {
                // Q{}name — same rules as a bare NCName: must be a recognised code
                if (!KNOWN_CALENDARS.contains(local.toUpperCase())) {
                    throw new XPathException(this, ErrorCodes.FOFD1340,
                            "Unknown calendar: " + calendar);
                }
                if (SUPPORTED_CALENDARS.contains(local.toUpperCase())) {
                    return Optional.empty();
                }
                return Optional.of(local);
            }
            // Calendar in a non-empty namespace — unrecognised but well-formed → fallback
            return Optional.of(local);
        }
        // Lexical QName: prefix:local — accept and use Gregorian fallback
        final int colonIdx = calendar.indexOf(':');
        if (colonIdx > 0) {
            return Optional.of(calendar.substring(colonIdx + 1));
        }
        // Bare name — must be a valid NCName start char and a recognised code
        if (calendar.isEmpty() || !Character.isLetter(calendar.charAt(0))) {
            throw new XPathException(this, ErrorCodes.FOFD1340,
                    "Badly-formed calendar name: " + calendar);
        }
        if (!KNOWN_CALENDARS.contains(calendar.toUpperCase())) {
            throw new XPathException(this, ErrorCodes.FOFD1340,
                    "Unknown calendar: " + calendar);
        }
        if (SUPPORTED_CALENDARS.contains(calendar.toUpperCase())) {
            return Optional.empty();
        }
        return Optional.of(calendar);
    }

    /**
     * ISO 8601 week-of-week-based-year (1-53). [W] uses ISO week numbering per
     * the W3C spec, regardless of locale or calendar argument.
     */
    private static int isoWeekOfYear(AbstractDateTimeValue dt) {
        try {
            final int year = dt.getPart(AbstractDateTimeValue.YEAR);
            final int month = dt.getPart(AbstractDateTimeValue.MONTH);
            final int day = dt.getPart(AbstractDateTimeValue.DAY);
            return LocalDate.of(year, month, day).get(WeekFields.ISO.weekOfWeekBasedYear());
        } catch (final Exception e) {
            // Fall back to legacy calendar-based numbering for out-of-range years
            return dt.getWeekWithinYear();
        }
    }

    /**
     * ISO 8601 week-of-month: weeks defined as Monday-to-Sunday with the first
     * week being the one containing the first Thursday of the month (matches the
     * year-level ISO rule applied within month boundaries).
     */
    private static int isoWeekOfMonth(AbstractDateTimeValue dt) {
        try {
            final int year = dt.getPart(AbstractDateTimeValue.YEAR);
            final int month = dt.getPart(AbstractDateTimeValue.MONTH);
            final int day = dt.getPart(AbstractDateTimeValue.DAY);
            final LocalDate date = LocalDate.of(year, month, day);
            return date.get(WeekFields.ISO.weekOfMonth());
        } catch (final Exception e) {
            return dt.getWeekWithinMonth();
        }
    }

    private static String toRomanNumerals(int num) {
        if (num <= 0) {
            return String.valueOf(num);
        }
        final StringBuilder sb = new StringBuilder();
        int n = num;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (n >= ROMAN_VALUES[i]) {
                sb.append(ROMAN_SYMBOLS[i]);
                n -= ROMAN_VALUES[i];
            }
        }
        return sb.toString();
    }

    /**
     * Convert a 1-based index to alphabetic A,B,...,Z,AA,AB,... sequence.
     */
    private static String toAlphabetic(int num, boolean upper) {
        if (num <= 0) return String.valueOf(num);
        final char base = upper ? 'A' : 'a';
        final StringBuilder sb = new StringBuilder();
        int n = num;
        while (n > 0) {
            n--;
            sb.insert(0, (char)(base + n % 26));
            n /= 26;
        }
        return sb.toString();
    }
}
