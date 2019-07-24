/*
 * 
 * The contents of this [class] are subject to the Netscape Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/NPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is mozilla.org code.
 *
 * The Initial Developer of the Original Code is Netscape
 * Communications Corporation.  Portions created by Netscape are
 * Copyright (C) 1999 Netscape Communications Corporation. All
 * Rights Reserved.
 *
 * Contributor(s):
 */
package org.exist.util;

    /**
     * Byte to text encoder using base 64 encoding. To create a base 64
     * encoding of a byte stream call {@link #translate} for every
     * sequence of bytes and {@link #getCharArray} to mark closure of
     * the byte stream and retrieve the text presentation.
     *
     * @author Based on code from the Mozilla Directory SDK
     */
public final class Base64Encoder {

    private FastStringBuffer out = new FastStringBuffer(256);

    private int buf = 0;                     // a 24-bit quantity

    private int buf_bytes = 0;               // how many octets are set in it

    private char line[] = new char[74];      // output buffer

    private int line_length = 0;             // output buffer fill pointer

    //static private final byte crlf[] = "\r\n".getBytes();

    private static final char map[] = {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', // 0-7
        'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', // 8-15
        'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', // 16-23
        'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', // 24-31
        'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', // 32-39
        'o', 'p', 'q', 'r', 's', 't', 'u', 'v', // 40-47
        'w', 'x', 'y', 'z', '0', '1', '2', '3', // 48-55
        '4', '5', '6', '7', '8', '9', '+', '/', // 56-63
    };


    private void encode_token() {
        final int i = line_length;
        line[i] = map[0x3F & (buf >> 18)];   // sextet 1 (octet 1)
        line[i + 1] = map[0x3F & (buf >> 12)];   // sextet 2 (octet 1 and 2)
        line[i + 2] = map[0x3F & (buf >> 6)];    // sextet 3 (octet 2 and 3)
        line[i + 3] = map[0x3F & buf];           // sextet 4 (octet 3)
        line_length += 4;
        buf = 0;
        buf_bytes = 0;
    }


    private void encode_partial_token() {
        final int i = line_length;
        line[i] = map[0x3F & (buf >> 18)];   // sextet 1 (octet 1)
        line[i + 1] = map[0x3F & (buf >> 12)];   // sextet 2 (octet 1 and 2)

        if (buf_bytes == 1)
            {line[i + 2] = '=';}
        else
            {line[i + 2] = map[0x3F & (buf >> 6)];}  // sextet 3 (octet 2 and 3)

        if (buf_bytes <= 2)
            {line[i + 3] = '=';}
        else
            {line[i + 3] = map[0x3F & buf];}         // sextet 4 (octet 3)
        line_length += 4;
        buf = 0;
        buf_bytes = 0;
    }


    private void flush_line() {
        out.append(line, 0, line_length);
        line_length = 0;
    }


    /**
     * Given a sequence of input bytes, produces a sequence of output bytes
     * using the base64 encoding.  If there are bytes in `out' already, the
     * new bytes are appended, so the caller should do `out.setLength(0)'
     * first if that's desired.
     *
     * @param in the input array
     */
    public final void translate(byte[] in) {
        final int in_length = in.length;

        for (int i = 0; i < in_length; i++) {
            if (buf_bytes == 0)
                {buf = (buf & 0x00FFFF) | (in[i] << 16);}
            else if (buf_bytes == 1)
                {buf = (buf & 0xFF00FF) | ((in[i] << 8) & 0x00FFFF);}
            else
                {buf = (buf & 0xFFFF00) | (in[i] & 0x0000FF);}

            if ((++buf_bytes) == 3) {
                encode_token();
                if (line_length >= 72) {
                    flush_line();
                }
            }

            if (i == (in_length - 1)) {
                if ((buf_bytes > 0) && (buf_bytes < 3))
                    {encode_partial_token();}
                if (line_length > 0)
                    {flush_line();}
            }
        }

        for (int i = 0; i < line.length; i++)
            line[i] = 0;
    }


    public char[] getCharArray() {
        char[] ch;

        if (buf_bytes != 0)
            {encode_partial_token();}
        flush_line();
        for (int i = 0; i < line.length; i++)
            line[i] = 0;
        ch = new char[out.length()];
        if (out.length() > 0)
            {out.getChars(0, out.length(), ch, 0);}
        return ch;
    }
    
    public void reset() {
    	out.setLength(0);
    }
}