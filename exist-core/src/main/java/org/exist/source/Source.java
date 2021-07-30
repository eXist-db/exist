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

import java.io.IOException;
import java.io.Reader;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;

import javax.annotation.Nullable;


/**
 * A general interface for access to external or internal sources.
 * This is mainly used as an abstraction for loading XQuery scripts
 * and modules, but can also be applied to other use cases.
 * 
 * @author wolf
 */
public interface Source {

    enum Validity {
        VALID,
        INVALID,
        UNKNOWN
    }

    /**
     * Returns a unique key to identify the source.
     *
     * @return unique key which identifies the source
     */
    long getKey();

    /**
     * Return the path to the source, or null if there is no path.
     *
     * @return the path, or null
     */
    @Nullable String path();

    /**
     * A user friendly string which identifies the type
     * of source.
     *
     * @return the type of the source
     */
    String type();
    
    /**
     * Is this source object still valid?
     * 
     * Returns {@link Validity#UNKNOWN} if the validity of
     * the source cannot be determined.
     * 
     * The {@link DBBroker} parameter is required by
     * some implementations as they have to read
     * resources from the database.
     * 
     * @param broker eXist-db broker
     * @return Validity of the source object
     */
    Validity isValid(DBBroker broker);
    
    /**
     * Checks if the source object is still valid
     * by comparing it to another version of the
     * same source. It depends on the concrete
     * implementation how the sources are compared.
     * 
     * Use this method if {@link #isValid(DBBroker)}
     * return {@link Validity#UNKNOWN}.
     * 
     * @param other source
     * @return Validity of the other source object
     */
    Validity isValid(Source other);
    
    /**
     * Returns a {@link Reader} to read the contents
     * of the source.
     *
     * @return the reader for reading the contents of the source
     * @throws IOException in case of an I/O error
     */
    Reader getReader() throws IOException;

    /**
     * Returns an {@link InputStream} to read the contents
     * of the source.
     *
     * @return the input stream for reading the contents of the source
     * @throws IOException in case of an I/O error
     */
    InputStream getInputStream() throws IOException;

    /**
     * Returns the content of the source as a String.
     *
     * @return the content of the source
     * @throws IOException in case of an I/O error
     */
    String getContent() throws IOException;

    /**
     * Returns the character encoding of the underlying source, or null if unknown.
     *
     * @return the character encoding, or null
     * @throws IOException in case of an I/O error
     */
    @Nullable Charset getEncoding() throws IOException;

    /**
     * Check: has subject requested permissions for this resource?
     *
     * @param subject The subject
     * @param perm The requested permissions
     * @throws PermissionDeniedException if user has not sufficient rights
     *
     * @deprecated These security checks only apply to {@link DBSource} and should be done by the caller
     */
    @Deprecated
    void validate(Subject subject, int perm) throws PermissionDeniedException;

    /**
     * Check if the source is an XQuery module. If it is, return a QName containing
     * the module prefix as local name and the module namespace as namespace URI.
     *
     * @return QName describing the module namespace, or null if the source is not a module.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Nullable QName isModule() throws IOException;

    @Override
    boolean equals(Object obj);

    /**
     * Get's a short string identifier
     * for the source.
     *
     * @return a short identifier, never null!
     */
    String shortIdentifier();

    /**
     * Get's the path, or a short string identifier
     * for the source.
     *
     * @return the result, never null!
     */
    String pathOrShortIdentifier();

    /**
     * Get's the path. or the content,
     * or a short string identifier for the source.
     *
     * @return the result, never null!
     */
    String pathOrContentOrShortIdentifier();
}
