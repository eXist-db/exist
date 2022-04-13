package org.exist.xquery.functions.integer;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class IntegerPicture {

    final static Pattern decimalDigitPattern = Pattern.compile("^((\\p{Nd}|#|[^\\p{N}\\p{L}])+?)$", Pattern.UNICODE_CHARACTER_CLASS);
    final static Pattern invalidDigitPattern = Pattern.compile("(\\p{Nd})");

    protected String primaryFormatToken;
    protected String formatModifier;

    protected IntegerPicture(final String primaryFormatToken, final String formatModifier) {
        this.primaryFormatToken = primaryFormatToken;
        this.formatModifier = formatModifier;
    }

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
            IntegerPicture result = new DigitsIntegerPicture(primaryFormatToken, formatModifier);
            result.parseFormatToken();

            return result;
        }

        // incorrect type 1 matcher (and not anything else)
        final Matcher invalidDigitMatcher = invalidDigitPattern.matcher(primaryFormatToken);
        if (invalidDigitMatcher.find()) {
            throw new XPathException(ErrorCodes.FODF1310, "Invalid primary format token is not a valid decimal digital pattern: " + primaryFormatToken);
        }

        //TODO (AP) types 2...
        throw new XPathException(ErrorCodes.FODF1310, "Not implemented");
    }

    /**
     * Set up the picture based on the format string
     *
     * @throws XPathException if something is bad/wrong with the form of the picture format string
     */
    abstract void parseFormatToken() throws XPathException;

    /**
     * pass an integer and a language string to the picture, and format the integer according to the picture and language
     * @param bigInteger
     * @param language
     * @return
     */
    abstract public String formatInteger(BigInteger bigInteger, String language);
}
