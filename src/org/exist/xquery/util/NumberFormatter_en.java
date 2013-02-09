package org.exist.xquery.util;

/**
 * English formatter for numbers and dates.
 *
 * @author Wolfgang
 */
public class NumberFormatter_en extends NumberFormatter {

    public static String[] DAYS = { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };

    public static String[] MONTHS = { "January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December" };

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
        if (hour > 12)
            {return "pm";}
        else
            {return "am";}
    }

    public String getOrdinalSuffix(long number) {
        if (number > 10 && number < 20)
            {return "th";}
        final long mod = number % 10;
        if (mod == 1)
            {return "st";}
        else if (mod == 2)
            {return "nd";}
        else if (mod == 3)
            {return "rd";}
        else
            {return "th";}
    }
}