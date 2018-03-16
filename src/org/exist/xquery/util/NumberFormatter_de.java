package org.exist.xquery.util;

import java.util.Locale;

/**
 * German language formatting of numbers and dates.
 *
 * @author Wolfgang
 */
public class NumberFormatter_de extends NumberFormatter {

    public NumberFormatter_de(Locale locale) {
        super(locale);
    }

    @Override
    public String getOrdinalSuffix(long number) {
        return ".";
    }
}
