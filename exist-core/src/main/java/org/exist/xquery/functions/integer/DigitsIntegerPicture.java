/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.functions.integer;

import com.ibm.icu.text.Replaceable;
import com.ibm.icu.text.ReplaceableString;
import com.ibm.icu.text.UnicodeSet;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import java.math.BigInteger;
import java.util.*;

import static com.ibm.icu.text.UnicodeMatcher.U_MATCH;

/**
 * Formatting integers according to https://www.w3.org/TR/xpath-functions-31/#formatting-integers
 */
class DigitsIntegerPicture extends IntegerPicture {

    // This was calculated by inspecting the result of com.ibm.icu.text.UnicodeSet("[:Nd:]")
    // In an ideal world it would be generated automatically as part of a build step.
    // Each family is a set of decimal digits (0-9) which can be used in a decimal digit pattern
    static final int[] codePointFamilies = new int[]{0x30, 0x660, 0x6f0, 0x7c0, 0x966, 0x9e6, 0xa66, 0xae6, 0xb66, 0xbe6, 0xc66, 0xce6, 0xd66, 0xde6, 0xe50, 0xed0, 0xf20, 0x1040, 0x1090, 0x17e0, 0x1810, 0x1946, 0x19d0, 0x1a80, 0x1a90, 0x1b50, 0x1bb0, 0x1c40, 0x1c50, 0xa620, 0xa8d0, 0xa900, 0xa9d0, 0xa9f0, 0xaa50, 0xabf0, 0xff10, 0x104a0, 0x11066, 0x110f0, 0x11136, 0x111d0, 0x112f0, 0x11450, 0x114d0, 0x11650, 0x116c0, 0x11730, 0x118e0, 0x11c50, 0x16a60, 0x16b50, 0x1d7ce, 0x1d7d8, 0x1d7e2, 0x1d7ec, 0x1d7f6, 0x1e950};

    static {
        Arrays.sort(DigitsIntegerPicture.codePointFamilies);
    }

    // A single group of decimal digits, and the separator of these groups
    static final UnicodeSet optionalPrefixDigitSet = new UnicodeSet("[#]").freeze();
    static final UnicodeSet decimalDigitSet = new UnicodeSet("[:Nd:]").freeze();
    static final UnicodeSet separatorDigitSet = new UnicodeSet("[:^Nd:]").add("[:^Lu:]").remove("#").freeze();

    private final String primaryFormatToken;
    private final FormatModifier formatModifier;

    // Fields determining the layout of decimal digits
    // These fields are generated when we parse the primaryFormatToken into groups
    private final List<Group> groups = new ArrayList<>();
    private boolean groupsAreRegular = false;
    private int mandatoryDigits = 1;
    private int digitFamily = -1;

    DigitsIntegerPicture(final String primaryFormatToken, final FormatModifier formatModifier) throws XPathException {
        this.primaryFormatToken = primaryFormatToken;
        this.formatModifier = formatModifier;

        parseFormatToken();
    }

    /**
     * Calculate the digit family that a codePoint belongs to
     *
     * @param codePoint a decimal digit
     * @return the codePoint representing 0 in the family of the input
     */
    private static int getCodePointFamily(final int codePoint) {
        final int index = Arrays.binarySearch(DigitsIntegerPicture.codePointFamilies, codePoint);
        if (index >= 0) {
            // The supplied codePoint is a codePoint family start digit
            return codePoint;
        }
        final int familyIndex = -(index + 2);
        return DigitsIntegerPicture.codePointFamilies[familyIndex];
    }

    /**
     * @throws XPathException if the format is incorrectly formed
     *                        {@see https://www.w3.org/TR/xpath-functions-31/#formatting-integers}
     */
    private void parseFormatToken() throws XPathException {
        buildGroups();
        countMandatoryDigits(); // Do it before we regularize
        regularizeGroups();
    }

