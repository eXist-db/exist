package org.exist.xquery.functions.integer;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import java.math.BigInteger;

public class SequenceIntegerPicture extends IntegerPicture {

    private final static BigInteger RADIX = BigInteger.valueOf(26L);

    private final int codePoint;

    SequenceIntegerPicture(final int codePoint) {
        this.codePoint = codePoint;
    }

    @Override
    public String formatInteger(BigInteger bigInteger, String language) throws XPathException {
        if (bigInteger.compareTo(BigInteger.ZERO) <= 0) throw new XPathException(ErrorCodes.FODF1310, "The format picture " + FromCodePoint(codePoint) + "cannot be used to format a number less than 1");

        StringBuilder sb = new StringBuilder();
        do {
            bigInteger = bigInteger.subtract(BigInteger.ONE);
            BigInteger[] divideAndRemainder = bigInteger.divideAndRemainder(RADIX);
            sb.append(FromCodePoint(codePoint + divideAndRemainder[1].intValue()));
            bigInteger = divideAndRemainder[0];
        }
        while (bigInteger.compareTo(BigInteger.ZERO) > 0);

        return sb.reverse().toString();
    }
}
