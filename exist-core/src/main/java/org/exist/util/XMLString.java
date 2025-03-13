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
package org.exist.util;

import net.jcip.annotations.NotThreadSafe;
import org.exist.xquery.Constants;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Faster string implementation which uses a CharArrayPool to
 * pool the backing char arrays.
 */
@NotThreadSafe
public final class XMLString implements CharSequence, Comparable<CharSequence> {

    public final static int SUPPRESS_NONE = 0;
    public final static int SUPPRESS_LEADING_WS = 0x01;
    public final static int SUPPRESS_TRAILING_WS = 0x02;
    public final static int COLLAPSE_WS = 0x04;
    public final static int SUPPRESS_BOTH = SUPPRESS_LEADING_WS | SUPPRESS_TRAILING_WS;
    public final static int NORMALIZE = SUPPRESS_LEADING_WS | SUPPRESS_TRAILING_WS | COLLAPSE_WS;

    public final static int DEFAULT_CAPACITY = 16;

    private char[] value_ = null;
    private int start_ = 0;
    private int length_ = 0;

    public XMLString() {
        value_ = CharArrayPool.getCharArray(DEFAULT_CAPACITY);
    }

    public XMLString(final int capacity) {
        value_ = CharArrayPool.getCharArray(capacity);
    }

    public XMLString(final char[] ch) {
        value_ = CharArrayPool.getCharArray(ch.length);
        System.arraycopy(ch, 0, value_, 0, ch.length);
        length_ = ch.length;
    }

    public XMLString(final char[] ch, final int start, final int length) {
        value_ = CharArrayPool.getCharArray(length);
        System.arraycopy(ch, start, value_, 0, length);
        length_ = length;
    }

    public XMLString(final XMLString other) {
        value_ = CharArrayPool.getCharArray(other.length_);
        System.arraycopy(other.value_, other.start_, value_, 0, other.length_);
        length_ = other.length_;
    }

    public final XMLString append(final String str) {
        append(str.toCharArray());
        return this;
    }

    public final XMLString append(final char[] ch) {
        append(ch, 0, ch.length);
        return this;
    }

    public final XMLString append(final char[] ch, final int offset, final int len) {
        ensureCapacity(length_ + len);
        System.arraycopy(ch, offset, value_, length_, len);
        length_ += len;
        return this;
    }

    public final XMLString append(final XMLString other) {
        ensureCapacity(length_ + other.length_);
        System.arraycopy(other.value_, other.start_, value_, length_, other.length_);
        length_ += other.length_;
        return this;
    }

    public final XMLString append(final char ch) {
        if (value_.length < length_ + 2) {
            ensureCapacity(length_ + 1);
        }
        value_[length_++] = ch;
        return this;
    }

    public final void setData(final char[] ch, final int offset, final int len) {
        length_ = 0;
        start_ = 0;
        append(ch, offset, len);
    }

    /**
     * Normalize the string.
     *
     * @param mode the normalization mode
     *
     * @return may return `this` or a new XMLString. The caller should be prepared to cleanup one or
     *     two XMLString instances!
     */
    public final XMLString normalize(final int mode) {
        if (length_ == 0) {
            return this;
        }
        if ((mode & SUPPRESS_LEADING_WS) != 0) {
            while (length_ > 1 && isWhiteSpace(value_[start_])) {
                --length_;
                if (length_ > 0) {
                    ++start_;
                }
            }
        }
        if ((mode & SUPPRESS_TRAILING_WS) != 0) {
            while (length_ > 1 && isWhiteSpace(value_[start_ + length_ - 1])) {
                --length_;
            }
        }
        if ((mode & COLLAPSE_WS) != 0) {
            final XMLString copy = new XMLString(length_);
            boolean inWhitespace = true;
            for (int i = start_; i < start_ + length_; i++) {
                switch (value_[i]) {
                    case '\n':
                    case '\r':
                    case '\t':
                    case ' ':
                        if (inWhitespace && i != start_ + length_ - 1) {
                            // remove the whitespace
                        } else {
                            copy.append(' ');
                            inWhitespace = true;
                        }
                        break;
                    default:
                        copy.append(value_[i]);
                        inWhitespace = false;
                        break;
                }
            }
            return copy;
        }
        return this;
    }

    public final boolean isWhitespaceOnly() {
        if (length_ == 0) {
            return true;
        }
        int i = 0;
        while (i < length_ && isWhiteSpace(value_[start_ + i])) {
            i++;
        }
        return i == length_;
    }

    @Override
    public final String toString() {
        if (value_ == null) {
            return "null";
        }
        return new String(value_, start_, length_);
    }

    @Override
    public final int length() {
        return length_;
    }

    public final int startOffset() {
        return start_;
    }

    public final String substring(final int start, final int count) {
        if (start < 0 || count < 0 || start >= length_ || start + count > length_) {
            throw new StringIndexOutOfBoundsException();
        }
        return new String(value_, start_ + start, count);
    }

    /**
     * Delete the content between {@code offset} and {@code offset + count}.
     *
     * @param start the offset to start deleting from
     * @param count the number of characters to delete
     *
     * @return this after the deletion has been made
     */
    public final XMLString delete(final int start, final int count) {
        System.arraycopy(value_, start + count + start_, value_, start, length_ - (start + count));
        start_ = 0;
        length_ = length_ - count;
        return this;
    }

