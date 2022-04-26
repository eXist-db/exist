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

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatting integers according to https://www.w3.org/TR/xpath-functions-31/#formatting-integers
 */
class DigitsIntegerPicture extends IntegerPicture {

    // This was calculated by inspecting the result of com.ibm.icu.text.UnicodeSet("[:Nd:]")
    // In an ideal world it would be part of a build step.
    final static int[] codePointFamilies = new int[]{0x30, 0x660, 0x6f0, 0x7c0, 0x966, 0x9e6, 0xa66, 0xae6, 0xb66, 0xbe6, 0xc66, 0xce6, 0xd66, 0xde6, 0xe50, 0xed0, 0xf20, 0x1040, 0x1090, 0x17e0, 0x1810, 0x1946, 0x19d0, 0x1a80, 0x1a90, 0x1b50, 0x1bb0, 0x1c40, 0x1c50, 0xa620, 0xa8d0, 0xa900, 0xa9d0, 0xa9f0, 0xaa50, 0xabf0, 0xff10, 0x104a0, 0x11066, 0x110f0, 0x11136, 0x111d0, 0x112f0, 0x11450, 0x114d0, 0x11650, 0x116c0, 0x11730, 0x118e0, 0x11c50, 0x16a60, 0x16b50, 0x1d7ce, 0x1d7d8, 0x1d7e2, 0x1d7ec, 0x1d7f6, 0x1e950};

    static {
        Arrays.sort(codePointFamilies);
    }

    final static Pattern separatorPattern = Pattern.compile("([^\\p{N}\\p{L}])");
    final static Pattern groupPattern = Pattern.compile("(#*)(\\p{Nd}*)");

    private final String primaryFormatToken;
    private final FormatModifier formatModifier;

    private final List<Group> groups = new ArrayList<>();
    private boolean groupsAreRegular = false;
    private int mandatoryDigits = 1;
    private int digitFamily = -1;

    DigitsIntegerPicture(final String primaryFormatToken, final FormatModifier formatModifier) throws XPathException {
        this.primaryFormatToken = primaryFormatToken;
        this.formatModifier = formatModifier;

        parseFormatToken();
    }

    private int GetCodePointFamily(final int codePoint) {
        final int index = Arrays.binarySearch(codePointFamilies, codePoint);
        if (index >= 0) {
            // The supplied codePoint is a codePoint family start digit
            return codePoint;
        }
        final int familyIndex = -(index + 2);
        return codePointFamilies[familyIndex];
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
     * We have a primaryFormatToken with a valid pattern
     * Interpret the primaryFormatToken into a list of groups
     */
    private void buildGroups() throws XPathException {
        boolean mandatoryDigitsObserved = false;
        int end = 0;
        final Matcher m = groupPattern.matcher(primaryFormatToken);
        while (m.find()) {
            if (m.start() > end) {
                throw new XPathException(ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " expected a digit grouping pattern at " + end);
            }
            end = m.end();
            final Group group = new Group();
            group.optional = CodePoints(m.group(1)).size();
            if (group.optional > 0 && mandatoryDigitsObserved) {
                throw new XPathException(ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " has optional digit after mandatory digit");
            }

            final List<Integer> mandatory = CodePoints(m.group(2));
            group.mandatory = mandatory.size();
            if (group.mandatory > 0) {
                mandatoryDigitsObserved = true;
            }

            if (group.optional == 0 && group.mandatory == 0) {
                throw new XPathException(ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " expected a digit grouping pattern at " + end);
            }

            for (final int codePoint : mandatory) {
                // All families begin at 0x____0
                // represent the family by the 0 character in the family
                final int codePointFamily = GetCodePointFamily(codePoint);
                if (digitFamily != -1 && digitFamily != codePointFamily) {
                    throw new XPathException(ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " contains multiple digit families");
                }
                digitFamily = codePointFamily;
            }

            // Either we just finished, or there's a separator
            m.usePattern(separatorPattern);
            if (m.find()) {
                if (m.start() > end) {
                    throw new XPathException(ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " expected a separator at " + end);
                }
                // But a separator at the end is an error
                if (m.end() == primaryFormatToken.length()) {
                    throw new XPathException(ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " ends with a separator");
                }
                end = m.end();
            } else {
                groups.add(group);
                break;
            }
            group.separator = Optional.of(m.group(1));
            groups.add(group);

            m.usePattern(groupPattern);
        }

        if (digitFamily == -1) {
            // # is a valid picture
            // use the most usual counting number
            digitFamily = GetCodePointFamily('0');
        }

        // We should be at the end
        if (end < primaryFormatToken.length()) {
            throw new XPathException(ErrorCodes.FODF1310, "Primary format token " + primaryFormatToken + " unexpected character at position " + end);
        }
    }

    private void countMandatoryDigits() {
        mandatoryDigits = 0;
        for (final Group group : groups) {
            mandatoryDigits += group.mandatory;
        }
        if (mandatoryDigits == 0) {
            mandatoryDigits = 1;
        }
    }

    private void regularizeGroups() {
        groupsAreRegular = false;
        Group prev = groups.get(0);
        for (int i = 1; i < groups.size(); i++) {
            final Group group = groups.get(i);

            if (!group.separator.isPresent()) group.separator = prev.separator;
            if (i > 1 && (group.optional + group.mandatory != prev.optional + prev.mandatory)) return;
            if (!group.separator.equals(prev.separator)) return;

            prev = group;
        }
        // One group stands for an infinite series of regular groups
        groupsAreRegular = true;
        groups.clear();
        groups.add(prev);
    }

    private Group getGroupTail(final int index) {
        if (groupsAreRegular) {
            return groups.get(0);
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
     * Format a number according to this formatting picture
     *
     * @param bigInteger to format according to this picture
     * @param language   to format {@code bigInteger} in, where words are needed
     * @return the formatted string
     */
    @Override
    public String formatInteger(final BigInteger bigInteger, final String language) {
        final StringBuilder reversed = formatNonNegativeInteger(bigInteger.abs());
        if (bigInteger.compareTo(BigInteger.ZERO) < 0) {
            reversed.append("-");
        }
        final StringBuilder result = reversed.reverse();
        if (formatModifier.numbering == FormatModifier.Numbering.Ordinal &&
                bigInteger.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0 &&
                bigInteger.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
            result.append(ordinalSuffix(bigInteger.intValue(), language));
        }

        return result.toString();
    }

    /**
     * @param bigInteger to format according to this picture
     * @return the reverse of the formatted string (think of it as a character stack)
     */
    private StringBuilder formatNonNegativeInteger(BigInteger bigInteger) {

        final StringBuilder sb = new StringBuilder();
        int remainingDigits = mandatoryDigits;
        int currentGroup = 0;
        Group group = getGroupTail(currentGroup);
        int groupUsed = 0;
        while (remainingDigits > 0 || bigInteger.compareTo(BigInteger.ZERO) > 0) {
            if (group != null && groupUsed == group.total()) {
                currentGroup++;
                group = getGroupTail(currentGroup);
                groupUsed = 0;
                if (group != null) {
                    sb.append(group.separator.orElse(""));
                }
            }
            final BigInteger[] divideAndRemainder = bigInteger.divideAndRemainder(TEN);
            final BigInteger remainder = divideAndRemainder[1];
            final int codePoint = digitFamily + remainder.intValue();
            sb.append(Character.toChars(codePoint));
            remainingDigits--;
            groupUsed++;
            bigInteger = divideAndRemainder[0];
        }
        return sb;
    }
}
