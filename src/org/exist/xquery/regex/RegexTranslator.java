package org.exist.xquery.regex;

import net.sf.saxon.trans.Err;
import net.sf.saxon.charcode.UTF16CharacterSet;
import net.sf.saxon.om.FastStringBuffer;
import net.sf.saxon.sort.IntHashSet;
import net.sf.saxon.value.Whitespace;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Abstract superclass for the various regex translators, which differ according to the target platform.
 * 
 * Copied from Saxon-HE 9.2 package net.sf.saxon.regex without change.
 */
public abstract class RegexTranslator {

    protected CharSequence regExp;
    protected int xmlVersion;
    protected boolean isXPath;
    protected boolean ignoreWhitespace;
    protected boolean inCharClassExpr;
    protected boolean caseBlind;
    protected int pos = 0;
    protected int length;
    protected char curChar;
    protected boolean eos = false;
    protected int currentCapture = 0;
    protected IntHashSet captures = new IntHashSet();
    protected final FastStringBuffer result = new FastStringBuffer(FastStringBuffer.SMALL);

    protected void translateTop() throws RegexSyntaxException {
         translateRegExp();
         if (!eos) {
             throw makeException("expected end of string");
         }
    }


    protected void translateRegExp() throws RegexSyntaxException {
        translateBranch();
        while (curChar == '|') {
            copyCurChar();
            translateBranch();
        }
    }

    protected void translateBranch() throws RegexSyntaxException {
        while (translateAtom())
            translateQuantifier();
    }

    protected abstract boolean translateAtom() throws RegexSyntaxException;

    protected void translateQuantifier() throws RegexSyntaxException {
        switch (curChar) {
            case '*':
            case '?':
            case '+':
                copyCurChar();
                break;
            case '{':
                copyCurChar();
                translateQuantity();
                expect('}');
                copyCurChar();
                break;
            default:
                return;
        }
        if (curChar == '?' && isXPath) {
            copyCurChar();
        }
    }

    protected void translateQuantity() throws RegexSyntaxException {
        String lower = parseQuantExact().toString();
        int lowerValue = -1;
        try {
            lowerValue = Integer.parseInt(lower);
            result.append(lower);
        } catch (NumberFormatException e) {
            // JDK 1.4 cannot handle ranges bigger than this
            result.append("" + Integer.MAX_VALUE);
        }
        if (curChar == ',') {
            copyCurChar();
            if (curChar != '}') {
                String upper = parseQuantExact().toString();
                try {
                    int upperValue = Integer.parseInt(upper);
                    result.append(upper);
                    if (lowerValue < 0 || upperValue < lowerValue)
                        throw makeException("invalid range in quantifier");
                } catch (NumberFormatException e) {
                    result.append("" + Integer.MAX_VALUE);
                    if (lowerValue < 0 && new BigDecimal(lower).compareTo(new BigDecimal(upper)) > 0)
                        throw makeException("invalid range in quantifier");
                }
            }
        }
    }

    protected CharSequence parseQuantExact() throws RegexSyntaxException {
        FastStringBuffer buf = new FastStringBuffer(FastStringBuffer.TINY);
        do {
            if ("0123456789".indexOf(curChar) < 0)
                throw makeException("expected digit in quantifier");
            buf.append(curChar);
            advance();
        } while (curChar != ',' && curChar != '}');
        return buf;
    }

    protected void copyCurChar() {
        result.append(curChar);
        advance();
    }

    public static final int NONE = -1;
    public static final int SOME = 0;
    public static final int ALL = 1;

    public static final String SURROGATES1_CLASS = "[\uD800-\uDBFF]";
    public static final String SURROGATES2_CLASS = "[\uDC00-\uDFFF]";
    public static final String NOT_ALLOWED_CLASS = "[\u0000&&[^\u0000]]";

    /**
     * A Range represents a range of consecutive Unicode codepoints
     */

    public static final class Range implements Comparable {
        private final int min;
        private final int max;

        /**
         * Create a range of unicode codepoints
         * @param min the first codepoint in the range
         * @param max the last codepoint in the range
         */

        public Range(int min, int max) {
            this.min = min;
            this.max = max;
        }

        /**
         * Get the start of the range
         * @return the first codepoint in the range
         */

        public int getMin() {
            return min;
        }

        /**
         * Get the end of the range
         * @return the last codepoint in the range
         */

        public int getMax() {
            return max;
        }

        /**
         * Compare this range with another range for ordering purposes. If the two ranges have different
         * start points, the order is the order of the start points; otherwise it is the order of the end
         * points.
         * @param o the other range
         * @return -1 if this range comes first, +1 if the other range comes first, 0 if they are equal
         * (start and end both equal)
         */

        public int compareTo(Object o) {
            Range other = (Range) o;
            if (min < other.min)
                return -1;
            if (min > other.min)
                return 1;
            if (max > other.max)
                return -1;
            if (max < other.max)
                return 1;
            return 0;
        }
    }


    protected void advance() {
        if (pos < length) {
            curChar = regExp.charAt(pos++);
            if (ignoreWhitespace && !inCharClassExpr) {
                while (Whitespace.isWhitespace(curChar)) {
                    advance();
                }
            }
        } else {
            pos++;
            curChar = RegexData.EOS;
            eos = true;
        }
    }

