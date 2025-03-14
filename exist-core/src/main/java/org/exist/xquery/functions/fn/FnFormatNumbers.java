/*
 * NOTE: Where indicated, this file is in part based on code from
 * The BaseX Team. The original license statement is also included below.
 *
 * Copyright (c) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The BSD 3-Clause
 * License by Evolved Binary for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The BSD 3-Clause license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (c) 2014, Evolved Binary Ltd
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Evolved Binary nor the names of its contributors
 *       may be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL EVOLVED BINARY BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (c) 2005-20 BaseX Team
 * All rights reserved.
 *
 * The BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.xquery.functions.fn;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.dom.QName;
import org.exist.util.CodePointString;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.lang.String;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Optional;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;


/**
 * Implements fn:format-number as per W3C XPath and XQuery Functions and Operators 3.1
 *
 * fn:format-number($value as numeric?, $picture as xs:string) as xs:string
 * fn:format-number($value as numeric?, $picture as xs:string, $decimal-format-name as xs:string) as xs:string
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class FnFormatNumbers extends BasicFunction {

    private static final FunctionParameterSequenceType FS_PARAM_VALUE = optParam("value", Type.NUMERIC, "The number to format");
    private static final FunctionParameterSequenceType FS_PARAM_PICTURE = param("picture", Type.STRING, "The picture string to use for formatting. To understand the picture string syntax, see: https://www.w3.org/TR/xpath-functions-31/#func-format-number");

    private static final String FS_FORMAT_NUMBER_NAME = "format-number";
    static final FunctionSignature[] FS_FORMAT_NUMBER = functionSignatures(
            FS_FORMAT_NUMBER_NAME,
            "Returns a string containing a number formatted according to a given picture string, taking account of decimal formats specified in the static context.",
            returns(Type.STRING, "The formatted string representation of the supplied number"),
            arities(
                    arity(
                            FS_PARAM_VALUE,
                            FS_PARAM_PICTURE
                    ),
                    arity(
                            FS_PARAM_VALUE,
                            FS_PARAM_PICTURE,
                            optParam("decimal-format-name", Type.STRING, "The name (as an EQName) of a decimal format to use.")
                    )
            )
    );

    public FnFormatNumbers(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence)
            throws XPathException {

        // get the decimal format
        final QName qnDecimalFormat;
        if (args.length == 3 && !args[2].isEmpty()) {
            final String decimalFormatName = args[2].itemAt(0).getStringValue().trim();
            try {
                qnDecimalFormat = QName.parse(context, decimalFormatName);
            } catch (final QName.IllegalQNameException e) {
                throw new XPathException(this, ErrorCodes.FODF1280, "Invalid decimal format QName.", args[2], e);
            }
        } else {
            qnDecimalFormat = null;
        }
        final DecimalFormat decimalFormat = context.getStaticDecimalFormat(qnDecimalFormat);
        if (decimalFormat == null) {
            throw new XPathException(this, ErrorCodes.FODF1280, "No known decimal format of that name.", args[2]);
        }

        final NumericValue number;
        if (args[0].isEmpty()) {
            number = new DoubleValue(this, Double.NaN);
        } else if (context.isBackwardsCompatible() && !Type.subTypeOfUnion(args[0].getItemType(), Type.NUMERIC)) {
            number = new DoubleValue(this, Double.NaN);
        } else {
            number = (NumericValue) args[0].itemAt(0);
        }

        final CodePointString pictureString = new CodePointString(args[1].itemAt(0).getStringValue());

        final Tuple2<SubPicture, Optional<SubPicture>> subPictures = analyzePictureString(decimalFormat, pictureString);
        final String value = format(number, decimalFormat, subPictures);
        return new StringValue(this, value);
    }

    enum AnalyzeState {
        MANTISSA_PART,
        INTEGER_PART,
        FRACTIONAL_PART,
        EXPONENT_PART
    }

    /**
     * Analyzes a picture-string sent to fn:format-number.
     *
     * See https://www.w3.org/TR/xpath-functions-31/#syntax-of-picture-string
     * See https://www.w3.org/TR/xpath-functions-31/#analyzing-picture-string
     *
     * @param decimalFormat the decimal format to use
     * @param pictureString the picture-string
     *
     * @return A tuple containing one or two sub-pictures
     *
     * @throws XPathException if the picture-string is invalid
     */
    private Tuple2<SubPicture, Optional<SubPicture>> analyzePictureString(final DecimalFormat decimalFormat, final CodePointString pictureString) throws XPathException {
        if (pictureString.length() == 0) {
            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() $picture string is zero-length");
        }

        final SubPicture firstSubPicture = new SubPicture();
        @Nullable SubPicture secondSubPicture = null;

        SubPicture subPicture = firstSubPicture;

        AnalyzeState state = AnalyzeState.INTEGER_PART;  // we start in the integer part of the mantissa
        int idx = 0;

        boolean capturePrefix = true;

        // we need two characters of look-behind to be able to detect
        // various invalid sub-pictures:
        //   1) active-passive-active characters
        //   2) grouping-separator character that appears adjacent to a decimal-separator character
        int prevPrevChar = '\0';
        int prevChar = '\0';

        for (; idx < pictureString.length(); idx++) {
            final int c = pictureString.codePointAt(idx);

            if (isActiveChar(decimalFormat, prevPrevChar) && (!isActiveChar(decimalFormat, prevChar)) && isActiveChar(decimalFormat, c)) {
                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture sub-picture must not contain a passive character that is preceded by an active character and that is followed by another active character");
            }

            switch (state) {

                case INTEGER_PART:
                    /* active characters */
                    if (c == decimalFormat.decimalSeparator) {
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        if (prevChar == decimalFormat.groupingSeparator) {
                            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture must not contain a grouping-separator character that appears adjacent to a decimal-separator character.");
                        }

                        subPicture.setHasDecimalSeparator(true);
                        state = AnalyzeState.FRACTIONAL_PART;
                    } else if (c == decimalFormat.exponentSeparator) {
                        /*
                        A character that matches the exponent-separator property is treated as an
                        exponent-separator-sign if it is both preceded and followed within the
                        sub-picture by an active character.
                        */

                        // we need to peek at the next char to determine if it is active
                        final boolean nextIsActive;
                        if (idx + 1 < pictureString.length()) {
                            nextIsActive = isActiveChar(decimalFormat, pictureString.codePointAt(idx + 1));
                        } else {
                            nextIsActive = false;
                        }

                        if (isActiveChar(decimalFormat, prevChar) && nextIsActive) {
                            // this is an exponent-separator-sign

                            capturePrefix = false;
                            subPicture.clearSuffix();

                            if (subPicture.hasPercent()) {
                                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture cannot contain an exponent separator sign as it already has a percent character.");
                            }

                            if (subPicture.hasPerMille()) {
                                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture cannot contain an exponent separator sign as it already has a per-mille character.");
                            }

                            state = AnalyzeState.EXPONENT_PART;

                        } else {
                            // just another passive char
                            analyzePassiveChar(decimalFormat, c, capturePrefix, subPicture);
                        }

                    } else if (c == decimalFormat.groupingSeparator) {
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        if (prevChar == decimalFormat.decimalSeparator) {
                            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture must not contain a grouping-separator character that appears adjacent to a decimal-separator character.");
                        }

                        if (prevChar == decimalFormat.groupingSeparator) {
                            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture must not contain two adjacent instances of the grouping-separator character.");
                        }

                        subPicture.newIntegerPartGroupingPosition();
                    } else if (c == decimalFormat.digit) {
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        if (isDecimalDigit(decimalFormat, prevChar)) {
                            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture must not contain a member of the decimal digit family that is followed by an instance of the optional digit character within its integer part.");
                        }

                        subPicture.incrementIntegerPartGroupingPosition();
                        subPicture.setHasIntegerOptionalDigit(true);
                    } else if (c == decimalFormat.patternSeparator) {
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        if (subPicture == secondSubPicture) {
                            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() $picture string contains more than two sub-pictures");
                        } else {
                            // store/check any outstanding picture state
                            if (!(subPicture.hasIntegerOptionalDigit() || subPicture.getMinimumIntegerPartSize() > 0 || subPicture.getMaximumFractionalPartSize() > 0)) {
                                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() mantissa part of sub-picture in $picture must contain at least one character that is either an optional digit character or a member of the decimal digit family");
                            }

                            if (prevChar == decimalFormat.groupingSeparator) {
                                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture must not contain a grouping-separator character in the absence of a decimal-separator character, at the end of the integer part.");
                            }

                            // switch to 2nd sub-picture
                            secondSubPicture = new SubPicture();
                            subPicture = secondSubPicture;

                            // reset analyze state
                            state = AnalyzeState.INTEGER_PART;
                            prevPrevChar = '\0';
                            prevChar = '\0';
                            capturePrefix = true;
                        }
                    } else if (isDecimalDigit(decimalFormat, c)) {  // decimal digit family
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        subPicture.incrementIntegerPartGroupingPosition();
                        subPicture.incrementMinimumIntegerPartSize();
                        subPicture.incrementScalingFactor();

                    } else {
                        /* passive character */
                        analyzePassiveChar(decimalFormat, c, capturePrefix, subPicture);
                    }

                    break;  // end of INTEGER_PART



                case FRACTIONAL_PART:
                    /* active characters */
                    if (c == decimalFormat.decimalSeparator) {
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        if (prevChar == decimalFormat.groupingSeparator) {
                            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture must not contain a grouping-separator character that appears adjacent to a decimal-separator character.");
                        }

                        subPicture.setHasDecimalSeparator(true);
                        throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture string contains more than one decimal-separator characters");
                    } else if (c == decimalFormat.exponentSeparator) {
                        /*
                        A character that matches the exponent-separator property is treated as an
                        exponent-separator-sign if it is both preceded and followed within the
                        sub-picture by an active character.
                        */

                        // we need to peek at the next char to determine if it is active
                        final boolean nextIsActive;
                        if (idx + 1 < pictureString.length()) {
                            nextIsActive = isActiveChar(decimalFormat, pictureString.codePointAt(idx + 1));
                        } else {
                            nextIsActive = false;
                        }

                        if (isActiveChar(decimalFormat, prevChar) && nextIsActive) {
                            // this is an exponent-separator-sign

                            capturePrefix = false;
                            subPicture.clearSuffix();

                            if (subPicture.hasPercent()) {
                                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture cannot contain an exponent separator sign as it already has a percent character.");
                            }

                            if (subPicture.hasPerMille()) {
                                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture cannot contain an exponent separator sign as it already has a per-mille character.");
                            }

                            state = AnalyzeState.EXPONENT_PART;

                        } else {
                            // just another passive char
                            analyzePassiveChar(decimalFormat, c, capturePrefix, subPicture);
                        }

                    } else if (c == decimalFormat.groupingSeparator) {
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        if (prevChar == decimalFormat.decimalSeparator) {
                            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture must not contain a grouping-separator character that appears adjacent to a decimal-separator character.");
                        }

                        if (prevChar == decimalFormat.groupingSeparator) {
                            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture must not contain two adjacent instances of the grouping-separator character.");
                        }

                        subPicture.newFractionalPartGroupingPosition();
                    } else if (c == decimalFormat.digit) {
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        subPicture.incrementMaximumFractionalPartSize();
                    }  else if (c == decimalFormat.patternSeparator) {
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        if (subPicture == secondSubPicture) {
                            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() $picture string contains more than two sub-pictures");
                        } else {
                            // store/check any outstanding picture state
                            if (!(subPicture.hasIntegerOptionalDigit() || subPicture.getMinimumIntegerPartSize() > 0 || subPicture.getMaximumFractionalPartSize() > 0)) {
                                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() mantissa part of sub-picture in $picture must contain at least one character that is either an optional digit character or a member of the decimal digit family");
                            }

                            // switch to 2nd sub-picture
                            secondSubPicture = new SubPicture();
                            subPicture = secondSubPicture;

                            // reset analyze state
                            state = AnalyzeState.INTEGER_PART;
                            prevPrevChar = '\0';
                            prevChar = '\0';
                            capturePrefix = true;
                        }
                    } else if (isDecimalDigit(decimalFormat, c)) {  // decimal digit family
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        if (prevChar == decimalFormat.digit) {
                            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture must not contain an instance of the optional digit character that is followed by a member of the decimal digit family within its fractional part.");
                        }

                        subPicture.incrementMinimumFractionalPartSize();
                        subPicture.incrementMaximumFractionalPartSize();

                    } else {
                        /* passive character */
                        analyzePassiveChar(decimalFormat, c, capturePrefix, subPicture);
                    }

                    break;  // end of FRACTIONAL_PART



                case EXPONENT_PART:
                    if (c == decimalFormat.decimalSeparator
                            || c == decimalFormat.exponentSeparator
                            || c == decimalFormat.groupingSeparator
                            || c == decimalFormat.digit) {
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture cannot have any active characters following the exponent-separator-sign");

                    }  else if (c == decimalFormat.patternSeparator) {
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        if (subPicture == secondSubPicture) {
                            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() $picture string contains more than two sub-pictures");
                        } else {
                            // store/check any outstanding picture state
                            if (!(subPicture.hasIntegerOptionalDigit() || subPicture.getMinimumIntegerPartSize() > 0 || subPicture.getMaximumFractionalPartSize() > 0)) {
                                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() mantissa part of sub-picture in $picture must contain at least one character that is either an optional digit character or a member of the decimal digit family");
                            }

                            // switch to 2nd sub-picture
                            secondSubPicture = new SubPicture();
                            subPicture = secondSubPicture;

                            // reset analyze state
                            state = AnalyzeState.INTEGER_PART;
                            prevPrevChar = '\0';
                            prevChar = '\0';
                            capturePrefix = true;
                        }
                    } else if (isDecimalDigit(decimalFormat, c)) {  // decimal digit family
                        capturePrefix = false;
                        subPicture.clearSuffix();

                        subPicture.incrementMinimumExponentSize();

                    } else {
                        /* passive character */
                        analyzePassiveChar(decimalFormat, c, capturePrefix, subPicture);
                    }

                    break;  // end of EXPONENT_PART
            }

            if (c != decimalFormat.patternSeparator) {
                prevPrevChar = prevChar;
                prevChar = c;
            }
        }

        if (!(subPicture.hasIntegerOptionalDigit() || subPicture.getMinimumIntegerPartSize() > 0 || subPicture.getMaximumFractionalPartSize() > 0)) {
            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() mantissa part of sub-picture in $picture must contain at least one character that is either an optional digit character or a member of the decimal digit family");
        }

        if ((!subPicture.hasDecimalSeparator()) && prevChar == decimalFormat.groupingSeparator) {
            throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture must not contain a grouping-separator character in the absence of a decimal-separator character, at the end of the integer part.");
        }

        return Tuple(firstSubPicture.adjust(), Optional.ofNullable(secondSubPicture).map(SubPicture::adjust));
    }

    private void analyzePassiveChar(final DecimalFormat decimalFormat, final int c, final boolean capturePrefix, final SubPicture subPicture) throws XPathException {
        if (capturePrefix) {
            subPicture.appendPrefix(c);
        }
        subPicture.appendSuffix(c);

        if (c == decimalFormat.percent) {
            if (subPicture.hasPercent()) {
                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture string contains more than one percent character");
            } else if (subPicture.hasPerMille()) {
                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture string cannot contain a per-mille character and a percent character");
            }
            subPicture.setHasPercent(true);
        } else if (c == decimalFormat.perMille) {
            if (subPicture.hasPerMille()) {
                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture string contains more than one per-mille character");
            } else if (subPicture.hasPercent()) {
                throw new XPathException(this, ErrorCodes.FODF1310, "format-number() sub-picture in $picture string cannot contain a percent character and a per-mille character");
            }
            subPicture.setHasPerMille(true);
        }
    }

    private static boolean isDecimalDigit(final DecimalFormat decimalFormat, final int c) {
        return c >= decimalFormat.zeroDigit && c <= decimalFormat.zeroDigit + 9;
    }

    private static boolean isActiveChar(final DecimalFormat decimalFormat, final int c) {
        return c == decimalFormat.decimalSeparator
                || c == decimalFormat.exponentSeparator
                || c == decimalFormat.groupingSeparator
                || c == decimalFormat.digit
                || c == decimalFormat.patternSeparator
                || isDecimalDigit(decimalFormat, c);
    }

    private String format(final NumericValue number, final DecimalFormat decimalFormat, final Tuple2<SubPicture, Optional<SubPicture>> subPictures) throws XPathException {

        // Rule 1: return NaN for NaN
        if (number.isNaN()) {
            return decimalFormat.NaN;
        }

        // Rule 2: should we use the positive or negative sub-picture
        final SubPicture subPicture;
        if (number.isNegative()) {
            subPicture = subPictures._2.orElseGet(() -> subPictures._1.copy().negate(decimalFormat));
        } else {
            subPicture = subPictures._1;
        }

        /*
        In the rules below, the positive sub-picture and its associated variables are used if the input number is positive,
        and the negative sub-picture and its associated variables are used if it is negative. For xs:double and xs:float,
        negative zero is taken as negative, positive zero as positive. For xs:decimal and xs:integer, the positive
        sub-picture is used for zero.
         */

        // Rule 3: adjust for percent or permille
        NumericValue adjustedNumber;
        if (subPicture.hasPercent()) {
            adjustedNumber = (NumericValue) number.mult(new IntegerValue(this, 100));
        } else if(subPicture.hasPerMille()) {
            adjustedNumber = (NumericValue) number.mult(new IntegerValue(this, 1000));
        } else {
            adjustedNumber = number;
        }

        // Rule 4: return infinity for infinity
        if (adjustedNumber.isInfinite()) {
            return subPicture.getPrefixString() + decimalFormat.infinity + subPicture.getSuffixString();
        }

        // Rule 5 and 6: adjust for exponent
        // Rule 5 and 6 were modified from BaseX code, Copyright BaseX Team 2005-19, BSD License
        int exp = 0;
        if (subPicture.getMinimumExponentSize() > 0 && !number.isZero()) {
            BigDecimal dec = number.convertTo(Type.DECIMAL).toJavaObject(BigDecimal.class).abs().stripTrailingZeros();
            int scl = 0;
            if (dec.compareTo(BigDecimal.ONE) >= 0) {
                scl = dec.setScale(0, RoundingMode.HALF_DOWN).precision();
            } else {
                while (dec.compareTo(BigDecimal.ONE) < 0) {
                    dec = dec.multiply(BigDecimal.TEN);
                    scl--;
                }
                scl++;
            }
            exp = scl - subPicture.getScalingFactor();
            if (exp != 0) {
                final BigDecimal n = BigDecimal.TEN.pow(Math.abs(exp));
                adjustedNumber = (NumericValue) adjustedNumber.mult(new DecimalValue(this, exp > 0 ? BigDecimal.ONE.divide(n, MathContext.DECIMAL64) : n));
            }
        }

        adjustedNumber = new DecimalValue(this, adjustedNumber.convertTo(Type.DECIMAL).toJavaObject(BigDecimal.class).multiply(BigDecimal.ONE, MathContext.DECIMAL64)).round(new IntegerValue(this, subPicture.getMaximumFractionalPartSize())).abs();

        /* we can now start formatting for display */

        // Rule 7 - must always contain a decimal-separator, and it must contain no leading zeroes and no trailing zeroes.
        final CodePointString formatted = new CodePointString(adjustedNumber
                .toJavaObject(BigDecimal.class)
                .toPlainString());

        formatted.replaceFirst('.', decimalFormat.decimalSeparator);
        // this string must always contain a decimal-separator
        if (!formatted.contains(decimalFormat.decimalSeparator)) {
            formatted.append(decimalFormat.decimalSeparator);
        }
        // must contain no leading zeroes and no trailing zeroes
        formatted.leftTrim('0');
        formatted.rightTrim('0');

        // covert to using the digits in the decimal digit family to represent the ten decimal digits
        if (decimalFormat.zeroDigit != '0') {
            formatted.transform('0', '9', decimalFormat.zeroDigit);
        }


        int idxDecimalSeparator = formatted.indexOf(decimalFormat.decimalSeparator);

        // Rule 8 - Left pad
        int intLength =  idxDecimalSeparator > -1 ? idxDecimalSeparator : formatted.length();
        final int leftPadLen = subPicture.getMinimumIntegerPartSize() - intLength;
        if (leftPadLen > 0) {
            formatted.leftPad(decimalFormat.zeroDigit, leftPadLen);

            idxDecimalSeparator = formatted.indexOf(decimalFormat.decimalSeparator);
            intLength =  idxDecimalSeparator > -1 ? idxDecimalSeparator : formatted.length();
        }

        // Rule 9 - Right pad
        int fractLen =  idxDecimalSeparator > -1 ?  formatted.length() - (idxDecimalSeparator + 1) : 0;
        final int rightPadLen = subPicture.getMinimumFractionalPartSize() - fractLen;
        if (rightPadLen > 0) {
            formatted.rightPad(decimalFormat.zeroDigit, rightPadLen);

            idxDecimalSeparator = formatted.indexOf(decimalFormat.decimalSeparator);
            fractLen =  idxDecimalSeparator > -1 ?  formatted.length() - (idxDecimalSeparator + 1) : 0;
        }

        // Rule 10 - Integer part groupings
        @Nullable final int[] integerPartGroupingPositions = subPicture.getIntegerPartGroupingPositions();
        if (integerPartGroupingPositions != null) {
            final int g = subPicture.integerPartGroupingPositionsAreRegular();
            if (g > -1) {
                // regular grouping
                int m = intLength / g;
                if (intLength % g == 0) {
                    m--; // prevents a group separator being inserted at index 0
                }
                if (m > -1) {
                    final int[] relGroupingOffsets = new int[m];
                    for (; m > 0; m--) {
                        final int groupingIdx = idxDecimalSeparator - (m * g);
                        relGroupingOffsets[m - 1] = groupingIdx;
                    }
                    formatted.insert(relGroupingOffsets, decimalFormat.groupingSeparator);

                    idxDecimalSeparator = formatted.indexOf(decimalFormat.decimalSeparator);
                }
            } else {
                // non-regular grouping
                final int[] relGroupingOffsets = new int[integerPartGroupingPositions.length];
                for (int i = 0; i < integerPartGroupingPositions.length; i++) {
                    final int integerPartGroupingPosition = integerPartGroupingPositions[i];
                    final int groupingIdx = idxDecimalSeparator - integerPartGroupingPosition;
                    relGroupingOffsets[i] = groupingIdx;
                }
                formatted.insert(relGroupingOffsets, decimalFormat.groupingSeparator);

                idxDecimalSeparator = formatted.indexOf(decimalFormat.decimalSeparator);

            }
        }

        // Rule 11 - Fractional part groupings
        @Nullable final int[] fractionalPartGroupingPositions = subPicture.getFractionalPartGroupingPositions();
        if (fractionalPartGroupingPositions != null) {
            int[] relGroupingOffsets = new int[0];
            for (int i = 0; i < fractionalPartGroupingPositions.length; i++) {
                final int fractionalPartGroupingPosition = fractionalPartGroupingPositions[i];
                final int groupingIdx = idxDecimalSeparator + 1 + fractionalPartGroupingPosition;
                if (groupingIdx <= formatted.length()) {
                    relGroupingOffsets = Arrays.copyOf(relGroupingOffsets, relGroupingOffsets.length + 1);
                    relGroupingOffsets[i] = groupingIdx;
                } else {
                    break;
                }
            }

            if (relGroupingOffsets.length > 0) {
                formatted.insert(relGroupingOffsets, decimalFormat.groupingSeparator);
            }

            fractLen =  idxDecimalSeparator > -1 ?  formatted.length() - (idxDecimalSeparator + 1) : 0;
        }

        // Rule 12 - strip decimal separator if unneeded
        if (!subPicture.hasDecimalSeparator() || fractLen == 0) {
            formatted.removeFirst(decimalFormat.decimalSeparator);
        }

        // Rule 13 - add exponent if exists
        final int minimumExponentSize = subPicture.getMinimumExponentSize();
        if (minimumExponentSize > 0) {
            formatted.append(decimalFormat.exponentSeparator);
            if (exp < 0) {
                formatted.append(decimalFormat.minusSign);
            }

            final CodePointString expStr = new CodePointString(String.valueOf(exp));

            final int expPadLen = subPicture.getMinimumExponentSize() - expStr.length();
            if (expPadLen > 0) {
                expStr.leftPad(decimalFormat.zeroDigit, expPadLen);
            }

            formatted.append(expStr);
        }

        // Rule 14 - concatenate prefix, formatted number, and suffix
        final String result = subPicture.getPrefixString() + formatted + subPicture.getSuffixString();

        return result;
    }

    /**
     * Data class for a SubPicture.
     *
     * See https://www.w3.org/TR/xpath-functions-31/#analyzing-picture-string
     */
    private static class SubPicture {
        private int[] integerPartGroupingPositions;
        private int minimumIntegerPartSize;
        private int scalingFactor;
        private StringBuilder prefix;
        private int[] fractionalPartGroupingPositions;
        private int minimumFractionalPartSize;
        private int maximumFractionalPartSize;
        private int minimumExponentSize;
        private StringBuilder suffix;

        // state needed for adjustment
        private boolean hasIntegerOptionalDigit = false;
        private boolean hasPercent = false;
        private boolean hasPerMille = false;
        private boolean hasDecimalSeparator = false;

        public SubPicture copy() {
            final SubPicture copy = new SubPicture();

            copy.integerPartGroupingPositions = integerPartGroupingPositions == null ? null : Arrays.copyOf(integerPartGroupingPositions, integerPartGroupingPositions.length);
            copy.minimumIntegerPartSize = minimumIntegerPartSize;
            copy.scalingFactor = scalingFactor;
            copy.prefix = prefix == null ? null : new StringBuilder(prefix);
            copy.fractionalPartGroupingPositions = fractionalPartGroupingPositions == null ? null : Arrays.copyOf(fractionalPartGroupingPositions, fractionalPartGroupingPositions.length);
            copy.minimumFractionalPartSize = minimumFractionalPartSize;
            copy.maximumFractionalPartSize = maximumFractionalPartSize;
            copy.minimumExponentSize = minimumExponentSize;
            copy.suffix = suffix == null ? null : new StringBuilder(suffix);

            copy.hasIntegerOptionalDigit = hasIntegerOptionalDigit;
            copy.hasPercent = hasPercent;
            copy.hasPerMille = hasPerMille;
            copy.hasDecimalSeparator = hasDecimalSeparator;

            return copy;
        }

        public SubPicture negate(final DecimalFormat decimalFormat) {
            this.prefix = new StringBuilder().appendCodePoint(decimalFormat.minusSign).append(getPrefixString());
            return this;
        }

        public void newIntegerPartGroupingPosition() {
            if (integerPartGroupingPositions == null) {
                integerPartGroupingPositions = new int[1];
            } else {
                integerPartGroupingPositions = Arrays.copyOf(integerPartGroupingPositions, integerPartGroupingPositions.length + 1);
            }
        }

        public void incrementIntegerPartGroupingPosition() {
            if (integerPartGroupingPositions == null) {
                return;
            }
            for (int i = 0; i < integerPartGroupingPositions.length; i++) {
                integerPartGroupingPositions[i]++;
            }
        }

        public @Nullable int[] getIntegerPartGroupingPositions() {
            return integerPartGroupingPositions;
        }

        /**
         * Determines if the <code>integer-part-grouping-positions</code> are regular.
         *
         * @return the value of G if regular, or -1 if irregular
         */
        public int integerPartGroupingPositionsAreRegular() {
            // There is an least one grouping-separator in the integer part of the sub-picture.
            if (integerPartGroupingPositions.length > 0) {

                // There is a positive integer G (the grouping size) such that the position of every grouping-separator
                // in the integer part of the sub-picture is a positive integer multiple of G.
                final int smallestGroupPosition = integerPartGroupingPositions[integerPartGroupingPositions.length - 1];
                int g = smallestGroupPosition;
                boolean divisible = false;
                for (; g > 0; g--) {
                    divisible = false;
                    for (final int integerPartGroupingPosition : integerPartGroupingPositions) {
                        divisible = integerPartGroupingPosition % g == 0;
                        if (!divisible) {
                            break;
                        }
                    }

                    if (divisible) {
                        break;
                    }
                }

                if (!divisible) {
                    return -1;
                }

                // Every position in the integer part of the sub-picture that is a positive integer multiple of G is
                // occupied by a grouping-separator.
                final int largestGroupPosition = integerPartGroupingPositions[integerPartGroupingPositions.length - 1];
                int m = 2;
                for (int p = g; p <= largestGroupPosition; p = g * m++) {

                    boolean isGroupSeparator = false;
                    for (final int integerPartGroupingPosition : integerPartGroupingPositions) {
                        if (integerPartGroupingPosition == p) {
                            isGroupSeparator = true;
                            break;
                        }
                    }

                    if (!isGroupSeparator) {
                        return -1;
                    }
                }

                return g;
            }

            return -1;
        }

        public void incrementMinimumIntegerPartSize() {
            minimumIntegerPartSize++;
        }

        public int getMinimumIntegerPartSize() {
            return minimumIntegerPartSize;
        }

        public void incrementScalingFactor() {
            scalingFactor++;
        }

        public int getScalingFactor() {
            return scalingFactor;
        }

        public void appendPrefix(final int c) {
            if (prefix == null) {
                prefix = new StringBuilder().appendCodePoint(c);
            } else {
                prefix = prefix.appendCodePoint(c);
            }
        }

        public String getPrefixString() {
            if (prefix == null) {
                return "";
            } else {
                return prefix.toString();
            }
        }

        public void newFractionalPartGroupingPosition() {
            if (fractionalPartGroupingPositions == null) {
                fractionalPartGroupingPositions = new int[1];
            } else {
                fractionalPartGroupingPositions = Arrays.copyOf(fractionalPartGroupingPositions, fractionalPartGroupingPositions.length + 1);
            }

            fractionalPartGroupingPositions[fractionalPartGroupingPositions.length - 1] = maximumFractionalPartSize;
        }

        public @Nullable int[] getFractionalPartGroupingPositions() {
            return fractionalPartGroupingPositions;
        }

        public void incrementMinimumFractionalPartSize() {
            minimumFractionalPartSize++;
        }

        public int getMinimumFractionalPartSize() {
            return minimumFractionalPartSize;
        }

        public void incrementMaximumFractionalPartSize() {
            maximumFractionalPartSize++;
        }

        public int getMaximumFractionalPartSize() {
            return maximumFractionalPartSize;
        }

        public void incrementMinimumExponentSize() {
            minimumExponentSize++;
        }

        public int getMinimumExponentSize() {
            return minimumExponentSize;
        }

        public void appendSuffix(final int c) {
            if (suffix == null) {
                suffix = new StringBuilder().appendCodePoint(c);
            } else {
                suffix = suffix.appendCodePoint(c);
            }
        }

        public void clearSuffix() {
            if (suffix != null) {
               suffix.setLength(0);
            }
        }

        public String getSuffixString() {
            if (suffix == null) {
                return "";
            } else {
                return suffix.toString();
            }
        }

        public void setHasIntegerOptionalDigit(final boolean hasIntegerOptionalDigit) {
            this.hasIntegerOptionalDigit = hasIntegerOptionalDigit;
        }

        public boolean hasIntegerOptionalDigit() {
            return hasIntegerOptionalDigit;
        }

        public void setHasPercent(final boolean hasPercent) {
            this.hasPercent = hasPercent;
        }

        public boolean hasPercent() {
            return hasPercent;
        }

        public void setHasPerMille(final boolean hasPerMille) {
            this.hasPerMille = hasPerMille;
        }

        public boolean hasPerMille() {
            return hasPerMille;
        }

        public void setHasDecimalSeparator(final boolean hasDecimalSeparator) {
            this.hasDecimalSeparator = hasDecimalSeparator;
        }

        public boolean hasDecimalSeparator() {
            return hasDecimalSeparator;
        }

        public SubPicture adjust() {
            if (minimumIntegerPartSize == 0 && maximumFractionalPartSize == 0) {
                if (minimumExponentSize > 0) {
                    minimumFractionalPartSize = 1;
                    maximumFractionalPartSize = 1;
                } else {
                    minimumIntegerPartSize = 1;
                }
            }

            if (minimumExponentSize > 0  && minimumIntegerPartSize == 0 && hasIntegerOptionalDigit) {
                minimumIntegerPartSize = 1;
            }

            if (minimumIntegerPartSize == 0 && minimumFractionalPartSize == 0) {
                minimumFractionalPartSize = 1;
            }

            return this;
        }
    }
}
