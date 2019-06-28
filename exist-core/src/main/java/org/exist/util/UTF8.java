/*
 *  `gnu.iou' I/O buffers and utilities.
 *  Copyright (C) 1998, 1999, 2000, 2001, 2002 John Pritchard.
 *
 *  This program is free software; you can redistribute it or modify
 *  it under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this program; if not, write to the Free
 *  Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 *  02111-1307 USA
 */

package org.exist.util;

import javax.annotation.Nullable;

/**
 * This class contains two static tools for doing UTF-8 encoding and
 * decoding.
 *
 *  UTF-8 is ASCII- transparent.  It supports character sets
 * requiring more than the seven bit ASCII base range of UTF-8,
 * including Unicode, ISO-8859, ISO-10646, etc..
 *
 *  We do not use an ISO UCS code signature, and we do not use a
 * Java Data I/O- style strlen prefix.
 *
 * @author John Pritchard (john@syntelos.org)
 */
public class UTF8 {

    /**
     * Decode UTF-8 input, terminates decoding at a null character,
     * value 0x0.
     *
     * @param code the encoded UTf-8 string
     *
     * @return the decoded UTF-8 string
     *
     * @throws IllegalStateException Bad format.
     */
    public final static XMLString decode(@Nullable final byte[] code) {
        if (null == code) {
            return null;
        }
        return decode(code, 0, code.length);
    }

    public final static XMLString decode(@Nullable final byte[] code, final int off, final int many) {
        if (null == code || 0 >= code.length) {
            return null;
        }

        final XMLString xs = new XMLString(many);
        return decode(code, off, many, xs);
    }

    /**
     * Decode UTF-8 input, terminates decoding at a null character,
     * value 0x0.
     *
     * @param code the encoded UTf-8 string
     * @param off the offset of the string
     * @param many many
     * @param xs xs
     *
     * @return the decoded UTF-8 string
     *
     * @throws IllegalStateException Bad format.
     */
    public final static XMLString decode(@Nullable final byte[] code, final int off, final int many, final XMLString xs) {
        if (null == code || 0 >= code.length) {
            return null;
        }

        char ch;
        final int end = (off + many);
        byte cc;

        for (int c = off; c < end; c++) {
            cc = code[c];

            if (0 <= cc) {
                xs.append((char) cc);
            } else {
                ch = 0;

                if (b11000000 == (cc & b11100000)) {

                    ch |= (code[c + 1] & b00111111);
                    ch |= (cc & b00011111) << 6;

                    c += 1;
                } else if (b11100000 == (cc & b11110000)) {

                    ch |= (code[c + 2] & b00111111);
                    ch |= (code[c + 1] & b00111111) << 6;
                    ch |= (cc & b00001111) << 12;

                    c += 2;
                } else if (b11110000 == (cc & b11111000)) {

                    ch |= (code[c + 3] & b00111111);
                    ch |= (code[c + 2] & b00111111) << 6;
                    ch |= (code[c + 1] & b00111111) << 12;

                    c += 3;
                } else if (b11111000 == (cc & b11111100)) {

                    ch |= (code[c + 4] & b00111111);
                    ch |= (code[c + 3] & b00111111) << 6;
                    ch |= (code[c + 2] & b00111111) << 12;

                    c += 4;
                } else if (b11111100 == (cc & b11111110)) {

                    ch |= (code[c + 5] & b00111111);
                    ch |= (code[c + 4] & b00111111) << 6;
                    ch |= (code[c + 3] & b00111111) << 12;

                    c += 5;
                } else {
                    ch = (char) (cc & b01111111); // 0x7f

                }
                xs.append(ch);

            } // else // if ( 0 < cc)
        }

        return xs;
    }

    /**
     * Encode string in UTF-8.
     *
     * @param str the string to encode
     *
     * @return the encoded string
     */
    public final static byte[] encode(@Nullable final char[] str) {
        if (null == str || 0 >= str.length) {
            return null;
        }
        return encode(str, 0, str.length, null, 0);
    }

