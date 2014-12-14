/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.util.NumberFormatter;
import org.exist.xquery.value.*;

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
			Type.STRING, Cardinality.EXACTLY_ONE, "The formatted date");

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("format-dateTime", Function.BUILTIN_FUNCTION_NS),
                    "Returns a string containing an xs:date value formatted for display.",
                    new SequenceType[] {
                		DATETIME,
                        PICTURE
                    },
                    RETURN
            ),
            new FunctionSignature(
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
            ),
            new FunctionSignature(
                    new QName("format-date", Function.BUILTIN_FUNCTION_NS),
                    "Returns a string containing an xs:date value formatted for display.",
                    new SequenceType[] {
                    	DATE,
                        PICTURE
                    },
                    RETURN
            ),
            new FunctionSignature(
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
            ),
            new FunctionSignature(
                    new QName("format-time", Function.BUILTIN_FUNCTION_NS),
                    "Returns a string containing an xs:time value formatted for display.",
                    new SequenceType[] {
                        TIME,
                        PICTURE
                    },
                    RETURN
            ),
            new FunctionSignature(
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
            )
    };

    private static Pattern componentPattern = Pattern.compile("([YMDdWwFHhmsfZzPCE])\\s*(.*)");

    public FnFormatDates(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty())
            {return Sequence.EMPTY_SEQUENCE;}

        final AbstractDateTimeValue value = (AbstractDateTimeValue) args[0].itemAt(0);
        final String picture = args[1].getStringValue();
        String language = "en";
        if (getArgumentCount() == 5) {
            if (args[2].hasOne())
                {language = args[2].getStringValue();}
        }

        return new StringValue(formatDate(picture, value, language));
    }

    private String formatDate(String pic, AbstractDateTimeValue dt, String language) throws XPathException {
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
                final int close = (i < pic.length() ? pic.indexOf("]", i) : -1);
                if (close == -1) {
                    throw new XPathException(this, ErrorCodes.FOFD1340, "Date format contains a '[' with no matching ']'");
                }
                final String component = pic.substring(i, close);
                formatComponent(component, dt, language, sb);
                i = close + 1;
            }
        }
        return sb.toString();
    }

    private void formatComponent(String component, AbstractDateTimeValue dt, String language, StringBuilder sb) throws XPathException {
        final Matcher matcher = componentPattern.matcher(component);
        if (!matcher.matches())
            {throw new XPathException(this, ErrorCodes.FOFD1340, "Unrecognized date/time component: " + component);}

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
        if (picture == null || picture.length() == 0) {
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
                if (allowDate) {
                    final int month = dt.getPart(AbstractDateTimeValue.MONTH);
                    formatNumber(specifier, picture, width, month, language, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a month component");
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
                    final int day = dt.getDayOfWeek();
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
                    formatNumber(specifier, picture, width, fraction, language, sb);
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
                final Sequence tz = dt.getTimezone();
                if(tz != Sequence.EMPTY_SEQUENCE) {
                    final DayTimeDurationValue dtv = ((DayTimeDurationValue)tz);
                    final NumberFormatter formatter = NumberFormatter.getInstance(language);
                    sb.append(dtv.getPart(DurationValue.SIGN) >= 0 ? '+' : '-');
                    sb.append(formatter.formatNumber(dtv.getPart(DurationValue.HOUR), "01", 2, 2));
                    sb.append(':');
                    sb.append(formatter.formatNumber(dtv.getPart(DurationValue.MINUTE), "01", 2, 2));
                }
                break;

            default:
                throw new XPathException(this, ErrorCodes.FOFD1340, "Unrecognized date/time component: " + component);
        }
    }

    private String getDefaultFormat(char specifier) {
        switch (specifier) {
            case 'F':
                return "Nn";
            case 'P':
                return "n";
            case 'C':
            case 'E':
                return "N";
            case 'm':
            case 's':
                return "01";
            default:
                return "1";
        }
    }

    private void formatNumber(char specifier, String picture, String width, int num, String language,
                              StringBuilder sb) throws XPathException {
        final NumberFormatter formatter = NumberFormatter.getInstance(language);
        if ("N".equals(picture) || "n".equals(picture) || "Nn".equals(picture)) {
            String name;
            switch (specifier) {
                case 'M':
                    name = formatter.getMonth(num);
                    break;
                case 'F':
                    name = formatter.getDay(num);
                    break;
                case 'P':
                    name = formatter.getAmPm(num);
                    break;
                default:
                    name = "";
                    break;
            }

            if ("N".equals(picture)) {
                name = name.toUpperCase();
            } else if ("n".equals(picture)) {
                name = name.toLowerCase();
            }

            final int widths[] = getWidths(width);
            if (widths != null) {
                final int min = widths[0];
                final int max = widths[1];
                while(name.length() < min) {
                    name = name + " ";
                }

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
        final int widths[] = getWidths(width);
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

    private int[] getWidths(String width) throws XPathException {
        if (width == null || width.length() == 0)
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