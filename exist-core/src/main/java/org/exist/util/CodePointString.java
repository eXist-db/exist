/*
 * Copyright 2019 Evolved Binary Ltd
 *
 * This file was ported from FusionDB and relicensed by Evolved Binary
 * for use in eXist-db under The 3-Clause BSD License.
 */
package org.exist.util;

import net.jcip.annotations.NotThreadSafe;

import java.util.Arrays;

/**
 * Representation of a Unicode String.
 *
 * The String is a series of Unicode code-points.
 * Each Unicode code-point is an int value.
 *
 * Note that this is a mutable string implementation!
 *
 * @author Adam Retter <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@NotThreadSafe
public class CodePointString {
    private int[] codePoints;

    // TODO(AR) change resizing of codePoints so it isn't linear

    /**
     * Construct a Code Point String from a java.lang.String.
     *
     * @param string the Java String
     */
    public CodePointString(final String string) {
        this.codePoints = string.codePoints().toArray();
    }

    /**
     * Copy constructor.
     *
     * @param other the other Code Point String
     */
    public CodePointString(final CodePointString other) {
        this.codePoints = Arrays.copyOf(other.codePoints, other.codePoints.length);
    }

    public CodePointString() {
        this.codePoints = new int[0];
    }

    /**
     * Make a copy of this Code Point string.
     *
     * @return a copy of this Code Point String.
     */
    public CodePointString copy() {
        return new CodePointString(this);
    }

    /**
     * Replace the first instance of <code>oldCodePoint</code> with <code>newCodePoint</code>.
     *
     * @param oldCodePoint The code point to replace
     * @param newCodePoint The replacement code point
     *
     * @return this
     */
    public CodePointString replaceFirst(final int oldCodePoint, final int newCodePoint) {
        for (int i = 0; i < codePoints.length; i++) {
            if (codePoints[i] == oldCodePoint) {
                codePoints[i] = newCodePoint;
                break;
            }
        }
        return this;
    }

    /**
     * Replace all instances of <code>oldCodePoint</code> with <code>newCodePoint</code>.
     *
     * @param oldCodePoint The code point to replace all instances of
     * @param newCodePoint The replacement code point
     *
     * @return this
     */
    public CodePointString replaceAll(final int oldCodePoint, final int newCodePoint) {
        for (int i = 0; i < codePoints.length; i++) {
            if (codePoints[i] == oldCodePoint) {
                codePoints[i] = newCodePoint;
            }
        }
        return this;
    }

    /**
     * Find the index of a code point.
     *
     * @param codePoint The code point to find
     *
     * @return the index of the code point in the
     *     string, or -1 if it is not found
     */
    public int indexOf(final int codePoint) {
        for (int i = 0; i < codePoints.length; i++) {
            if (codePoints[i] == codePoint) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Determines if this string contains a code point.
     *
     * @param codePoint The code point to find
     *
     * @return true if the code point is found, false otherwise
     */
    public boolean contains(final int codePoint) {
        return indexOf(codePoint) >= 0;
    }

    /**
     * Append a code point to this string.
     *
     * @param codePoint the code point to append.
     * @return this
     */
    public CodePointString append(final int codePoint) {
        this.codePoints = Arrays.copyOf(codePoints, codePoints.length + 1);
        this.codePoints[codePoints.length - 1] = codePoint;
        return this;
    }

    /**
     * Append a code point string to this string.
     *
     * @param other the code point string to append.
     * @return this
     */
    public CodePointString append(final CodePointString other) {
        final int len = codePoints.length;
        this.codePoints = Arrays.copyOf(codePoints, len + other.length());
        System.arraycopy(other.codePoints, 0, codePoints, len, other.length());
        return this;
    }

    /**
     * Left trim this string.
     *
     * Removes n code points from the start of this string.
     *
     * @param codePoint the code point to trim starting from index 0
     *
     * @return this
     */
    public CodePointString leftTrim(final int codePoint) {
        if (codePoints.length > 0) {
            int i = 0;
            for (; i < codePoints.length && codePoints[i] == codePoint; i++) {
            }

            if (i > 0) {
                this.codePoints = Arrays.copyOfRange(codePoints, i, codePoints.length);
            }
        }
        return this;
    }

    /**
     * Right trim this string.
     *
     * Removes n code points from the end of this string.
     *
     * @param codePoint the code point to trim starting from index {@link #length()} - 1
     *
     * @return this
     */
    public CodePointString rightTrim(final int codePoint) {
        if (codePoints.length > 0) {
            int i = codePoints.length - 1;
            for (; i >= 0 && codePoints[i] == codePoint; i--) {
            }
            this.codePoints = Arrays.copyOfRange(codePoints, 0, i + 1);
        }
        return this;
    }

    /**
     * Transform a region of code points within the string
     *
     * Replaces any code point <code>c</code> between <code>fromOldCodePoint</code> (inclusive) to
     * <code>toOldCodePoint</code> (inclusive), with <code>fromNewCodePoint + (c - fromOldCodePoint)</code>.
     *
     * @param fromOldCodePoint the starting code point of the region to transform
     * @param toOldCodePoint the ending code point of the region to transform
     * @param fromNewCodePoint the new code point for the transformation
     *
     * @return this
     */
    public CodePointString transform(final int fromOldCodePoint, final int toOldCodePoint, final int fromNewCodePoint) {
        for (int i = 0; i < codePoints.length; i++) {
            final int c = codePoints[i];
            if (c >= fromOldCodePoint && c <= toOldCodePoint) {
                codePoints[i] = fromNewCodePoint + (c - fromOldCodePoint);
            }
        }
        return this;
    }

    /**
     * Pads the left of the string with <code>len</code> <code>codePoint</code>(s).
     *
     * @param codePoint the code point to use for the padding
     * @param len the length of the padding
     *
     * @return this
     */
    public CodePointString leftPad(final int codePoint, final int len) {
        if (len > 0) {
            final int[] newCodePoints = new int[codePoints.length + len];
            Arrays.fill(newCodePoints, 0, len, codePoint);
            System.arraycopy(codePoints, 0, newCodePoints, len, codePoints.length);
            this.codePoints = newCodePoints;
        }
        return this;
    }

    /**
     * Pads the right of the string with <code>len</code> <code>codePoint</code>(s).
     *
     * @param codePoint the code point to use for the padding
     * @param len the length of the padding
     *
     * @return this
     */
    public CodePointString rightPad(final int codePoint, final int len) {
        if (len > 0) {
            final int origLen = codePoints.length;
            final int newLen = codePoints.length + len;
            this.codePoints = Arrays.copyOf(codePoints, newLen);
            Arrays.fill(this.codePoints, origLen, newLen, codePoint);
        }
        return this;
    }

    /**
     * Insert a code point into the string.
     *
     * @param index the offset at which to insert the code point
     * @param codePoint the code point to insert
     *
     * @return this
     *
     * @throws IndexOutOfBoundsException if <code>index < 0 || index > getLength()</code>
     */
    public CodePointString insert(final int index, final int codePoint) {
        if (index < 0 || index > codePoints.length) {
            throw new IndexOutOfBoundsException();
        }

        final int[] newCodePoints = new int[codePoints.length + 1];
        System.arraycopy(codePoints, 0, newCodePoints, 0, index);
        newCodePoints[index] = codePoint;
        System.arraycopy(codePoints, index, newCodePoints, index + 1, codePoints.length - index);
        this.codePoints = newCodePoints;

        return this;
    }

    /**
     * Insert a code point into the string at one or more offsets.
     *
     * Note that this is NOT the same as calling {@link #insert(int, int)}
     * multiple times, as the <code>offsets</code> refer to the positions
     * in the string before the first insert is made.
     *
     * @param indexes the offsets at which to insert the code point
     * @param codePoint the code point to insert
     *
     * @return this
     *
     * @throws IndexOutOfBoundsException if <code>indexes[i] < 0 || indexes[i] > getLength()</code>
     */
    public CodePointString insert(final int[] indexes, final int codePoint) {
        // first sort the indexes into ascending order
        Arrays.sort(indexes);

        // only codePoints.length >= offsets > 0
        for (int i = 0; i < indexes.length; i++) {
            final int index = indexes[i];
            if (index < 0 || index > codePoints.length) {
                throw new IndexOutOfBoundsException();
            }
        }

        final int[] newCodePoints = Arrays.copyOf(codePoints, codePoints.length + indexes.length);
        for (int i = 0; i < indexes.length; i++) {
            final int index = indexes[i] + i;
            // shift to right
            if (newCodePoints.length > 1) {
                System.arraycopy(newCodePoints, index, newCodePoints, index + 1, newCodePoints.length - index - 1);
            }
            // insert codepoint
            newCodePoints[index] = codePoint;
        }

        this.codePoints = newCodePoints;
        return this;
    }

    /**
     * Remove the first instance of a code point from the string
     *
     * @param codePoint the code point to remove
     *
     * @return this
     */
    public CodePointString removeFirst(final int codePoint) {
        int idx = -1;
        for (int i = 0; i < codePoints.length; i++) {
            if (codePoints[i] == codePoint) {
                idx = i;
                break;
            }
        }

        if (idx > -1) {
            final int[] newCodePoints = new int[codePoints.length - 1];

            if (newCodePoints.length > 0) {
                System.arraycopy(codePoints, 0, newCodePoints, 0, idx);
                if (idx + 1 < codePoints.length) {
                    System.arraycopy(codePoints, idx + 1, newCodePoints, idx, newCodePoints.length - idx);
                }
            }

            this.codePoints = newCodePoints;
        }
        return this;
    }

    /**
     * Return the number of code points in the string.
     *
     * @return the number of code points in the string
     */
    public int length() {
        return codePoints.length;
    }

    /**
     * Gets a code point from the string.
     *
     * @param index the offset within the string
     *
     * @return the code point
     *
     * @throws IndexOutOfBoundsException if the index is outside the bounds of the string
     */
    public int codePointAt(final int index) {
        return codePoints[index];
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(codePoints.length);
        for (final int codePoint : codePoints) {
            builder.appendCodePoint(codePoint);
        }
        return builder.toString();
    }
}