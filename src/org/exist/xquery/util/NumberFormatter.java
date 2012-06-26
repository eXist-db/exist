package org.exist.xquery.util;

import org.exist.xquery.XPathException;

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
     * This method was taken from saxon {@link http://saxon.sourceforge.net/}.
     *
     * @param val
     * @return
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

    public NumberFormatter() {
    }

    public abstract String getMonth(int month);

    public abstract String getDay(int day);

    public abstract String getAmPm(int hour);

    public abstract String getOrdinalSuffix(long number);

    public String formatNumber(long number, String picture) throws XPathException {
        int min = getMinDigits(picture);
        int max = getMaxDigits(picture);
        return formatNumber(number, picture, min, max);
    }

    public String formatNumber(long number, String picture, int min, int max) throws XPathException {
        if (picture == null)
            return "" + number;

        boolean ordinal = false;
        if (picture.endsWith("o")) {
            ordinal = true;
            picture = picture.substring(0, picture.length() - 1);
        } else if (picture.endsWith("c")) {
            picture = picture.substring(0, picture.length() - 1);
        }

        StringBuilder sb = new StringBuilder();
        int digitSign = getFirstDigit(picture);
        int zero = getZeroDigit(digitSign);

        int count = 0;
        long n = number;
        while (n > 0) {
            int digit = zero + (int)n % 10;
            sb.insert(0, (char)digit);
            count++;
            if (count == max)
                break;
            n = n / 10;
        }
        if (sb.length() < min) {
            for (int i = sb.length(); i < min; i++) {
                sb.insert(0, (char) zero);
            }
        }

        if (ordinal)
            sb.append(getOrdinalSuffix(number));
        return sb.toString();
    }

    private int getFirstDigit(String picture) throws XPathException {
        for (int i = 0; i < picture.length(); i++) {
            char ch = picture.charAt(i);
            if (ch != OPTIONAL_DIGIT_SIGN)
                return ch;
        }
        throw new XPathException("There should be at least one digit sign in the picture string: " + picture);
    }

    public static int getMinDigits(String picture) {
        int count = 0;
        for (int i = 0; i < picture.length(); i++) {
            char ch = picture.charAt(i);
            if (ch != OPTIONAL_DIGIT_SIGN)
                count++;
        }
        return count;
    }

    public static int getMaxDigits(String picture) {
        return picture.length();
    }
}