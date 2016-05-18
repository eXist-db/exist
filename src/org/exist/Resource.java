/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist;

import org.exist.security.Permission;
import org.exist.xmldb.XmldbURI;

/**
 * Interface of common accessors for any generic database resource
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author Adam Retter
 */
public interface Resource {

    /**
     * Get the URI of the resource within the database
     *
     * @return The URI of the resource in the database
     */
    XmldbURI getURI();

    /**
     * Get the Permissions of the resource
     *
     * @return The current permissions assigned to the
     * resource
     */
    Permission getPermissions();

    /**
     * Get the metadata associated
     * with the resource
     *
     * @return The metadata associated with the resource
     */
    ResourceMetadata getMetadata();
}
