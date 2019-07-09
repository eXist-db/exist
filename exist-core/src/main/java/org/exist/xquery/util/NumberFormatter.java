package org.exist.xquery.util;

import org.exist.xquery.XPathException;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.text.DateFormatSymbols;
import java.time.DayOfWeek;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

import static java.lang.invoke.MethodType.methodType;

/**
 * Formatter for numbers and dates. Concrete implementations are language-dependant.
 *
 * @author Wolfgang
 */
public abstract class NumberFormatter {

    private static int[] zeroDigits = {
            0x0030, 0x0660, 0x06f0, 0x0966, 0x09e6, 0x0a66, 0x0ae6, 0x0b66, 0x0be6, 0x0c66, 0x0ce6,
            0x0d66, 0x0e50, 0x0ed0, 0x0f20, 0x1040, 0x17e0, 0x1810, 0x1946, 0x19d0, 0xff10,
            0x104a0, 0x107ce, 0x107d8, 0x107e2, 0x107ec, 0x107f6 };

    private static char OPTIONAL_DIGIT_SIGN = '#';

    /**
     * Get the zero digit corresponding to the digit family of the given value.
     * This method was taken from saxon, see <a href="http://saxon.sourceforge.net/">http://saxon.sourceforge.net/</a>.
     *
     * @param val an integer value
     * @return the zero digit
     */
    public static int getZeroDigit(int val) {
        for (int z = 0; z < zeroDigits.length; z++) {
            if (val <= zeroDigits[z] + 9) {
                if (val >= zeroDigits[z]) {
                    return zeroDigits[z];
                } else {
                    return -1;
                }
            }
        }
        return -1;
    }

    private final Locale locale;

    public NumberFormatter(final Locale locale) {
        this.locale = locale;
    }

    public String getMonth(int month) {
        return Month.of(month).getDisplayName(TextStyle.FULL, locale);
    }

    public String getDay(int day) {
        return DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale);
    }

    public String getAmPm(int hour) {
        final DateFormatSymbols symbols = DateFormatSymbols.getInstance(locale);
        final String[] amPm = symbols.getAmPmStrings();
        if (hour > 12) {
            return amPm[1];
        }
        return amPm[0];
    }

    public abstract String getOrdinalSuffix(long number);

    public String formatNumber(long number, String picture) throws XPathException {
        final int min = getMinDigits(picture);
        final int max = getMaxDigits(picture);
        return formatNumber(number, picture, min, max);
    }

    public String formatNumber(long number, String picture, int min, int max) throws XPathException {
        if (picture == null)
            {return "" + number;}

        boolean ordinal = false;
        if (picture.endsWith("o")) {
            ordinal = true;
            picture = picture.substring(0, picture.length() - 1);
        } else if (picture.endsWith("c")) {
            picture = picture.substring(0, picture.length() - 1);
        }

        final StringBuilder sb = new StringBuilder();
        final int digitSign = getFirstDigit(picture);
        final int zero = getZeroDigit(digitSign);

        int count = 0;
        long n = number;
        while (n > 0) {
            final int digit = zero + (int)n % 10;
            sb.insert(0, (char)digit);
            count++;
            if (count == max)
                {break;}
            n = n / 10;
        }
        if (sb.length() < min) {
            for (int i = sb.length(); i < min; i++) {
                sb.insert(0, (char) zero);
            }
        }

        if (ordinal)
            {sb.append(getOrdinalSuffix(number));}
        return sb.toString();
    }

    private int getFirstDigit(String picture) throws XPathException {
        for (int i = 0; i < picture.length(); i++) {
            final char ch = picture.charAt(i);
            if (ch != OPTIONAL_DIGIT_SIGN)
                {return ch;}
        }
        throw new XPathException("There should be at least one digit sign in the picture string: " + picture);
    }

    public static int getMinDigits(String picture) {
        int count = 0;
        for (int i = 0; i < picture.length(); i++) {
            final char ch = picture.charAt(i);
            if ((ch == 'o' || ch == 'c') && i == picture.length() - 1)
                {break;}
            if (ch != OPTIONAL_DIGIT_SIGN)
                {count++;}
        }
        return count;
    }

    public static int getMaxDigits(String picture) {
        int count = 0;
        for (int i = 0; i < picture.length(); i++) {
            final char ch = picture.charAt(i);
            if ((ch == 'o' || ch == 'c') && i == picture.length() - 1)
                {break;}
            count++;
        }
        return count;
    }

    public static NumberFormatter getInstance(final String language) {
        final String className = NumberFormatter.class.getName() + "_" + language;
        final Locale locale = new Locale(language);
        try {
            final Class langClazz = Class.forName(className);
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            final MethodHandle methodHandle = lookup.findConstructor(langClazz, methodType(void.class, Locale.class));
            final java.util.function.Function<Locale, NumberFormatter> constructor = (java.util.function.Function<Locale, NumberFormatter>)
                    LambdaMetafactory.metafactory(
                            lookup, "apply", methodType(java.util.function.Function.class),
                            methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();
            return constructor.apply(locale);
        } catch (final Throwable e) {
            if (e instanceof InterruptedException) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }

            return new NumberFormatter_en(locale);
        }
    }
}