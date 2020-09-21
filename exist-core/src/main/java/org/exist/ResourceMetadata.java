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
package org.exist;

/**
 * Interface of common accessors for the metadata of any generic database resource
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author Adam Retter
 *
 * @deprecated Will be removed in eXist-db 6.0.0
 */
@Deprecated
public interface ResourceMetadata {

    /**
     * Get the creation time of the resource
     *
     * @return the difference, measured in milliseconds, between the time when
     * the resource was created and midnight, January 1, 1970 UTC.
     *
     * @deprecated Will be removed in eXist-db 6.0.0
     */
    @Deprecated
    long getCreated();
}