    /**
     * Encode string in UTF-8.
     *
     * Warning: the size of bytbuf is not checked. Use encoded() to determine
     * the size needed.
     *
     * @param str the string to encode
     * @param start the offset of the string
     * @param length the length of the string
     * @param bytbuf bytebuf
     * @param offset the offset in bytebuf
     *
     * @return the encoded string
     */
    public final static byte[] encode(@Nullable final char[] str, final int start, final int length, @Nullable byte[] bytbuf, int offset) {
        if (null == str || 0 >= length) {
            return bytbuf;
        }

        if (bytbuf == null) {
            bytbuf = new byte[encoded(str, start, length)];
        }

        char ch, sch;
        final int end = start + length;
        for (int c = start; c < end; c++) {

            ch = str[c];

            if (0x7f >= ch) {
                bytbuf[offset++] = (byte) ch;
            } else if (0x7ff >= ch) {

                sch = (char) (ch >>> 6);

                if (0 < sch) {
                    bytbuf[offset++] = (byte) (b11000000 | (sch & b00011111));
                } else {
                    bytbuf[offset++] = (byte) (b11000000);
                }

                bytbuf[offset++] = (byte) (b10000000 | (ch & b00111111));
            } else {

                sch = (char) (ch >>> 12);

                if (0 < sch) {

                    bytbuf[offset++] = (byte) (b11100000 | (sch & b00001111));
                } else {
                    bytbuf[offset++] = (byte) (b11100000);
                }

                bytbuf[offset++] = (byte) (b10000000 | ((ch >>> 6) & b00111111));

                bytbuf[offset++] = (byte) (b10000000 | (ch & b00111111));
            }
        }

        return bytbuf;
    }

    public final static byte[] encode(@Nullable final String str, @Nullable final byte[] bytbuf, final int offset) {
        return encode(str, 0, str.length(), bytbuf, offset);
    }

    /**
     * Encode string in UTF-8.
     *
     * Warning: the size of bytbuf is not checked. Use encoded() to determine
     * the size needed.
     *
     * @param str the string to encode
     * @param start the offset of the string
     * @param length the length of the string
     * @param bytbuf bytebuf
     * @param offset the offset in bytebuf
     *
     * @return the encoded string
     */
    public final static byte[] encode(@Nullable final String str, final int start, final int length, final byte[] bytbuf, int offset) {
        if (null == str || 0 >= length) {
            return bytbuf;
        }

        char ch, sch;
        final int end = start + length;
        for (int c = start; c < end; c++) {

            ch = str.charAt(c);

            if (0x7f >= ch) {
                bytbuf[offset++] = (byte) ch;
            } else if (0x7ff >= ch) {

                sch = (char) (ch >>> 6);

                if (0 < sch) {
                    bytbuf[offset++] = (byte) (b11000000 | (sch & b00011111));
                } else {
                    bytbuf[offset++] = (byte) (b11000000);
                }

                bytbuf[offset++] = (byte) (b10000000 | (ch & b00111111));
            } else {

                sch = (char) (ch >>> 12);

                if (0 < sch) {

                    bytbuf[offset++] = (byte) (b11100000 | (sch & b00001111));
                } else {
                    bytbuf[offset++] = (byte) (b11100000);
                }

                bytbuf[offset++] = (byte) (b10000000 | ((ch >>> 6) & b00111111));

                bytbuf[offset++] = (byte) (b10000000 | (ch & b00111111));
            }
        }

        return bytbuf;
    }

    /**
     * Encode string in UTF-8.
     *
     * @param s the string to encode
     *
     * @return the encoded string
     */
    public final static byte[] encode(@Nullable final String s) {
        if (null == s) {
            return null;
        } else {
            return encode(s.toCharArray(), 0, s.length(), null, 0);
        }
    }

    private static final char b10000000 = (char) 0x80;
    private static final char b11000000 = (char) 0xC0;
    private static final char b11100000 = (char) 0xE0;
    private static final char b11110000 = (char) 0xF0;
    private static final char b11111000 = (char) 0xF8;
    private static final char b11111100 = (char) 0xFC;
    private static final char b11111110 = (char) 0xFE;

