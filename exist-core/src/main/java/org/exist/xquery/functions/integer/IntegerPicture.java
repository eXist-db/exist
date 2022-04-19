package org.exist.xquery.functions.integer;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class IntegerPicture {

    final static BigInteger TEN = BigInteger.valueOf(10L);

    final static Pattern decimalDigitPattern = Pattern.compile("^((\\p{Nd}|#|[^\\p{N}\\p{L}])+?)$", Pattern.UNICODE_CHARACTER_CLASS);
    final static Pattern invalidDigitPattern = Pattern.compile("(\\p{Nd})");

    /**
     * The value of $picture consists of a primary format token,
     * optionally followed by a format modifier.
     *
     * @param pictureFormat the format to use - choose which sub picture is needed
     * @return the right picture to handle this format
     * @throws XPathException if the format is not a known/valid form of picture format
     */
    public static IntegerPicture fromString(final String pictureFormat) throws XPathException {

        String primaryFormatToken;
        String formatModifier;

        final int splitPosition = pictureFormat.lastIndexOf(';');
        if (splitPosition < 0) {
            primaryFormatToken = pictureFormat;
            formatModifier = "";
        } else {
            primaryFormatToken = pictureFormat.substring(0, splitPosition);
            formatModifier = pictureFormat.substring(splitPosition + 1);
        }
        if (primaryFormatToken.isEmpty()) {
            throw new XPathException(ErrorCodes.FODF1310, "Invalid (empty) primary format token in integer format token: " + primaryFormatToken);
        }

        // type 1 matcher (some digits)
        final Matcher decimalDigitMatcher = decimalDigitPattern.matcher(primaryFormatToken);
        if (decimalDigitMatcher.matches()) {
            return new DigitsIntegerPicture(primaryFormatToken, formatModifier);
        }

        // incorrect type 1 matcher (and not anything else)
        final Matcher invalidDigitMatcher = invalidDigitPattern.matcher(primaryFormatToken);
        if (invalidDigitMatcher.find()) {
            throw new XPathException(ErrorCodes.FODF1310, "Invalid primary format token is not a valid decimal digital pattern: " + primaryFormatToken);
        }

        switch (primaryFormatToken) {
            case "A":
                return new SequenceIntegerPicture('A');
            case "a":
                return new SequenceIntegerPicture('a');
            case "i":
            case "I":
            case "W":
            case "w":
            case "Ww":
            default:
                // TODO (AP) any other token
                throw new XPathException(ErrorCodes.FODF1310, "Not implemented");
        }
    }

    /**
     * pass an integer and a language string to the picture, and format the integer according to the picture and language
     * @param bigInteger
     * @param language
     * @return
     */
    abstract public String formatInteger(BigInteger bigInteger, String language) throws XPathException;

    /**
     * Convert a string into a list of unicode code points
     * @param s the input string
     * @return a list of the codepoints forming the string
     */
    protected static List<Integer> CodePoints(String s) {
        final List<Integer> codePointList = new ArrayList<>(s.length());
        for (int i = 0; i < s.length();) {
            int codePoint = Character.codePointAt(s, i);
            i += Character.charCount(codePoint);
            codePointList.add(codePoint);
        }
        return codePointList;
    }

    protected static String FromCodePoint(int codePoint) {
        StringBuilder sb = new StringBuilder();
        for (char c : Character.toChars(codePoint)) {
            sb.append(c);
        }
        return sb.toString();
    }
}
