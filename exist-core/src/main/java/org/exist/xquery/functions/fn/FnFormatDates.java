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

import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import org.apache.commons.lang3.StringUtils;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.util.NumberFormatter;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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

    private static final Pattern componentPattern = Pattern.compile("([YMDdWwFHhmsfZzPCE])\\s*(.*)");

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
        String calendar = null;
        if (getArgumentCount() == 5) {
            if (args[2].hasOne()) {
                language = args[2].getStringValue();
            } else {
                language = context.getDefaultLanguage();
            }

            if (args[3].hasOne()) {
                calendar = args[3].getStringValue();
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

        // Validate calendar parameter
        if (calendar != null) {
            if (calendar.startsWith(":")) {
                throw new XPathException(this, ErrorCodes.FOFD1340,
                        "Invalid calendar name: " + calendar);
            }
            if (calendar.startsWith("Q{}")) {
                final String localPart = calendar.substring(3);
                if (localPart.isEmpty() || !Character.isLetter(localPart.charAt(0))) {
                    throw new XPathException(this, ErrorCodes.FOFD1340,
                            "Invalid calendar name: " + calendar);
                }
                if (!isKnownCalendar(localPart)) {
                    throw new XPathException(this, ErrorCodes.FOFD1340,
                            "Unknown calendar: " + calendar);
                }
            } else if (calendar.startsWith("Q{") && calendar.contains("}")) {
                // EQName with non-empty namespace: accept with fallback
            } else if (calendar.contains(":")) {
                // Prefixed QName: accept with fallback
            } else if (!isKnownCalendar(calendar)) {
                throw new XPathException(this, ErrorCodes.FOFD1340,
                        "Unknown calendar: " + calendar);
            }
        }

        return new StringValue(this, formatDate(picture, value, language, place));
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
        // Per spec, whitespace within a variable marker is insignificant
        component = component.replaceAll("\\s+", "");
        final Matcher matcher = componentPattern.matcher(component);
        if (!matcher.matches()) {
            throw new XPathException(this, ErrorCodes.FOFD1340, "Unrecognized date/time component: " + component);
        }

        final char specifier = component.charAt(0);
        String width = null;
        String picture = matcher.group(2);
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
                    final int millis = dt.getPart(AbstractDateTimeValue.MILLISECOND);
                    formatFractionalSeconds(millis, picture, width, sb);
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
            case 'z':
                if(dt.getTimezone() != Sequence.EMPTY_SEQUENCE) {
                    sb.append("GMT");
                }
            case 'Z':
                final Calendar cal = dt.toJavaObject(Calendar.class);

                final Sequence tz = dt.getTimezone();
                if(tz != Sequence.EMPTY_SEQUENCE) {
                    final DayTimeDurationValue dtv = ((DayTimeDurationValue)tz);

                    //cope with eXist's duration class's weird #getPart method
                    int minute = dtv.getPart(DurationValue.MINUTE);
                    if(minute < 0) {
                        minute = minute * -1;
                    }

                    sb.append(formatTimeZone(picture,
                            dtv.getPart(DurationValue.HOUR), minute, cal.getTimeZone(), language, place));
                } else if ("Z".equals(picture)) {
                    // Military timezone: J = local time (no timezone specified)
                    sb.append("J");
                }
                break;

            case 'E':
                if (allowDate) {
                    final int year = dt.getPart(AbstractDateTimeValue.YEAR);
                    sb.append(year >= 0 ? "AD" : "BC");
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350,
                            "format-time does not support an era component");
                }
                break;
            case 'C':
                sb.append("AD");
                break;
            default:
                throw new XPathException(this, ErrorCodes.FOFD1340, "Unrecognized date/time component: " + component);
        }
    }

    private String formatTimeZone(String timezonePicture, final int hour, final int minute,
            final TimeZone timeZone, final String language, final Optional<String> place) {
        // Military timezone letter
        if ("Z".equals(timezonePicture)) {
            return formatMilitaryTimeZone(hour, minute);
        }

        // Named timezone
        if ("N".equals(timezonePicture)) {
            final Locale locale = new Locale(language);
            final TimeZone tz = place.map(TimeZone::getTimeZone).orElse(timeZone);
            return tz.getDisplayName(timeZone.useDaylightTime(), TimeZone.SHORT, locale);
        }

        // Check for 't' modifier (use "Z" for UTC)
        final boolean useZForUTC = timezonePicture.endsWith("t");
        if (useZForUTC) {
            timezonePicture = timezonePicture.substring(0, timezonePicture.length() - 1);
        }
        if (useZForUTC && hour == 0 && minute == 0) {
            return "Z";
        }

        // Parse the picture: find digit family, separator, hour/minute digit counts
        int zero = '0';
        boolean zeroFound = false;
        int hourDigits = 0;
        int minuteDigits = 0;
        String separator = null;

        for (int i = 0; i < timezonePicture.length(); i++) {
            final int ch = timezonePicture.codePointAt(i);
            final int family = net.sf.saxon.expr.number.Alphanumeric.getDigitFamily(ch);
            if (family >= 0) {
                if (!zeroFound) { zero = family; zeroFound = true; }
                if (separator == null) { hourDigits++; } else { minuteDigits++; }
            } else if (ch == '#') {
                if (separator == null) { hourDigits++; } else { minuteDigits++; }
            } else if (separator == null && hourDigits > 0) {
                separator = new String(Character.toChars(ch));
            }
            if (Character.isSupplementaryCodePoint(ch)) { i++; }
        }

        final int absHour = Math.abs(hour);
        final String sign = (hour < 0) ? "-" : "+";
        final StringBuilder result = new StringBuilder(sign);

        if (separator != null && minuteDigits > 0) {
            result.append(padWithDigitFamily(absHour, hourDigits, zero));
            result.append(separator);
            result.append(padWithDigitFamily(minute, minuteDigits, zero));
        } else if (hourDigits >= 3) {
            result.append(padWithDigitFamily(absHour * 100 + minute, hourDigits, zero));
        } else {
            result.append(padWithDigitFamily(absHour, hourDigits, zero));
            if (minute != 0) {
                result.append(":");
                result.append(padWithDigitFamily(minute, 2, zero));
            }
        }

        return result.toString();
    }

    private static String padWithDigitFamily(int value, int minDigits, int zero) {
        String s = Integer.toString(value);
        while (s.length() < minDigits) { s = "0" + s; }
        if (zero != '0') {
            final StringBuilder converted = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                final char ch = s.charAt(i);
                if (ch >= '0' && ch <= '9') {
                    converted.appendCodePoint(zero + (ch - '0'));
                } else {
                    converted.append(ch);
                }
            }
            return converted.toString();
        }
        return s;
    }

    // Military timezone: Z(0), A-I(+1 to +9), K-M(+10 to +12), N-Y(-1 to -12)
    // J is reserved for local time (no timezone) and is NOT in this array
    private final static char[] MILITARY_TZ_CHARS = {'Z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'K', 'L',
            'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y' };

    private String formatMilitaryTimeZone(final int hour, final int minute) {
        if (minute == 0 && hour >= -12 && hour <= 12) {
            final int offset = (hour < 0) ? 12 + (hour * -1) : hour;
            return String.valueOf(MILITARY_TZ_CHARS[offset]);
        } else {
            return String.format("%+03d:%02d", hour, minute);
        }
    }

    /**
     * Format fractional seconds as left-aligned digits.
     * Unlike regular integer formatting, fractional seconds treat the value
     * as a fraction (0.456) where digits are extracted left-to-right.
     */
    private void formatFractionalSeconds(int millis, String picture, String width,
            StringBuilder sb) throws XPathException {
        // Build the fractional digit string, left-aligned, padded to 3 digits
        String fracDigits = String.format("%03d", millis);

        // Count actual digit positions in picture (ignoring separators and modifiers)
        int picMin = 0;
        int picMax = 0;
        for (int i = 0; i < picture.length(); i++) {
            final char ch = picture.charAt(i);
            if ((ch == 'o' || ch == 'c') && i == picture.length() - 1) { break; }
            final int family = net.sf.saxon.expr.number.Alphanumeric.getDigitFamily(ch);
            if (family >= 0) {
                picMin++;
                picMax++;
            } else if (ch == '#') {
                picMax++;
            }
        }

        int min = picMin;
        // A multi-digit picture constrains max precision; single-digit is unbounded
        final boolean pictureSetsMax = (picMax > 1);
        int max = pictureSetsMax ? picMax : Integer.MAX_VALUE;

        // Width specifier
        final int[] widths = getWidths(width);
        if (widths != null) {
            if (widths[0] > 0) { min = Math.max(picMin, widths[0]); }
            if (widths[1] > 0) {
                if (pictureSetsMax) {
                    max = Math.max(picMax, widths[1]);
                } else {
                    max = widths[1];
                }
            }
        }
        if (max < min) { max = min; }

        // Pad to min with trailing zeros
        while (fracDigits.length() < min) {
            fracDigits += "0";
        }

        // Truncate to max precision
        if (fracDigits.length() > max) {
            fracDigits = fracDigits.substring(0, max);
        }

        // Remove trailing zeros beyond min (variable-width output)
        while (fracDigits.length() > min && fracDigits.endsWith("0")) {
            fracDigits = fracDigits.substring(0, fracDigits.length() - 1);
        }

        // Apply digit family from picture (e.g., Arabic-Indic digits)
        final int digitSign = getFirstDigitInPicture(picture);
        if (digitSign >= 0) {
            final int zero = net.sf.saxon.expr.number.Alphanumeric.getDigitFamily(digitSign);
            if (zero != '0') {
                final StringBuilder converted = new StringBuilder();
                for (int i = 0; i < fracDigits.length(); i++) {
                    final char ch = fracDigits.charAt(i);
                    if (ch >= '0' && ch <= '9') {
                        converted.append((char)(zero + (ch - '0')));
                    } else {
                        converted.append(ch);
                    }
                }
                fracDigits = converted.toString();
            }
        }

        // Insert grouping separators from picture if present
        if (hasGroupingSeparators(picture)) {
            fracDigits = applyGroupingSeparators(fracDigits, picture);
        }

        sb.append(fracDigits);
    }

    private static int getFirstDigitInPicture(String picture) {
        for (int i = 0; i < picture.length(); i++) {
            final char ch = picture.charAt(i);
            if (ch != '#' && ch != 'o' && ch != 'c') {
                final int family = net.sf.saxon.expr.number.Alphanumeric.getDigitFamily(ch);
                if (family >= 0) {
                    return ch;
                }
            }
        }
        return -1;
    }

    private static boolean hasGroupingSeparators(String picture) {
        for (int i = 0; i < picture.length(); i++) {
            final char ch = picture.charAt(i);
            if ((ch == 'o' || ch == 'c') && i == picture.length() - 1) { break; }
            final int family = net.sf.saxon.expr.number.Alphanumeric.getDigitFamily(ch);
            if (family < 0 && ch != '#') {
                return true;
            }
        }
        return false;
    }

    private static String applyGroupingSeparators(String digits, String picture) {
        final StringBuilder result = new StringBuilder();
        int digitIdx = 0;
        for (int i = 0; i < picture.length() && digitIdx < digits.length(); i++) {
            final char ch = picture.charAt(i);
            if ((ch == 'o' || ch == 'c') && i == picture.length() - 1) { break; }
            final int family = net.sf.saxon.expr.number.Alphanumeric.getDigitFamily(ch);
            if (family >= 0 || ch == '#') {
                result.append(digits.charAt(digitIdx));
                digitIdx++;
            } else {
                result.append(ch);
            }
        }
        while (digitIdx < digits.length()) {
            result.append(digits.charAt(digitIdx));
            digitIdx++;
        }
        return result.toString();
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
                    // Truncate to min for recognizable abbreviation
                    name = name.substring(0, Math.max(min, 1));
                }
            }

            sb.append(name);
            return;
        }

        // Word formatting: W (uppercase), w (lowercase), Ww (title case)
        // With optional ordinal modifier: Wo, wo, Wwo
        final String basePicture = picture.endsWith("o") ? picture.substring(0, picture.length() - 1) : picture;
        final boolean ordinalWords = picture.endsWith("o") && (basePicture.equals("W") || basePicture.equals("w") || basePicture.equals("Ww"));
        if ("W".equals(basePicture) || "w".equals(basePicture) || "Ww".equals(basePicture)) {
            final Locale locale = new Locale(language);
            final String spelloutRule = ordinalWords ? "%spellout-ordinal" : "%spellout-cardinal";

            // Check if the rule exists, fall back to cardinal if ordinal not available
            final RuleBasedNumberFormat rbnf = new RuleBasedNumberFormat(locale, RuleBasedNumberFormat.SPELLOUT);
            String ruleToUse = spelloutRule;
            boolean ruleFound = false;
            for (final String ruleName : rbnf.getRuleSetNames()) {
                if (ruleName.equals(ruleToUse)) {
                    ruleFound = true;
                    break;
                }
            }
            if (!ruleFound) {
                ruleToUse = "%spellout-cardinal";
            }

            final MessageFormat fmt = new MessageFormat("{0,spellout," + ruleToUse + "}", locale);
            String word = fmt.format(new Object[]{num});

            if ("W".equals(basePicture)) {
                word = word.toUpperCase(locale);
            } else if ("Ww".equals(basePicture)) {
                // Title case: capitalize each word
                final String[] parts = word.split("((?<=[ -])|(?=[ -]))");
                final StringBuilder titled = new StringBuilder();
                for (final String part : parts) {
                    titled.append(StringUtils.capitalize(part));
                }
                word = titled.toString();
            }
            // "w" is already lowercase from ICU4J

            sb.append(word);
            return;
        }

        // Roman numeral formatting: I (uppercase), i (lowercase)
        if ("I".equals(picture) || "i".equals(picture)) {
            String roman = toRoman(Math.abs(num));
            if ("i".equals(picture)) {
                roman = roman.toLowerCase();
            }
            sb.append(roman);
            return;
        }

        // Handle grouping separators in numeric pictures (e.g., [Y9;999], [Y9,999,*])
        if (hasGroupingSeparators(picture)) {
            sb.append(formatWithGroupingSeparators(num, picture));
            return;
        }

        // Validate optional digit placement: # must precede mandatory digits, not follow
        boolean seenMandatory = false;
        for (int i = 0; i < picture.length(); i++) {
            final char ch = picture.charAt(i);
            if ((ch == 'o' || ch == 'c') && i == picture.length() - 1) { break; }
            if (ch == '#') {
                if (seenMandatory) {
                    throw new XPathException(this, ErrorCodes.FOFD1340,
                            "Optional digit '#' must not appear after mandatory digits in: " + picture);
                }
            } else {
                final int family = net.sf.saxon.expr.number.Alphanumeric.getDigitFamily(ch);
                if (family >= 0) { seenMandatory = true; }
            }
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

    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private static String toRoman(int num) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (num >= ROMAN_VALUES[i]) {
                sb.append(ROMAN_SYMBOLS[i]);
                num -= ROMAN_VALUES[i];
            }
        }
        return sb.toString();
    }

    private static String formatWithGroupingSeparators(int num, String picture) {
        String pic = picture;
        if (pic.endsWith("o") || pic.endsWith("c")) { pic = pic.substring(0, pic.length() - 1); }
        if (pic.endsWith(",*")) { pic = pic.substring(0, pic.length() - 2); }

        int zero = '0';
        for (int i = 0; i < pic.length(); i++) {
            final int family = net.sf.saxon.expr.number.Alphanumeric.getDigitFamily(pic.charAt(i));
            if (family >= 0) { zero = family; break; }
        }

        // Map separator positions (counted from the right)
        final List<Integer> sepPositions = new ArrayList<>();
        final List<Character> sepChars = new ArrayList<>();
        int digitCount = 0;
        for (int i = pic.length() - 1; i >= 0; i--) {
            final char ch = pic.charAt(i);
            final int family = net.sf.saxon.expr.number.Alphanumeric.getDigitFamily(ch);
            if (family >= 0 || ch == '#') {
                digitCount++;
            } else {
                sepPositions.add(digitCount);
                sepChars.add(ch);
            }
        }

        final String digits = Integer.toString(num);
        final StringBuilder result = new StringBuilder();
        int digitIdx = digits.length() - 1;
        int pos = 0;
        while (digitIdx >= 0) {
            for (int s = 0; s < sepPositions.size(); s++) {
                if (sepPositions.get(s) == pos && pos > 0) {
                    result.insert(0, sepChars.get(s));
                }
            }
            result.insert(0, digits.charAt(digitIdx));
            digitIdx--;
            pos++;
        }

        if (zero != '0') {
            final StringBuilder converted = new StringBuilder();
            for (int i = 0; i < result.length(); i++) {
                final char ch = result.charAt(i);
                if (ch >= '0' && ch <= '9') {
                    converted.append((char)(zero + (ch - '0')));
                } else {
                    converted.append(ch);
                }
            }
            return converted.toString();
        }
        return result.toString();
    }

    private static boolean isKnownCalendar(final String calendar) {
        return switch (calendar.toUpperCase()) {
            case "AD", "ISO", "OS", "NS" -> true;
            default -> false;
        };
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
            {throw new XPathException(this, ErrorCodes.FOFD1350,"Minimum width > maximum width in component");}
        return new int[] { min, max };
    }
}