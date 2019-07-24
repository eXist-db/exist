/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.util.io.FastByteArrayInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A simple source object wrapping around a single string value.
 * 
 * @author wolf
 */
public class StringSource extends AbstractSource {

    private String data;
    
    public StringSource(String content) {
        this.data = content;
    }

    @Override
    public String path() {
        return type();
    }

    @Override
    public String type() {
        return "String";
    }

    /* (non-Javadoc)
             * @see org.exist.source.Source#getKey()
             */
    public Object getKey() {
        return data;
    }

    @Override
    public Validity isValid(final DBBroker broker) {
        return Source.Validity.VALID;
    }

    @Override
    public Validity isValid(final Source other) {
        return Source.Validity.VALID;
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#getReader()
     */
    public Reader getReader() throws IOException {
        return new StringReader(data);
    }

    public InputStream getInputStream() throws IOException {
        return new FastByteArrayInputStream(data.getBytes(UTF_8));
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#getContent()
     */
    public String getContent() throws IOException {
        return data;
    }

	@Override
	public void validate(Subject subject, int perm) throws PermissionDeniedException {
		// TODO protected?
	}
}