    private static final char b01111111 = (char) 0x7F;
    private static final char b00111111 = (char) 0x3F;
    private static final char b00011111 = (char) 0x1F;
    private static final char b00001111 = (char) 0x0F;
    //private static final char b00000111 = (char) 0x07;
    //private static final char b00000011 = (char) 0x03;
    //private static final char b00000001 = (char) 0x01;

    /**
     * Returns the length of the string encoded in UTF-8.
     *
     * @param str the string
     * @return the length of the encoded string
     */
    public final static int encoded(@Nullable final String str) {
        if (null == str) {
            return 0;
        }

        int bytlen = 0;

        char ch;
        //char sch;
        for (int c = 0; c < str.length(); c++) {
            ch = str.charAt(c);

            if (0x7f >= ch) {
                bytlen++;
            } else if (0x7ff >= ch) {
                bytlen += 2;
            } else {
                bytlen += 3;
            }

        }

        return bytlen;
    }

    /**
     * Returns the length of the string encoded in UTF-8.
     *
     * @param str the string
     * @param start the offset of the string
     * @param len the length of the string
     * @return the length of the encoded string
     */
    public final static int encoded(@Nullable final char[] str, final int start, final int len) {
        if (null == str || 0 >= len) {
            return 0;
        }

        int bytlen = 0;

        char ch;
        //char sch;
        final int end = start + len;
        for (int c = start; c < end; c++) {

            ch = str[c];

            if (0x7f >= ch) {
                bytlen++;
            } else if (0x7ff >= ch) {
                bytlen += 2;
            } else {
                bytlen += 3;
            }

        }

        return bytlen;
    }

    /**
     * Static method to generate the UTF-8 representation of a Unicode character.
     * This particular code is taken from saxon (see http://saxon.sf.net).
     *
     * @param in  the Unicode character, or the high half of a surrogate pair
     * @param in2 the low half of a surrogate pair (ignored unless the first argument is in the
     *            range for a surrogate pair)
     * @param out an array of at least 4 bytes to hold the UTF-8 representation.
     * @return the number of bytes in the UTF-8 representation
     */
    public static int getUTF8Encoding(final char in, final char in2, final byte[] out) {
        // See Tony Graham, "Unicode, a Primer", page 92
        final int i = (int) in;
        if (i <= 0x7f) {
            out[0] = (byte) i;
            return 1;
        } else if (i <= 0x7ff) {
            out[0] = (byte) (0xc0 | ((in >> 6) & 0x1f));
            out[1] = (byte) (0x80 | (in & 0x3f));
            return 2;
        } else if (i >= 0xd800 && i <= 0xdbff) {
            // surrogate pair
            final int j = (int) in2;
            if (!(j >= 0xdc00 && j <= 0xdfff)) {
                throw new IllegalArgumentException("Malformed Unicode Surrogate Pair (" + i + "," + j + ")");
            }
            final byte xxxxxx = (byte) (j & 0x3f);
            final byte yyyyyy = (byte) (((i & 0x03) << 4) | ((j >> 6) & 0x0f));
            final byte zzzz = (byte) ((i >> 2) & 0x0f);
            final byte uuuuu = (byte) (((i >> 6) & 0x0f) + 1);
            out[0] = (byte) (0xf0 | ((uuuuu >> 2) & 0x07));
            out[1] = (byte) (0x80 | ((uuuuu & 0x03) << 4) | zzzz);
            out[2] = (byte) (0x80 | yyyyyy);
            out[3] = (byte) (0x80 | xxxxxx);
            return 4;
        } else if (i >= 0xdc00 && i <= 0xdfff) {
            // second half of surrogate pair - ignore it
            return 0;
        } else {
            out[0] = (byte) (0xe0 | ((in >> 12) & 0x0f));
            out[1] = (byte) (0x80 | ((in >> 6) & 0x3f));
            out[2] = (byte) (0x80 | (in & 0x3f));
            return 3;
        }
    }
}
