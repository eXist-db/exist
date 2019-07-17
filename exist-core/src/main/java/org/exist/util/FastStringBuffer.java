/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2003-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * This class is to large extents copied from Saxon 2003-01-21 (version ?).
 * See comment at the back about licensing for those parts.
 * 
 *  $Id$
 */
package org.exist.util;

import java.io.Serializable;
import java.io.Writer;

/**
 * A simple implementation of a class similar to StringBuffer. Unlike
 * StringBuffer it is not synchronized. It also offers the capability
 * to remove unused space. (This class could possibly be replaced by
 * StringBuilder in JDK 1.5, but using our own class gives more control.)
 */

public final class FastStringBuffer implements CharSequence, Serializable {

	private static final long serialVersionUID = -504264698052799896L;

	private char[] array;
    private int used = 0;

    public FastStringBuffer(int initialSize) {
        array = new char[initialSize];
    }

    /**
     * Append the contents of a String to the buffer
     * @param s the String to be appended
     */

    public void append(String s) {
        int len = s.length();
        ensureCapacity(len);
        s.getChars(0, len, array, used);
        used += len;
    }

    /**
     * Append the contents of a CharSlice to the buffer
     * @param s the String to be appended
     */

    public void append(CharSlice s) {
        int len = s.length();
        ensureCapacity(len);
        s.copyTo(array, used);
        used += len;
    }

    /**
     * Append the contents of a FastStringBuffer to the buffer
     * @param s the FastStringBuffer to be appended
     */

    public void append(FastStringBuffer s) {
        int len = s.length();
        ensureCapacity(len);
        s.getChars(0, len, array, used);
        used += len;
    }

    /**
     * Append the contents of a StringBuffer to the buffer
     * @param s the StringBuffer to be appended
     */

    public void append(StringBuffer s) {
        int len = s.length();
        ensureCapacity(len);
        s.getChars(0, len, array, used);
        used += len;
    }

    /**
     * Append the contents of a general CharSequence to the buffer
     * @param s the CharSequence to be appended
     */

    public void append(CharSequence s) {
        // Although we provide variants of this method for different subtypes, Java decides which to use based
        // on the static type of the operand. We want to use the right method based on the dynamic type, to avoid
        // creating objects and copying strings unnecessarily. So we do a dynamic dispatch.
        final int len = s.length();
        ensureCapacity(len);
        if (s instanceof CharSlice) {
            ((CharSlice)s).copyTo(array, used);
        } else if (s instanceof String) {
            ((String)s).getChars(0, len, array, used);
        } else if (s instanceof FastStringBuffer) {
            ((FastStringBuffer)s).getChars(0, len, array, used);
        } else if (s instanceof CompressedWhitespace) {
            ((CompressedWhitespace)s).uncompress(this);
            return;
        } else {
            s.toString().getChars(0, len, array, used);
        }
        used += len;
    }

    /**
     * Append the contents of a character array to the buffer
     * @param srcArray the array whose contents are to be added
     * @param start the offset of the first character in the array to be copied
     * @param length the number of characters to be copied
     */

    public void append(char[] srcArray, int start, int length) {
        ensureCapacity(length);
        System.arraycopy(srcArray, start, array, used, length);
        used += length;
    }

    /**
     * Append the entire contents of a character array to the buffer
     * @param srcArray the array whose contents are to be added
     */

    public void append(char[] srcArray) {
        final int length = srcArray.length;
        ensureCapacity(length);
        System.arraycopy(srcArray, 0, array, used, length);
        used += length;
    }

    /**
     * Append a character to the buffer
     * @param ch the character to be added
     */

    public void append(char ch) {
        ensureCapacity(1);
        array[used++] = ch;
    }

    /**
     * Append a wide character to the buffer (as a surrogate pair if necessary).
     *
     * @param ch the character
     */
    public void appendWideChar(final int ch) {
        if (ch > 0xffff) {
            append(XMLChar.highSurrogate(ch));
            append(XMLChar.lowSurrogate(ch));
        } else {
            append((char)ch);
        }
    }

    /**
     * Prepend a wide character to the buffer (as a surrogate pair if necessary).
     *
     * @param ch the character
     */
    public void prependWideChar(int ch) {
        if (ch > 0xffff) {
            insertCharAt(0, XMLChar.lowSurrogate(ch));
            insertCharAt(0, XMLChar.highSurrogate(ch));
        } else {
            insertCharAt(0, (char)ch);
        }
    }

    /**
     * Returns the length of this character sequence.  The length is the number
     * of 16-bit <code>char</code>s in the sequence.
     *
     * @return the number of <code>char</code>s in this sequence
     */
    public int length() {
        return used;
    }

