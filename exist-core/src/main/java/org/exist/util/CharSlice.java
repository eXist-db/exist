package org.exist.util;

import net.jcip.annotations.ThreadSafe;

import java.io.Serializable;
import java.io.Writer;

/**
 * This is an implementation of the JDK 1.4 CharSequence interface: it implements
 * a CharSequence as a view of an array. The implementation relies on the array
 * being immutable: as a minimum, the caller is required to ensure that the array
 * contents will not change so long as the CharSlice remains in existence.
 *
 * This class should be more efficient than String because it avoids copying the
 * characters unnecessarily.
 *
 * The methods in the class don't check their arguments. Incorrect arguments will
 * generally result in exceptions from lower-level classes.
 *
 */
@ThreadSafe
public final class CharSlice implements CharSequence, Serializable {

	private static final long serialVersionUID = -2668084569793755681L;

	private final char[] array;
    private final int offset;
    private final int len;

    public CharSlice(final char[] array) {
        this.array = array;
        this.offset = 0;
        this.len = array.length;
    }

    public CharSlice(final char[] array, final int offset, final int len) {
        this.array = array;
        this.offset = offset;
        this.len = len;
        if (offset + len > array.length) {
            throw new IndexOutOfBoundsException("offset(" + offset +
                    ") + length(" + len + ") > size(" + array.length + ')');
        }
    }

    /**
     * Returns the length of this character sequence.
     *
     * The length is the number of 16-bit Unicode characters in the sequence.
     *
     * @return  the number of characters in this sequence
     */
    @Override
    public int length() {
        return len;
    }

    /**
     * Returns the character at the specified index.  An index ranges from zero
     * to <pre>length() - 1</pre>.  The first character of the sequence is at
     * index zero, the next at index one, and so on, as for array
     * indexing.
     *
     * @param   index   the index of the character to be returned
     *
     * @return  the specified character
     *
     * @throws  java.lang.IndexOutOfBoundsException
     *          if the <pre>index</pre> argument is negative or not less than
     *          <pre>length()</pre>
     */
    @Override
    public char charAt(final int index) {
        return array[offset+index];
    }

    /**
     * Returns a new character sequence that is a subsequence of this sequence.
     * The subsequence starts with the character at the specified index and
     * ends with the character at index <pre>end - 1</pre>.  The length of the
     * returned sequence is <pre>end - start</pre>, so if <pre>start == end</pre>
     * then an empty sequence is returned.
     *
     * @param   start   the start index, inclusive
     * @param   end     the end index, exclusive
     *
     * @return  the specified subsequence
     *
     * @throws  java.lang.IndexOutOfBoundsException
     *          if <pre>start</pre> or <pre>end</pre> are negative,
     *          if <pre>end</pre> is greater than <pre>length()</pre>,
     *          or if <pre>start</pre> is greater than <pre>end</pre>
     */
    @Override
    public CharSequence subSequence(final int start, final int end) {
        return new CharSlice(array, offset + start, end - start);
    }

    /**
     * Convert to a string
     */
    @Override
    public String toString() {
        return new String(array, offset, len);
    }

    /**
     * Compare equality
     */

    @Override
    public boolean equals(final Object other) {
        return toString().equals(other);
    }

    /**
     * Generate a hash code
     */
    @Override
    public int hashCode() {
        // Same algorithm as String#hashCode(), but not cached
        final int end = offset + len;
        int h = 0;
        for (int i = offset; i < end; i++) {
            h = 31 * h + array[i];
        }
        return h;
    }

    /**
     * Get the index of a specific character in the sequence. Returns -1 if not found.
     * This method mimics {@link String#indexOf}
     * @param c the character to be found
     * @return the position of the first occurrence of that character, or -1 if not found.
     */
    public int indexOf(final char c) {
        final int end = offset + len;
        for (int i = offset; i < end; i++) {
            if (array[i] == c) {
                return i-offset;
            }
        }
        return -1;
    }

    /**
     * Returns a new character sequence that is a subsequence of this sequence.
     * Unlike subSequence, this is guaranteed to return a String.
     *
     * @param start the start offset of the substring
     * @param end the end offset of the substring
     *
     * @return the substring
     */
    public String substring(final int start, final int end) {
        return new String(array, offset + start, end - start);
    }

    /**
     * Append the contents to another array at a given offset. The caller is responsible
     * for ensuring that sufficient space is available.
     * @param destination the array to which the characters will be copied
     * @param destOffset the offset in the target array where the copy will start
     */
    public void copyTo(final char[] destination, final int destOffset) {
        System.arraycopy(array, offset, destination, destOffset, len);
    }

    /**
     * Write the value to a writer.
     *
     * @param writer the writer
     * @throws java.io.IOException if an error occurs whilst writing
     */
    public void write(final Writer writer) throws java.io.IOException {
        writer.write(array, offset, len);
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
