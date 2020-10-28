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
package org.exist.source;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A simple source object wrapping around a single string value.
 * 
 * @author wolf
 */
public class StringSource extends AbstractSource {

    private final String content;
    
    public StringSource(final String content) {
        super(hashKey(content));
        this.content = content;
    }

    @Override
    public String path() {
        return null;
    }

    @Override
    public String type() {
        return "String";
    }

    @Override
    public Validity isValid(final DBBroker broker) {
        return Source.Validity.VALID;
    }

    @Override
    public Validity isValid(final Source other) {
        return Source.Validity.VALID;
    }

    @Override
    public Reader getReader() {
        return new StringReader(content);
    }

    @Override
    public InputStream getInputStream() {
        return new UnsynchronizedByteArrayInputStream(content.getBytes(UTF_8));
    }

    @Override
    public String getContent() {
        return content;
    }

	@Override
	public void validate(final Subject subject, final int perm) {
		// TODO protected?
	}

    @Override
    public int hashCode() {
        return content.hashCode();
    }
}