    /**
     * Returns the <code>char</code> value at the specified index.  An index ranges from zero
     * to <code>length() - 1</code>.  The first <code>char</code> value of the sequence is at
     * index zero, the next at index one, and so on, as for array
     * indexing.
     *
     * If the <code>char</code> value specified by the index is a
     * <a href="Character.html#unicode">surrogate</a>, the surrogate
     * value is returned.
     *
     * @param index the index of the <code>char</code> value to be returned
     * @return the specified <code>char</code> value
     * @throws IndexOutOfBoundsException if the <code>index</code> argument is negative or not less than
     *                                   <code>length()</code>
     */
    public char charAt(int index) {
        if (index >= used) {
            throw new IndexOutOfBoundsException("" + index);
        }
        return array[index];
    }

    /**
     * Returns a new <code>CharSequence</code> that is a subsequence of this sequence.
     * The subsequence starts with the <code>char</code> value at the specified index and
     * ends with the <code>char</code> value at index <code>end - 1</code>.  The length
     * (in <code>char</code>s) of the
     * returned sequence is <code>end - start</code>, so if <code>start == end</code>
     * then an empty sequence is returned.
     *
     * @param start the start index, inclusive
     * @param end   the end index, exclusive
     * @return the specified subsequence
     * @throws IndexOutOfBoundsException if <code>start</code> or <code>end</code> are negative,
     *                                   if <code>end</code> is greater than <code>length()</code>,
     *                                   or if <code>start</code> is greater than <code>end</code>
     */
    public CharSequence subSequence(int start, int end) {
        return new CharSlice(array, start, end - start);
    }

    /**
     * Copies characters from this FastStringBuffer into the destination character
     * array.
     *
     * The first character to be copied is at index <code>srcBegin</code>;
     * the last character to be copied is at index <code>srcEnd-1</code>
     * (thus the total number of characters to be copied is
     * <code>srcEnd-srcBegin</code>). The characters are copied into the
     * subarray of <code>dst</code> starting at index <code>dstBegin</code>
     * and ending at index:
     * <blockquote><pre>
     *     dstbegin + (srcEnd-srcBegin) - 1
     * </pre></blockquote>
     *
     * @param      srcBegin   index of the first character in the string
     *                        to copy.
     * @param      srcEnd     index after the last character in the string
     *                        to copy.
     * @param      dst        the destination array.
     * @param      dstBegin   the start offset in the destination array.
     * @throws IndexOutOfBoundsException If any of the following
     *            is true:
     *            <ul><li><code>srcBegin</code> is negative.
     *            <li><code>srcBegin</code> is greater than <code>srcEnd</code>
     *            <li><code>srcEnd</code> is greater than the length of this
     *                string
     *            <li><code>dstBegin</code> is negative
     *            <li><code>dstBegin+(srcEnd-srcBegin)</code> is larger than
     *                <code>dst.length</code></ul>
     */
    public void getChars(int srcBegin, int srcEnd, char dst[], int dstBegin) {
        if (srcBegin < 0) {
            throw new StringIndexOutOfBoundsException(srcBegin);
        }
        if (srcEnd > used) {
            throw new StringIndexOutOfBoundsException(srcEnd);
        }
        if (srcBegin > srcEnd) {
            throw new StringIndexOutOfBoundsException(srcEnd - srcBegin);
        }
        System.arraycopy(array, srcBegin, dst, dstBegin, srcEnd - srcBegin);
    }