    /**
     * Insert the content at {@code offset}.
     *
     * @param offset the offset to start the insertion from
     * @param data the characters to be inserted
     *
     * @return this after the insertion has been made
     */
    public final XMLString insert(final int offset, final String data) {
        ensureCapacity(length_ + data.length());
        System.arraycopy(value_, offset, value_, offset + data.length(), length_ - offset);
        System.arraycopy(data.toCharArray(), 0, value_, offset, data.length());
        length_ += data.length();
        return this;
    }

    /**
     * Replace the content between {@code offset} and {@code offset + count}
     * with {@code data}.
     *
     * @param offset the offset to start replacing from
     * @param count the number of characters to replace
     * @param data the replacement characters
     *
     * @return this after the replacement has been made
     */
    public final XMLString replace(final int offset, final int count, final String data) {
        if (offset < 0 || count < 0 || offset >= length_ || offset + count > length_) {
            throw new StringIndexOutOfBoundsException();
        }

        // 1) create a new array of the correct size for the data
        final int change = data.length() - count;
        final int newValueLength = length_ + change;
        final char[] newValue = CharArrayPool.getCharArray(newValueLength);

        // 2) copy everything from value_ to newValue that is before our offset
        System.arraycopy(value_, 0, newValue, 0, offset);

        // 3) insert our replacement data at the offset
        System.arraycopy(data.toCharArray(), 0, newValue, offset, data.length());

        // 4) copy everything from value_ to newValue_ that is after our offset + count
        final int remainingExistingCharacters;
        if(!data.isEmpty() && length_ < data.length()) {
            // value_ is expanding or staying the same length
            remainingExistingCharacters = length_ - count;
        } else {
            // empty `data` (i.e. replacement), or shrinking of value_
            remainingExistingCharacters = length_ - offset - count;
        }
        System.arraycopy(value_, offset + count, newValue, offset + data.length(), remainingExistingCharacters);

        // 5) replace value_ with newValue
        CharArrayPool.releaseCharArray(value_);
        value_ = newValue;
        length_= newValueLength;

        return this;
    }

    public final char charAt(final int pos) {
        return value_[start_ + pos];
    }

    public static boolean isWhiteSpace(final char ch) {
        return (ch == 0x20) || (ch == 0x09) || (ch == 0xD) || (ch == 0xA);
    }

    @Override
    public final CharSequence subSequence(final int start, final int end) {
        return new String(value_, start_ + start, end - start);
    }

    /**
     * @return `this`
     */
    public final XMLString transformToLower() {
        final int end = start_ + length_;
        for (int i = start_; i < end; i++) {
            value_[i] = Character.toLowerCase(value_[i]);
        }
        return this;
    }

    public final int UTF8Size() {
        return UTF8.encoded(value_, start_, length_);
    }

    public final byte[] UTF8Encode(final byte[] b, final int offset) {
        return UTF8.encode(value_, start_, length_, b, offset);
    }

    public final void toSAX(final ContentHandler ch) throws SAXException {
        ch.characters(value_, start_, length_);
    }

    @Override
    public final int compareTo(final CharSequence cs) {
        for (int i = 0; i < length_ && i < cs.length(); i++) {
            if (value_[start_ + i] < cs.charAt(i)) {
                return Constants.INFERIOR;
            } else if (value_[start_ + i] > cs.charAt(i)) {
                return Constants.SUPERIOR;
            }
        }
        if (length_ < cs.length()) {
            return Constants.INFERIOR;
        } else if (length_ > cs.length()) {
            return Constants.SUPERIOR;
        } else {
            return Constants.EQUAL;
        }
    }

    @Override
    public boolean equals(final Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (anObject instanceof XMLString anotherString) {
            if (length_ == anotherString.length_) {
                int n = length_;
                final char[] v1 = value_;
                final char[] v2 = anotherString.value_;
                int i = start_;
                int j = anotherString.start_;

                while (n-- != 0) {
                    if (v1[i++] != v2[j++]) {
                        return false;
                    }
                }
                return true;
            }
        } else {
            final String anotherString = anObject.toString();
            if (length_ == anotherString.length()) {
                int j = start_;
                for (int i = 0; i < length_; i++) {
                    if (value_[j++] != anotherString.charAt(i)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int off = start_;
        int h = 0;
        for (int i = 0; i < length_; i++) {
            h = 31 * h + value_[off++];
        }
        return h;
    }

    private void ensureCapacity(final int capacity) {
        if (value_ == null) {
            value_ = CharArrayPool.getCharArray(capacity);
        } else if (value_.length - start_ < capacity) {
            int newCapacity = (length_ + 1) * 2;
            if (newCapacity < capacity) {
                newCapacity = capacity;
            }
            final char[] temp = CharArrayPool.getCharArray(newCapacity);
            System.arraycopy(value_, start_, temp, 0, length_);
            CharArrayPool.releaseCharArray(value_);
            value_ = temp;
            start_ = 0;
        }
    }

    public final void reset() {
        CharArrayPool.releaseCharArray(value_);
        value_ = null;
        start_ = 0;
        length_ = 0;
    }

    public final void reuse() {
        start_ = 0;
        length_ = 0;
    }
}