    protected int absorbSurrogatePair() throws RegexSyntaxException {
        if (UTF16CharacterSet.isSurrogate(curChar)) {
            if (!UTF16CharacterSet.isHighSurrogate(curChar))
                throw makeException("invalid surrogate pair");
            char c1 = curChar;
            advance();
            if (!UTF16CharacterSet.isLowSurrogate(curChar))
                throw makeException("invalid surrogate pair");
            return UTF16CharacterSet.combinePair(c1, curChar);
        } else {
            return curChar;
        }
    }

    protected void recede() {
        // The caller must ensure we don't fall off the start of the expression
        if (eos) {
            curChar = regExp.charAt(length - 1);
            pos = length;
            eos = false;
        } else {
            curChar = regExp.charAt((--pos)-1);
        }
        if (ignoreWhitespace && !inCharClassExpr) {
            while (Whitespace.isWhitespace(curChar)) {
                recede();
            }
        }
    }

    protected void expect(char c) throws RegexSyntaxException {
        if (curChar != c) {
            throw makeException("expected", new String(new char[]{c}));
        }
    }

    protected RegexSyntaxException makeException(String key) {
        return new RegexSyntaxException("Error at character " + (pos - 1) +
                " in regular expression " + Err.wrap(regExp, Err.VALUE) + ": " + key);
    }

    protected RegexSyntaxException makeException(String key, String arg) {
        return new RegexSyntaxException("Error at character " + (pos - 1) +
                " in regular expression " + Err.wrap(regExp, Err.VALUE) + ": " + key +
                " (" + arg + ')');
    }

    protected static boolean isJavaMetaChar(int c) {
        switch (c) {
            case '\\':
            case '^':
            case '?':
            case '*':
            case '+':
            case '(':
            case ')':
            case '{':
            case '}':
            case '|':
            case '[':
            case ']':
            case '-':
            case '&':
            case '$':
            case '.':
                return true;
        }
        return false;
    }

    protected static String highSurrogateRanges(List ranges) {
        FastStringBuffer highRanges = new FastStringBuffer(ranges.size() * 2);
        for (int i = 0, len = ranges.size(); i < len; i++) {
            Range r = (Range)ranges.get(i);
            char min1 = UTF16CharacterSet.highSurrogate(r.getMin());
            char min2 = UTF16CharacterSet.lowSurrogate(r.getMin());
            char max1 = UTF16CharacterSet.highSurrogate(r.getMax());
            char max2 = UTF16CharacterSet.lowSurrogate(r.getMax());
            if (min2 != UTF16CharacterSet.SURROGATE2_MIN) {
                min1++;
            }
            if (max2 != UTF16CharacterSet.SURROGATE2_MAX) {
                max1--;
            }
            if (max1 >= min1) {
                highRanges.append(min1);
                highRanges.append(max1);
            }
        }
        return highRanges.toString();
    }

    protected static String lowSurrogateRanges(List ranges) {
        FastStringBuffer lowRanges = new FastStringBuffer(ranges.size() * 2);
        for (int i = 0, len = ranges.size(); i < len; i++) {
            Range r = (Range)ranges.get(i);
            char min1 = UTF16CharacterSet.highSurrogate(r.getMin());
            char min2 = UTF16CharacterSet.lowSurrogate(r.getMin());
            char max1 = UTF16CharacterSet.highSurrogate(r.getMax());
            char max2 = UTF16CharacterSet.lowSurrogate(r.getMax());
            if (min1 == max1) {
                if (min2 != UTF16CharacterSet.SURROGATE2_MIN || max2 != UTF16CharacterSet.SURROGATE2_MAX) {
                    lowRanges.append(min1);
                    lowRanges.append(min2);
                    lowRanges.append(max2);
                }
            } else {
                if (min2 != UTF16CharacterSet.SURROGATE2_MIN) {
                    lowRanges.append(min1);
                    lowRanges.append(min2);
                    lowRanges.append(UTF16CharacterSet.SURROGATE2_MAX);
                }
                if (max2 != UTF16CharacterSet.SURROGATE2_MAX) {
                    lowRanges.append(max1);
                    lowRanges.append(UTF16CharacterSet.SURROGATE2_MIN);
                    lowRanges.append(max2);
                }
            }
        }
        return lowRanges.toString();
    }

    protected static void sortRangeList(List ranges) {
        Collections.sort(ranges);
        int toIndex = 0;
        int fromIndex = 0;
        int len = ranges.size();
        while (fromIndex < len) {
            Range r = (Range)ranges.get(fromIndex);
            int min = r.getMin();
            int max = r.getMax();
            while (++fromIndex < len) {
                Range r2 = (Range)ranges.get(fromIndex);
                if (r2.getMin() > max + 1)
                    break;
                if (r2.getMax() > max)
                    max = r2.getMax();
            }
            if (max != r.getMax())
                r = new Range(min, max);
            ranges.set(toIndex++, r);
        }
        while (len > toIndex)
            ranges.remove(--len);
    }

    protected static boolean isBlock(String name) {
        for (int i = 0; i < RegexData.blockNames.length; i++) {
            if (name.equals(RegexData.blockNames[i])) {
                return true;
            }
        }
        return false;
    }

    protected static boolean isAsciiAlnum(char c) {
        return  'a' <= c && c <= 'z' ||
                'A' <= c && c <= 'Z' ||
                '0' <= c && c <= '9';
    }



}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Contributor(s):
//

