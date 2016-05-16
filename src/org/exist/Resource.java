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
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Resource {

    String COLLECTION_ID = "eXist-collection-id";

    String NAME = "eXist:file-name";

    String PATH = "eXist:file-path";

    String OWNER = "eXist:owner";

    String TYPE = "eXist:meta-type";

    String CREATED = "eXist:created";

    String LAST_MODIFIED = "eXist:last-modified";
    
    XmldbURI getURI();
    
    Permission getPermissions();
    
    ResourceMetadata getMetadata();

    boolean isCollection();

}
