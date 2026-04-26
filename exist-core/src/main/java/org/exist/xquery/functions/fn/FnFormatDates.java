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

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.util.NumberFormatter;
import org.exist.xquery.value.*;

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
        "Returns a string containing an xs:date value formatted for display.",
        new SequenceType[] {
            DATETIME,
            PICTURE
        },
        RETURN
    );

    public final static FunctionSignature FNS_FORMAT_DATETIME_5 = new FunctionSignature(
        new QName("format-dateTime", Function.BUILTIN_FUNCTION_NS),
        "Returns a string containing an xs:date value formatted for display.",
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

    private static final Pattern componentPattern = Pattern.compile("\\s*([YMDdWwFHhmsfZzPCE])\\s*(.*)");

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
        final Optional<String> place;
        if (getArgumentCount() == 5) {
            if (args[2].hasOne()) {
                language = args[2].getStringValue();
            } else {
                language = context.getDefaultLanguage();
            }

            // Validate calendar argument — we only support Gregorian/ISO
            if (args[3].hasOne()) {
                final String calendar = args[3].getStringValue().trim();
                if (!calendar.isEmpty()) {
                    validateCalendar(calendar);
                }
            }

            if(args[4].hasOne()) {
                place = Optional.of(args[4].getStringValue());
            } else {
                place = Optional.empty();
            }
        } else {
            language = context.getDefaultLanguage();
            place = Optional.empty();
        }

        return new StringValue(this, formatDate(picture, value, language, place));
    }

    private String formatDate(String pic, AbstractDateTimeValue dt, final String language,
            final Optional<String> place) throws XPathException {

        // Per W3C spec: if $place is a recognized IANA timezone name, adjust the datetime
        // to the applicable timezone offset before formatting. This adjustment takes
        // daylight savings time into account where possible.
        if (place.isPresent()) {
            try {
                final java.time.ZoneId placeZone = java.time.ZoneId.of(place.get());
                dt = adjustToPlaceTimezone(dt, placeZone);
            } catch (final java.time.DateTimeException e) {
                // Not a recognized IANA timezone ID (e.g., country code "us") — no adjustment
            }
        }

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
        String width = null;
        // Strip whitespace from the picture/width part (spec: whitespace within a variable marker is ignored)
        String picture = matcher.group(2).replaceAll("\\s+", "");
        // check if there's an optional width specifier
        final int widthSep = picture.indexOf(',');
        if (-1 < widthSep) {
            width = picture.substring(widthSep + 1);
            picture = picture.substring(0, widthSep);
        }
        // get default format picture if none was specified
        if (picture == null || picture.isEmpty()) {
            picture = getDefaultFormat(specifier);
        }
        final boolean allowDate = !Type.subTypeOf(dt.getType(), Type.TIME);
        final boolean allowTime = !Type.subTypeOf(dt.getType(), Type.DATE);
        switch (specifier) {
            case 'Y':
                if (allowDate) {
                    final int year = dt.getPart(AbstractDateTimeValue.YEAR);
                    formatNumber(specifier, picture, width, year, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a year component");
                }
                break;
            case 'M':
                if(!tzHMZNPictureHint) {
                    if (allowDate) {
                        final int month = dt.getPart(AbstractDateTimeValue.MONTH);
                        formatNumber(specifier, picture, width, month, language, sb);
                    } else {
                        throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a month component");
                    }
                } else {
                    if (allowTime) {
                        final int minute = dt.getPart(AbstractDateTimeValue.MINUTE);
                        formatNumber(specifier, picture, width, minute, language, sb);
                    } else {
                        throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a minute component");
                    }
                }
                break;
            case 'D':
                if (allowDate) {
                    final int day = dt.getPart(AbstractDateTimeValue.DAY);
                    formatNumber(specifier, picture, width, day, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a day component");
                }
                break;
            case 'd':
                if (allowDate) {
                    final int dayInYear = dt.getDayWithinYear();
                    formatNumber(specifier, picture, width, dayInYear, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a day component");
                }
                break;
            case 'W':
                if (allowDate) {
                    final int week = dt.getWeekWithinYear();
                    formatNumber(specifier, picture, width, week, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a week component");
                }
                break;
            case 'w':
                if (allowDate) {
                    final int week = dt.getWeekWithinMonth();
                    formatNumber(specifier, picture, width, week, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a week component");
                }
                break;
            case 'F':
                if (allowDate) {
                    int day = dt.getDayOfWeek();

                    /**
                     * We convert from the 1 == Sunday base
                     * used by {@link AbstractDateTimeValue#getDayOfWeek()}
                     * to the 1 == Monday base expected
                     * by {@link #formatNumber(char, String, String, int, Optional, StringBuilder)}.
                     */
                    if (day == Calendar.SUNDAY) {
                        day = 7;
                    } else {
                        day--;
                    }

                    formatNumber(specifier, picture, width, day, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a day component");
                }
                break;
            case 'H':
                if (allowTime) {
                    final int hour = dt.getPart(AbstractDateTimeValue.HOUR);
                    formatNumber(specifier, picture, width, hour, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a hour component");
                }
                break;
            case 'h':
                if (allowTime) {
                    int hour = dt.getPart(AbstractDateTimeValue.HOUR) % 12;
                    if (hour == 0)
                        {hour = 12;}
                    formatNumber(specifier, picture, width, hour, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a hour component");
                }
                break;
            case 'm':
                if (allowTime) {
                    final int minute = dt.getPart(AbstractDateTimeValue.MINUTE);
                    formatNumber(specifier, picture, width, minute, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a minute component");
                }
                break;
            case 's':
                if (allowTime) {
                    final int second = dt.getPart(AbstractDateTimeValue.SECOND);
                    formatNumber(specifier, picture, width, second, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a second component");
                }
                break;
            case 'f':
                if (allowTime) {
                    final int fraction = dt.getPart(AbstractDateTimeValue.MILLISECOND);
                    formatFractionalSeconds(fraction, picture, width, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350,
                            "format-date does not support a fractional seconds component");
                }
                break;
            case 'P':
                if (allowTime) {
                    final int hour = dt.getPart(AbstractDateTimeValue.HOUR);
                    formatNumber(specifier, picture, width, hour, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350,
                            "format-date does not support an am/pm component");
                }
                break;
            case 'E':
                if (allowDate) {
                    final int year = dt.getPart(AbstractDateTimeValue.YEAR);
                    String era = year > 0 ? "AD" : "BC";
                    if ("n".equals(picture)) {
                        era = era.toLowerCase();
                    } else if ("Nn".equals(picture)) {
                        era = era.charAt(0) + era.substring(1).toLowerCase();
                    }
                    sb.append(era);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350,
                            "format-time does not support an era component");
                }
                break;
            case 'C':
                // Calendar name — we only support Gregorian
                sb.append("ISO");
                break;
            case 'z':
                if(dt.getTimezone() != Sequence.EMPTY_SEQUENCE) {
                    sb.append("GMT");
                }
            case 'Z':
                final Calendar cal = dt.toJavaObject(Calendar.class);

                final Sequence tz = dt.getTimezone();
                if(tz != Sequence.EMPTY_SEQUENCE) {
                    final DayTimeDurationValue dtv = ((DayTimeDurationValue)tz);

                    // Determine timezone sign from the total offset,
                    // since getPart(HOUR) loses the sign for -00:30 offsets
                    final double totalSeconds = dtv.getValue();
                    final boolean isNegative = totalSeconds < 0;
                    final int totalMinutes = (int) Math.abs(totalSeconds / 60);
                    final int absHour = totalMinutes / 60;
                    final int absMinute = totalMinutes % 60;

                    sb.append(formatTimeZone(picture,
                            absHour, absMinute, isNegative, cal.getTimeZone(), language, place));
                }
                break;

            default:
                throw new XPathException(this, ErrorCodes.FOFD1340, "Unrecognized date/time component: " + component);
        }
    }

    /**
     * Adjust a datetime value to the timezone applicable at the given IANA place.
     * Takes daylight savings time into account where possible (when the value includes a date).
     */
    private AbstractDateTimeValue adjustToPlaceTimezone(final AbstractDateTimeValue dt,
            final java.time.ZoneId placeZone) throws XPathException {
        // Convert the datetime to a java.time.Instant so we can query the zone rules
        final Calendar cal = dt.toJavaObject(Calendar.class);
        final java.time.Instant instant = cal.toInstant();

        // Get the applicable offset at this instant (accounts for DST)
        final java.time.ZoneOffset applicableOffset = placeZone.getRules().getOffset(instant);
        final int offsetMinutes = applicableOffset.getTotalSeconds() / 60;

        // Adjust using eXist's built-in timezone adjustment
        final DayTimeDurationValue offset = new DayTimeDurationValue(dt.getExpression(), offsetMinutes * 60000L);
        return dt.adjustedToTimezone(offset);
    }

    private String formatTimeZone(final String timezonePicture, final int absHour, final int absMinute,
            final boolean isNegative, final TimeZone timeZone, final String language,
            final Optional<String> place) {
        final Locale locale = new Locale(language);
        final String sign = isNegative ? "-" : "+";

        switch(timezonePicture) {
            case "0":
                if(absMinute != 0) {
                    return String.format(locale, "%s%d:%02d", sign, absHour, absMinute);
                } else {
                    return String.format(locale, "%s%d", sign, absHour);
                }

            case "0000":
                return String.format(locale, "%s%02d%02d", sign, absHour, absMinute);

            case "0:00":
                return String.format(locale, "%s%d:%02d", sign, absHour, absMinute);

            case "00:00t":
                if(absHour == 0 && absMinute == 0 && !isNegative) {
                    return "Z";
                }
                return String.format(locale, "%s%02d:%02d", sign, absHour, absMinute);

            case "N":
            case "n":
            case "Nn":
                final String tzName = formatTimeZoneName(isNegative ? -absHour : absHour, absMinute, locale, place);
                if ("n".equals(timezonePicture)) {
                    return tzName.toLowerCase(locale);
                } else if ("Nn".equals(timezonePicture)) {
                    if (tzName.length() <= 1) {
                        return tzName;
                    }
                    return tzName.substring(0, 1).toUpperCase(locale) + tzName.substring(1).toLowerCase(locale);
                }
                return tzName;

            case "Z":
                return formatMilitaryTimeZone(absHour, absMinute, isNegative);

            case "00:00":
            default:
                return String.format(locale, "%s%02d:%02d", sign, absHour, absMinute);
        }
    }

    private final static char[] MILITARY_TZ_CHARS = {'Z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L',
            'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y' };

    /**
     * Military time zone
     *
     * Z = +00:00, A = +01:00, B = +02:00, ..., M = +12:00, N = -01:00, O = -02:00, ... Y = -12:00.
     *
     * The letter J (meaning local time) is used in the case of a value that does not specify a timezone
     * offset.
     *
     * Timezone offsets that have no representation in this system (for example Indian Standard Time, +05:30)
     * are output as if the format 01:01 had been requested.
     */
    private String formatMilitaryTimeZone(final int absHour, final int absMinute,
            final boolean isNegative) {
        if(absMinute == 0 && absHour >= 0 && absHour <= 12) {
            if (!isNegative) {
                // +00 = Z, +01 = A, +02 = B, ..., +12 = M
                return String.valueOf(MILITARY_TZ_CHARS[absHour]);
            } else if (absHour > 0) {
                // -01 = N, -02 = O, ..., -12 = Y
                return String.valueOf(MILITARY_TZ_CHARS[12 + absHour]);
            } else {
                // -00:00 should not normally occur, but treat as Z
                return "Z";
            }
        } else {
            final String sign = isNegative ? "-" : "+";
            return String.format("%s%02d:%02d", sign, absHour, absMinute);
        }
    }

    private static final Pattern ALPHA_PATTERN = Pattern.compile("[A-Za-z]+");
    /** Well-known legacy timezone abbreviation IDs that java.time does not recognize. */
    private static final java.util.Set<String> KNOWN_TZ_ABBREVIATIONS = java.util.Set.of(
            "EST", "CST", "MST", "PST", "HST", "AST",    // North America
            "GMT", "UTC", "UCT", "WET", "CET", "EET",     // Europe/Universal
            "IST", "JST", "KST", "SST",                    // Asia/Pacific
            "CAT", "EAT", "WAT"                            // Africa
    );
    private static final java.time.format.DateTimeFormatter TZ_ABBREV_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("z");

    /**
     * Format a timezone as a name (e.g., "EST", "HST", "GMT") for the [ZN] modifier.
     *
     * If a valid IANA place is given, checks whether the offset matches that place's
     * standard or DST offset; if so, returns the corresponding display name.
     * Otherwise, does a generic lookup by offset milliseconds, preferring shorter
     * alphabetic abbreviations (e.g., "HST" over "HAST").
     * Falls back to numeric format (e.g., "+13:00") if no alphabetic name is found.
     */
    private String formatTimeZoneName(final int hour, final int minute,
            final Locale locale, final Optional<String> place) {
        final int offsetMs = (hour * 60 + (hour < 0 ? -minute : minute)) * 60 * 1000;

        // Try place-based lookup first (IANA timezone ID like "America/New_York")
        if (place.isPresent()) {
            try {
                final java.time.ZoneId placeZone = java.time.ZoneId.of(place.get());
                final java.time.zone.ZoneRules rules = placeZone.getRules();
                final int standardOffsetMs = rules.getStandardOffset(java.time.Instant.EPOCH).getTotalSeconds() * 1000;

                if (offsetMs == standardOffsetMs) {
                    // Standard time — format with a date in January
                    final java.time.ZonedDateTime stdTime =
                            java.time.ZonedDateTime.of(2020, 1, 15, 12, 0, 0, 0, placeZone);
                    final String name = stdTime.format(TZ_ABBREV_FORMATTER.withLocale(locale));
                    if (ALPHA_PATTERN.matcher(name).matches()) {
                        return name;
                    }
                } else if (!rules.isFixedOffset()) {
                    // Check if offset matches the DST offset for this place
                    final java.time.Instant summerInstant =
                            java.time.ZonedDateTime.of(2020, 7, 15, 12, 0, 0, 0, placeZone).toInstant();
                    final int dstOffsetMs = rules.getOffset(summerInstant).getTotalSeconds() * 1000;
                    if (offsetMs == dstOffsetMs && rules.isDaylightSavings(summerInstant)) {
                        final java.time.ZonedDateTime dstTime =
                                java.time.ZonedDateTime.of(2020, 7, 15, 12, 0, 0, 0, placeZone);
                        final String name = dstTime.format(TZ_ABBREV_FORMATTER.withLocale(locale));
                        if (ALPHA_PATTERN.matcher(name).matches()) {
                            return name;
                        }
                    }
                }
                // Offset doesn't match this place — fall through to generic lookup
            } catch (final java.time.DateTimeException e) {
                // Not a valid IANA timezone ID (e.g., country code "us") — fall through
            }
        }

        // Generic offset-based lookup: find the shortest alphabetic timezone abbreviation
        final String[] ids = TimeZone.getAvailableIDs(offsetMs);
        String bestName = null;
        String bestAbbrevId = null;
        for (final String id : ids) {
            try {
                final java.time.ZoneId zid = java.time.ZoneId.of(id);
                final java.time.ZonedDateTime zdt =
                        java.time.ZonedDateTime.of(2020, 1, 15, 12, 0, 0, 0, zid);
                final String name = zdt.format(TZ_ABBREV_FORMATTER.withLocale(locale));
                if (ALPHA_PATTERN.matcher(name).matches()) {
                    if (bestName == null || name.length() < bestName.length()) {
                        bestName = name;
                    }
                }
            } catch (final java.time.DateTimeException e) {
                // Legacy 3-letter abbreviation IDs (like "IST", "HST") are not valid
                // java.time ZoneIds, but the ID itself is a well-known timezone name
                if (KNOWN_TZ_ABBREVIATIONS.contains(id)
                        && (bestAbbrevId == null || id.length() < bestAbbrevId.length())) {
                    bestAbbrevId = id;
                }
            }
        }
        if (bestName != null) {
            return bestName;
        }
        if (bestAbbrevId != null) {
            return bestAbbrevId;
        }

        // Fallback to numeric format
        return String.format("%+03d:%02d", hour, minute);
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
        // Handle Roman numeral formatting
        if ("I".equals(picture) || "i".equals(picture)) {
            String roman = toRomanNumerals(num);
            if ("i".equals(picture)) {
                roman = roman.toLowerCase();
            }
            sb.append(roman);
            return;
        }

        final NumberFormatter formatter = NumberFormatter.getInstance(language);
        if ("N".equals(picture) || "n".equals(picture) || "Nn".equals(picture)) {
            String name = switch (specifier) {
                case 'M' -> formatter.getMonth(num);
                case 'F' -> formatter.getDay(num);
                case 'P' -> formatter.getAmPm(num);
                default -> "";
            };

            if ("N".equals(picture)) {
                name = name.toUpperCase();
            } else if ("n".equals(picture)) {
                name = name.toLowerCase();
            }

            final int[] widths = getWidths(width);
            if (widths != null) {
                final int min = widths[0];
                final int max = widths[1];
                final StringBuilder ws = new StringBuilder();
                while(name.length() < min) {
                    ws.append(" ");
                }
                name = name + ws;

                if(name.length() > max) {
                    name = name.substring(0, max);
                }
            }

            sb.append(name);
            return;
        }

        // determine min and max width
        int min = NumberFormatter.getMinDigits(picture);
        int max = NumberFormatter.getMaxDigits(picture);
        if (max == 1) {
            max = Integer.MAX_VALUE;
        }
        // explicit width takes precedence
        final int[] widths = getWidths(width);
        if (widths != null) {
            if (widths[0] > 0) {min = widths[0];}
            if (widths[1] > 0) {max = widths[1];}
        }
        try {
            sb.append(formatter.formatNumber(num, picture, min, max));
        } catch (final XPathException e) {
            throw new XPathException(this, ErrorCodes.FOFD1350, e.getMessage());
        }
    }

    /**
     * Format fractional seconds. Unlike regular numbers, fractional second digits
     * are significant from left to right (most significant first).
     *
     * The picture determines precision (number of fraction digits):
     *   [f1]         → max 1 digit: "4" for .456
     *   [f01]        → exactly 2 digits: "45" for .456
     *   [f001]       → exactly 3 digits: "456" for .456
     *   [f111,2-2]   → picture takes precedence: 3 digits "123"
     *
     * Width modifier controls precision only when no picture digits are specified:
     *   [f,2-2]      → exactly 2 digits: "45" for .456
     *   [f,1-*]      → all significant digits: "456" for .456
     */
    private void formatFractionalSeconds(int millis, String picture, String width,
                                          StringBuilder sb) throws XPathException {
        // Build fraction digits string: at least 3 digits (millisecond precision)
        final String fractionDigits = String.format("%03d", millis);

        // Determine min and max from picture
        int picMin = NumberFormatter.getMinDigits(picture);
        int picMax = NumberFormatter.getMaxDigits(picture);

        int min;
        int max;

        if (picMax > 0) {
            // Picture specifies precision — use it
            min = picMin;
            max = picMax;
        } else {
            // No picture digits — use width modifier or defaults
            min = 1;
            max = Integer.MAX_VALUE;
        }

        // Width modifier overrides ONLY when picture doesn't specify digits
        final int[] widths = getWidths(width);
        if (widths != null) {
            if (widths[0] > 0 && picMax == 0) { min = widths[0]; }
            if (widths[1] > 0 && picMax == 0) { max = widths[1]; }
            // When picture has digits, width min still applies for padding
            if (widths[0] > 0 && picMax > 0 && widths[0] > min) { min = widths[0]; }
        }

        // Build result: start with full fraction digits, extend if needed
        final StringBuilder result = new StringBuilder(fractionDigits);
        while (result.length() < min) {
            result.append('0');
        }

        // Apply max: truncate from right (preserving most significant digits)
        if (max < Integer.MAX_VALUE && result.length() > max) {
            result.setLength(max);
        } else if (max == Integer.MAX_VALUE) {
            // Trim trailing zeros but keep at least min digits
            while (result.length() > min && result.charAt(result.length() - 1) == '0') {
                result.setLength(result.length() - 1);
            }
        }

        sb.append(result);
    }

    private int[] getWidths(String width) throws XPathException {
        if (width == null || width.isEmpty())
            {return null;}

        int min = -1;
        int max = -1;
        String minPart = width;
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

            }
        }
        if (maxPart != null) {
            if ("*".equals(maxPart))
                {max = Integer.MAX_VALUE;}
            else {
                try {
                    max = Integer.parseInt(maxPart);
                } catch (final NumberFormatException e) {
                }
            }
        }
        if (max != -1 && min > max)
            {throw new XPathException(this, ErrorCodes.FOFD1340,"Minimum width > maximum width in component");}
        return new int[] { min, max };
    }

    private static final java.util.Set<String> KNOWN_CALENDARS = java.util.Set.of(
            "AD", "ISO", "OS", "NS", "CE", "CB", "AH", "AM", "AP", "AE", "JE", "HE", "ME", "SE",
            "SH", "SS", "BS", "BE", "KO", "TH", "JP");

    private void validateCalendar(String calendar) throws XPathException {
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
                // Q{}name — treated as no-namespace, must be a known calendar
                if (!KNOWN_CALENDARS.contains(local.toUpperCase())) {
                    throw new XPathException(this, ErrorCodes.FOFD1340,
                            "Unknown calendar: " + calendar);
                }
            }
            // Calendar in a non-empty namespace — accept and use Gregorian fallback
            return;
        }
        // Bare name — must be a valid NCName and a known calendar code
        if (calendar.isEmpty() || !Character.isLetter(calendar.charAt(0)) || calendar.contains(":")) {
            throw new XPathException(this, ErrorCodes.FOFD1340,
                    "Badly-formed calendar name: " + calendar);
        }
        if (!KNOWN_CALENDARS.contains(calendar.toUpperCase())) {
            throw new XPathException(this, ErrorCodes.FOFD1340,
                    "Unknown calendar: " + calendar);
        }
    }

    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private static String toRomanNumerals(int num) {
        if (num <= 0) {
            return String.valueOf(num);
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (num >= ROMAN_VALUES[i]) {
                sb.append(ROMAN_SYMBOLS[i]);
                num -= ROMAN_VALUES[i];
            }
        }
        return sb.toString();
    }
}