    /**
     * assume {@link #primaryFormatToken} has a valid pattern
     * Interpret it into a list of groups
     */
    private void buildGroups() throws XPathException {

        boolean mandatoryDigitsObserved = false;

        final PrimaryFormatParser formatParser = new PrimaryFormatParser(primaryFormatToken);
        for (;;) {
            final Group group = new Group();
            group.optional = formatParser.matchSet(DigitsIntegerPicture.optionalPrefixDigitSet);
            if (group.optional > 0 && mandatoryDigitsObserved) {
                throw new XPathException((Expression) null, ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " has optional digit after mandatory digit at " + formatParser.match());
            }

            group.mandatory = formatParser.matchSet(DigitsIntegerPicture.decimalDigitSet);
            if (group.mandatory > 0) {
                mandatoryDigitsObserved = true;

                for (final int codePoint : formatParser.matchCodes()) {
                    // All families begin at 0x____0
                    // represent the family by the 0 character in the family
                    final int codePointFamily = DigitsIntegerPicture.getCodePointFamily(codePoint);
                    if (digitFamily != -1 && digitFamily != codePointFamily) {
                        throw new XPathException((Expression) null, ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " contains multiple digit families");
                    }
                    digitFamily = codePointFamily;
                }
            }

            final int trailingOptional = formatParser.matchSetOnce(DigitsIntegerPicture.optionalPrefixDigitSet);
            if (trailingOptional > 0) {
                throw new XPathException((Expression) null, ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " has optional digit after mandatory digit at " + formatParser.match());
            }

            if (group.optional == 0 && group.mandatory == 0) {
                throw new XPathException((Expression) null, ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " expected a digit grouping pattern at " + formatParser.pos());
            }

            final int separator = formatParser.matchSetOnce(DigitsIntegerPicture.separatorDigitSet);
            if (separator > 0) {
                if (formatParser.end()) {
                    throw new XPathException((Expression) null, ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " ends with a separator at " + formatParser.match());
                }
                group.separator = Optional.of(String.valueOf(Character.toChars(formatParser.matchCodes().getFirst())));
                groups.add(group);
            } else {
                groups.add(group);
                // No separator, so we're done.
                break;
            }
        }

        if (digitFamily == -1) {
            // # is a valid picture
            // use the default counting number as the family
            digitFamily = DigitsIntegerPicture.getCodePointFamily('0');
        }
    }

    /**
     * assume {@link #groups} has been generated
     * we calculate the minimum number of digits to display in output
     */
    private void countMandatoryDigits() {
        mandatoryDigits = 0;
        for (final Group group : groups) {
            mandatoryDigits += group.mandatory;
        }
        if (mandatoryDigits == 0) {
            mandatoryDigits = 1;
        }
    }

    /**
     * assume {@link #groups} has been generated
     * determine whether the groups are "regular" as per the spec
     * and if so, fix groups up so that 1 group can represent an infinite number
     */
    private void regularizeGroups() {
        groupsAreRegular = false;
        Group prev = groups.getFirst();
        for (int i = 1; i < groups.size(); i++) {
            final Group group = groups.get(i);

            if (!group.separator.isPresent()) group.separator = prev.separator;
            if (i > 1 && (group.total() != prev.total())) return;
            if (i == 1 && prev.total() > group.total()) return;
            if (!group.separator.equals(prev.separator)) return;

            prev = group;
        }
        // One group stands for an infinite series of regular groups
        groupsAreRegular = true;
        groups.clear();
        groups.add(prev);
    }

    /**
     * Numbers are formatted from the little end first
     * So we need to access groups from that end too
     * If groups are regular there is only ever 1 group,
     * and we return that.
     *
     * @param index from the end
     * @return the index-th group from the end
     */
    private Group getGroupFromEnd(final int index) {
        if (groupsAreRegular) {
            return groups.getFirst();
        } else if (index < groups.size()) {
            return groups.get(groups.size() - index - 1);
        } else {
            return null;
        }
    }