    /**
     * Get the index of the first character equal to a given value
     * @param ch the character to search for
     * @return the position of the first occurrence, or -1 if not found
     */
    public int indexOf(char ch) {
        for (int i=0; i<used; i++) {
            if (array[i] == ch) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Convert contents of the FastStringBuffer to a string
     */
    public String toString() {
        condense();
        return new String(array, 0, used);
    }

    /**
     * Set the character at a particular offset
     * @param index the index of the character to be set
     * @param ch the new character to overwrite the existing character at that location
     * @throws IndexOutOfBoundsException if {@code int < 0 || int >= length()}
     */
    public void setCharAt(int index, char ch) {
        if (index<0 || index>used) {
            throw new IndexOutOfBoundsException(""+index);
        }
        array[index] = ch;
    }

    /**
     * Insert a character at a particular offset
     * @param index the index of the character to be set
     * @param ch the new character to insert at that location
     * @throws IndexOutOfBoundsException if {@code int < 0 || int >= length()}
     */
    public void insertCharAt(int index, char ch) {
        if (index<0 || index>used) {
            throw new IndexOutOfBoundsException(""+index);
        }
        ensureCapacity(1);
        for (int i=used; i>index; i--) {
            array[i] = array[i-1];
        }
        used++;
        array[index] = ch;
    }

    /**
     * Remove a character at a particular offset
     * @param index the index of the character to be set
     * @throws IndexOutOfBoundsException if {@code int < 0 || int >= length()}
     */
    public void removeCharAt(int index) {
        if (index<0 || index>used) {
            throw new IndexOutOfBoundsException(""+index);
        }
        used--;
        for (int i=index; i<used; i++) {
            array[i] = array[i+1];
        }
    }

    /**
     * Set the length. If this exceeds the current length, this method is a no-op.
     * If this is less than the current length, characters beyond the specified point
     * are deleted.
     *
     * @param length the new length
     */
    public void setLength(int length) {
        if (length < 0 || length > used) {
            return;
        }
        used = length;
    }

    /**
     * Expand the character array if necessary to ensure capacity for appended data
     *
     * @param extra the extra capacity needed.
     */
    public void ensureCapacity(int extra) {
        if (used + extra > array.length) {
            int newlen = array.length * 2;
            if (newlen < used + extra) {
                newlen = used + extra*2;
            }
            char[] array2 = new char[newlen];
            System.arraycopy(array, 0, array2, 0, used);
            array = array2;
        }
    }

    /**
     * Remove surplus space from the array. This doesn't reduce the array to the minimum
     * possible size; it only reclaims space if it seems worth doing. Specifically, it
     * contracts the array if the amount of wasted space is more than 256 characters, or
     * more than half the allocated size.
     *
     * @return the character sequence.
     */
    public CharSequence condense() {
        if (array.length - used > 256 || array.length > used * 2) {
            char[] array2 = new char[used];
            System.arraycopy(array, 0, array2, 0, used);
            array = array2;
        }
        return this;
    }

    /**
     * Write the value to a writer.
     *
     * @param writer the writer
     *
     * @throws java.io.IOException if an error occurs whilst writing
     */
    public void write(final Writer writer) throws java.io.IOException {
        writer.write(array, 0, used);
    }

    /**
     * Diagnostic print of the contents of a CharSequence.
     *
     * @param in the character sequence
     *
     * @return the diagnostic print
     */
    public static String diagnosticPrint(CharSequence in) {
        final FastStringBuffer buff = new FastStringBuffer(in.length()*2);
        for (int i=0; i<in.length(); i++) {
            final char c = in.charAt(i);
            if (c > 32 && c < 127) {
                buff.append(c);
            } else {
                buff.append("\\u");
                for (int d=12; d>=0; d-=4) {
                    buff.append("0123456789abcdef".charAt((c>>d)&0xf));
                }
            }
        }
        return buff.toString();
    }
    
    //Quick copies from old eXist's FastStringBuffer
  
    /**
     *  Manefest constant: Suppress leading whitespace. This should be used when
     *  normalize-to-SAX is called for the first chunk of a multi-chunk output,
     *  or one following unsuppressed whitespace in a previous chunk.
     *
     *  see    #sendNormalizedSAXcharacters(char[],int,int,org.xml.sax.ContentHandler,int)
     */
    public final static int SUPPRESS_LEADING_WS = 0x01;

    /**
     *  Manefest constant: Suppress trailing whitespace. This should be used
     *  when normalize-to-SAX is called for the last chunk of a multi-chunk
     *  output; it may have to be or'ed with SUPPRESS_LEADING_WS.
     */
    public final static int SUPPRESS_TRAILING_WS = 0x02;

    /**
     *  Manefest constant: Suppress both leading and trailing whitespace. This
     *  should be used when normalize-to-SAX is called for a complete string.
     *  (I'm not wild about the name of this one. Ideas welcome.)
     *
     * see    sendNormalizedSAXcharacters(char[],int,int,org.xml.sax.ContentHandler,int)
     */
    public final static int SUPPRESS_BOTH
             = SUPPRESS_LEADING_WS | SUPPRESS_TRAILING_WS;
   
    /**
     *  Gets the normalizedString attribute of the FastStringBuffer object
     *
     *@param  mode  Description of the Parameter
     *@return       The normalizedString value
     */
    public String getNormalizedString( int mode ) {
        return getNormalizedString( new StringBuffer(toString()), mode ).toString();
    }


    /**
     *  Gets the normalizedString attribute of the FastStringBuffer object
     *
     *@param  sb    Description of the Parameter
     *@param  mode  Description of the Parameter
     *@return       The normalizedString value
     */    
    public StringBuffer getNormalizedString(StringBuffer sb, int mode ) {
    	//TODO : switch (mode)
    	return new StringBuffer(toString().trim());   	
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
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none
//

