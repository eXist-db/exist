/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.util;

import com.evolvedbinary.j8fu.Either;
import org.exist.util.io.FastByteArrayInputStream;
import org.xml.sax.InputSource;

import java.io.*;

import static com.evolvedbinary.j8fu.Either.Left;
import static com.evolvedbinary.j8fu.Either.Right;

public class StringInputSource extends InputSource {

    private final Either<byte[], String> source;

    /**
     * Creates a String Source from a string
     * the InputSource will be read using
     * {@link #getCharacterStream()}.
     *
     * @param string the input string.
     */
    public StringInputSource(final String string) {
        super();
        this.source = Right(string);
    }

    /**
     * Creates a String Source from bytes
     * the InputSource will be read using
     * {@link #getByteStream()}.
     *
     * @param string the input string.
     */
    public StringInputSource(final byte[] string) {
        super();
        this.source = Left(string);
    }

    @Override
    public Reader getCharacterStream() {
        if (source.isLeft()) {
            return null;
        } else {
            return new StringReader(source.right().get());
        }
    }

    /**
     * Set a character stream input.
     *
     * @param r the reader
     *
     * @throws IllegalStateException this class is immutable!
     */
    @Override
    public void setCharacterStream(final Reader r) {
        throw new IllegalStateException("StringInputSource is immutable");
    }

    @Override
    public InputStream getByteStream() {
        if (source.isLeft()) {
            return new FastByteArrayInputStream(source.left().get());
        } else {
            return null;
        }
    }

    /**
     * Set a byte stream input.
     *
     * @param is the input stream.
     *
     * @throws IllegalStateException this class is immutable!
     */
    @Override
    public void setByteStream(final InputStream is) {
        throw new IllegalStateException("StringInputSource is immutable");
    }
}
