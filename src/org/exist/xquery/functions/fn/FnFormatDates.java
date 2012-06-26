package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.util.NumberFormatter;
import org.exist.xquery.util.NumberFormatter_en;
import org.exist.xquery.value.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FnFormatDates extends BasicFunction {

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("format-dateTime", Function.BUILTIN_FUNCTION_NS),
                    "Returns a string containing an xs:date value formatted for display.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.DATE_TIME,
                                    Cardinality.ZERO_OR_ONE, "The date"),
                            new FunctionParameterSequenceType("picture", Type.STRING,
                                    Cardinality.EXACTLY_ONE, "The picture string")
                    },
                    new FunctionReturnSequenceType(Type.STRING,
                            Cardinality.EXACTLY_ONE, "The formatted date")
            ),
            new FunctionSignature(
                    new QName("format-date", Function.BUILTIN_FUNCTION_NS),
                    "Returns a string containing an xs:date value formatted for display.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.DATE,
                                    Cardinality.ZERO_OR_ONE, "The date"),
                            new FunctionParameterSequenceType("picture", Type.STRING,
                                    Cardinality.EXACTLY_ONE, "The picture string")
                    },
                    new FunctionReturnSequenceType(Type.STRING,
                            Cardinality.EXACTLY_ONE, "The formatted date")
            ),
            new FunctionSignature(
                    new QName("format-time", Function.BUILTIN_FUNCTION_NS),
                    "Returns a string containing an xs:time value formatted for display.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.TIME,
                                    Cardinality.ZERO_OR_ONE, "The date"),
                            new FunctionParameterSequenceType("picture", Type.STRING,
                                    Cardinality.EXACTLY_ONE, "The picture string")
                    },
                    new FunctionReturnSequenceType(Type.STRING,
                            Cardinality.EXACTLY_ONE, "The formatted date")
            )
    };

    private static Pattern componentPattern = Pattern.compile("([YMDdWwFHhmsfZzPCE])\\s*(.*)");

    public FnFormatDates(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;

        AbstractDateTimeValue value = (AbstractDateTimeValue) args[0].itemAt(0);
        String picture = args[1].getStringValue();

        String formatted = formatDate(picture, value);
        return new StringValue(formatted);
    }

    private String formatDate(String pic, AbstractDateTimeValue dt) throws XPathException {
        StringBuilder sb = new StringBuilder();
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
                int close = (i < pic.length() ? pic.indexOf("]", i) : -1);
                if (close == -1) {
                    throw new XPathException(this, ErrorCodes.FOFD1340, "Date format contains a '[' with no matching ']'");
                }
                String component = pic.substring(i, close);
                formatComponent(component, dt, sb);
                i = close + 1;
            }
        }
        return sb.toString();
    }

    private void formatComponent(String component, AbstractDateTimeValue dt, StringBuilder sb) throws XPathException {
        Matcher matcher = componentPattern.matcher(component);
        if (!matcher.matches())
            throw new XPathException(this, ErrorCodes.FOFD1340, "Unrecognized date/time component: " + component);

        char specifier = component.charAt(0);
        String width = null;
        String picture = matcher.group(2);
        // check if there's an optional width specifier
        int widthSep = picture.indexOf(',');
        if (-1 < widthSep) {
            width = picture.substring(widthSep + 1);
            picture = picture.substring(0, widthSep);
        }
        // get default format picture if none was specified
        if (picture == null || picture.length() == 0) {
            picture = getDefaultFormat(specifier);
        }
        boolean allowDate = !Type.subTypeOf(dt.getType(), Type.TIME);
        boolean allowTime = !Type.subTypeOf(dt.getType(), Type.DATE);
        switch (specifier) {
            case 'Y':
                if (allowDate) {
                    int year = dt.getPart(AbstractDateTimeValue.YEAR);
                    formatNumber(specifier, picture, width, year, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a year component");
                }
                break;
            case 'M':
                if (allowDate) {
                    int month = dt.getPart(AbstractDateTimeValue.MONTH);
                    formatNumber(specifier, picture, width, month, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a month component");
                }
                break;
            case 'D':
                if (allowDate) {
                    int day = dt.getPart(AbstractDateTimeValue.DAY);
                    formatNumber(specifier, picture, width, day, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a day component");
                }
                break;
            case 'd':
                if (allowDate) {
                    int dayInYear = dt.getDayWithinYear();
                    formatNumber(specifier, picture, width, dayInYear, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a day component");
                }
                break;
            case 'W':
                if (allowDate) {
                    int week = dt.getWeekWithinYear();
                    formatNumber(specifier, picture, width, week, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a week component");
                }
                break;
            case 'w':
                if (allowDate) {
                    int week = dt.getWeekWithinMonth();
                    formatNumber(specifier, picture, width, week, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a week component");
                }
                break;
            case 'F':
                if (allowDate) {
                    int day = dt.getDayOfWeek();
                    formatNumber(specifier, picture, width, day, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-time does not support a day component");
                }
                break;
            case 'H':
                if (allowTime) {
                    int hour = dt.getPart(AbstractDateTimeValue.HOUR);
                    formatNumber(specifier, picture, width, hour, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a hour component");
                }
                break;
            case 'h':
                if (allowTime) {
                    int hour = dt.getPart(AbstractDateTimeValue.HOUR) % 12;
                    if (hour == 0)
                        hour = 12;
                    formatNumber(specifier, picture, width, hour, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a hour component");
                }
                break;
            case 'm':
                if (allowTime) {
                    int minute = dt.getPart(AbstractDateTimeValue.MINUTE);
                    formatNumber(specifier, picture, width, minute, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a minute component");
                }
                break;
            case 's':
                if (allowTime) {
                    int second = dt.getPart(AbstractDateTimeValue.SECOND);
                    formatNumber(specifier, picture, width, second, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350, "format-date does not support a second component");
                }
                break;
            case 'f':
                if (allowTime) {
                    int fraction = dt.getPart(AbstractDateTimeValue.MILLISECOND);
                    formatNumber(specifier, picture, width, fraction, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350,
                            "format-date does not support a fractional seconds component");
                }
                break;
            case 'P':
                if (allowTime) {
                    int hour = dt.getPart(AbstractDateTimeValue.HOUR);
                    formatNumber(specifier, picture, width, hour, sb);
                } else {
                    throw new XPathException(this, ErrorCodes.FOFD1350,
                            "format-date does not support an am/pm component");
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

    private void formatNumber(char specifier, String picture, String width, int num, StringBuilder sb) throws XPathException {
        NumberFormatter formatter = new NumberFormatter_en();
        if (picture.equals("N") || picture.equals("n") || picture.equals("Nn")) {
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
            if (picture.equals("N"))
                name = name.toUpperCase();
            if (picture.equals("n"))
                name = name.toLowerCase();
            sb.append(name);
            return;
        }
        // determine min and max width
        int min = NumberFormatter.getMinDigits(picture);
        int max = NumberFormatter.getMaxDigits(picture);
        if (max == 1)
            max = Integer.MAX_VALUE;
        // explicit width takes precedence
        int widths[] = getWidths(width);
        if (widths != null) {
            if (widths[0] > 0) min = widths[0];
            if (widths[1] > 0) max = widths[1];
        }
        try {
            sb.append(formatter.formatNumber(num, picture, min, max));
        } catch (XPathException e) {
            throw new XPathException(this, ErrorCodes.FOFD1350, e.getMessage());
        }
    }

    private int[] getWidths(String width) throws XPathException {
        if (width == null || width.length() == 0)
            return null;

        int min = -1;
        int max = -1;
        String minPart = width;
        String maxPart = null;
        int p = width.indexOf('-');
        if (p < 0) {
            minPart = width;
        } else {
            minPart = width.substring(0, p);
            maxPart = width.substring(p + 1);
        }
        if ("*".equals(minPart))
            min = 1;
        else {
            try {
                min = Integer.parseInt(minPart);
            } catch (NumberFormatException e) {

            }
        }
        if (maxPart != null) {
            if ("*".equals(maxPart))
                max = Integer.MAX_VALUE;
            else {
                try {
                    max = Integer.parseInt(maxPart);
                } catch (NumberFormatException e) {
                }
            }
        }
        if (max != -1 && min > max)
            throw new XPathException(this, ErrorCodes.FOFD1350,"Minimum width > maximum width in component");
        return new int[] { min, max };
    }
}