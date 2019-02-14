package org.exist.xquery.util;

import java.util.Locale;

/**
 * German language formatting of numbers and dates.
 *
 * @author Wolfgang
 */
public class NumberFormatter_fr extends NumberFormatter {

    public NumberFormatter_fr(Locale locale) {
        super(locale);
    }

    @Override
    public String getOrdinalSuffix(long number) {
        if (number == 1) {
            return "er";
        }
        return "";
    }
}
