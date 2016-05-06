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
import java.io.Reader;
import java.io.InputStream;

import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;


/**
 * A general interface for access to external or internal sources.
 * This is mainly used as an abstraction for loading XQuery scripts
 * and modules, but can also be applied to other use cases.
 * 
 * @author wolf
 */
public interface Source {

    public final static int VALID = 1;
    public final static int INVALID = -1;
    public final static int UNKNOWN = 0;

    String path();

    String type();

    /**
     * Returns a unique key to identify the source, usually
     * an URI.
     * 
     */
    public Object getKey();
    
    /**
     * Is this source object still valid?
     * 
     * Returns {@link #UNKNOWN} if the validity of
     * the source cannot be determined.
     * 
     * The {@link DBBroker} parameter is required by
     * some implementations as they have to read
     * resources from the database.
     * 
     * @param broker
     */
    public int isValid(DBBroker broker);
    
    /**
     * Checks if the source object is still valid
     * by comparing it to another version of the
     * same source. It depends on the concrete
     * implementation how the sources are compared.
     * 
     * Use this method if {@link #isValid(DBBroker)}
     * return {@link #UNKNOWN}.
     * 
     * @param other
     */
    public int isValid(Source other);
    
    /**
     * Returns a {@link Reader} to read the contents
     * of the source.
     * 
     * @throws IOException
     */
    public Reader getReader() throws IOException;

    public InputStream getInputStream() throws IOException;

    public String getContent() throws IOException;
    
    /**
     * Set a timestamp for this source. This is used
     * by {@link org.exist.storage.XQueryPool} to
     * check if a source has timed out.
     * 
     * @param timestamp
     */
    public void setCacheTimestamp(long timestamp);
    
    public long getCacheTimestamp();
    
    /**
     * Check: has subject requested permissions for this resource?
     *
     * @param  subject The subject
     * @param  perm The requested permissions
     */
    public void validate(Subject subject, int perm) throws PermissionDeniedException;

    public QName isModule() throws IOException;

}