    /**
     * Represent a matching group in the formatting
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static class Group {
        int optional;
        int mandatory;
        Optional<String> separator;

        public Group() {
            this.optional = 0;
            this.mandatory = 0;
            this.separator = Optional.empty();
        }

        public int total() {
            return optional + mandatory;
        }

        @Override
        public String toString() {
            return "Group(" + optional + "," + mandatory + "," + separator.orElse("") + ")";
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("primary=").append(primaryFormatToken).append("::");
        sb.append("modifier=").append(formatModifier).append("::");
        sb.append("regular=").append(groupsAreRegular).append("::");
        for (final Group group : groups) {
            sb.append("group=").append(group).append("::");
        }
        return sb.substring(0, sb.length() - 2);
    }

    /**
     * Format a number according to this parsed formatting state
     *
     * @param bigInteger the number to format according to this picture
     * @param locale     holds the language to format {@code bigInteger} in, needed if we are returning an ordinal
     * @return the formatted string
     */
    @Override
    public String formatInteger(final BigInteger bigInteger, final Locale locale) {
        final StringBuilder reversed = formatNonNegativeInteger(bigInteger.abs());
        if (bigInteger.compareTo(BigInteger.ZERO) < 0) {
            reversed.append("-");
        }
        final StringBuilder result = reversed.reverse();
        if (formatModifier.numbering == FormatModifier.Numbering.ORDINAL && bigInteger.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0 && bigInteger.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
            result.append(IntegerPicture.ordinalSuffix(bigInteger.intValue(), locale));
        }

        return result.toString();
    }

    /**
     * @param bigInteger to format according to this picture
     * @return the reverse of the formatted string (think of it as a character stack)
     */
    private StringBuilder formatNonNegativeInteger(final BigInteger bigInteger) {

        final StringBuilder reversedBuilder = new StringBuilder();
        int remainingDigits = mandatoryDigits;
        int groupIndex = 0;
        Group group = getGroupFromEnd(groupIndex);
        int groupUsed = 0;
        BigInteger acc = bigInteger;
        while (remainingDigits > 0 || acc.compareTo(BigInteger.ZERO) > 0) {
            if (group != null && groupUsed == group.total()) {
                groupIndex++;
                group = getGroupFromEnd(groupIndex);
                groupUsed = 0;
                if (group != null) {
                    reversedBuilder.append(group.separator.orElse(""));
                }
            }
            final BigInteger[] divideAndRemainder = acc.divideAndRemainder(IntegerPicture.TEN);
            final BigInteger remainder = divideAndRemainder[1];
            final int codePoint = digitFamily + remainder.intValue();
            reversedBuilder.append(Character.toChars(codePoint));
            remainingDigits--;
            groupUsed++;
            acc = divideAndRemainder[0];
        }
        return reversedBuilder;
    }

    private static class PrimaryFormatParser {

        final String primaryFormat;
        final Replaceable target;
        final int length;
        final int[] offset = {0};
        int lastMatch = -1;

        PrimaryFormatParser(final String primaryFormat) {
            this.primaryFormat = primaryFormat;
            this.target = new ReplaceableString(primaryFormat);
            this.length = primaryFormat.length();
        }

        int matchSet(final UnicodeSet unicodeSet, final int maxPoints) {
            final int matchStart = offset[0];
            final int matchLimit = matchStart + maxPoints;
            int matchPoints = 0;
            for (int matchStatus = U_MATCH; offset[0] < matchLimit && matchStatus == U_MATCH;) {
                matchStatus = unicodeSet.matches(target, offset, length, false);
                if (matchStatus == U_MATCH) matchPoints++;
            }
            if (offset[0] > matchStart) {
                lastMatch = matchStart;
            }
            return matchPoints;
        }

        List<Integer> matchCodes() {
            return IntegerPicture.codePoints(primaryFormat.substring(lastMatch, offset[0]));
        }

        int matchSet(final UnicodeSet unicodeSet) {
            return matchSet(unicodeSet, length - offset[0]);
        }

        int matchSetOnce(final UnicodeSet unicodeSet) {
            return matchSet(unicodeSet, Math.min(length - offset[0], 1));
        }

        boolean end() {
            return (offset[0] >= length);
        }

        int pos() {
            return offset[0];
        }

        int match() {
            return lastMatch;
        }
    }
}
