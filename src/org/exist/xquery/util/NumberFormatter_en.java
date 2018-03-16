package org.exist.xquery.util;

import java.util.Locale;

/**
 * English formatter for numbers and dates.
 *
 * @author Wolfgang
 */
public class NumberFormatter_en extends NumberFormatter {

    public NumberFormatter_en(Locale locale) {
        super(locale);
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