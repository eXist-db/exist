/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
package org.exist.performance.actions;

import org.exist.EXistException;
import org.exist.performance.AbstractAction;
import org.exist.performance.Connection;
import org.exist.performance.Runner;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.EXistResource;
import org.w3c.dom.Element;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

public class StoreInline extends AbstractAction {

    private String collectionPath;
    private String name;
    private String content;
    private MimeType type = MimeType.XML_TYPE;

    public void configure(Runner runner, Action parent, Element config) throws EXistException {
        super.configure(runner, parent, config);

        if (!config.hasAttribute("collection"))
            throw new EXistException(StoreInline.class.getName() + " requires an attribute 'collection'");
        collectionPath = config.getAttribute("collection");

        if (!config.hasAttribute("name"))
            throw new EXistException(StoreInline.class.getName() + " requires an attribute 'name'");
        name = config.getAttribute("name");

        if (config.hasAttribute("type")) {
            type = MimeTable.getInstance().getContentType(config.getAttribute("type"));
            if (type == null)
                type = MimeType.XML_TYPE;
        }
        content = type.isXMLType() ? getContent(config) : getContentValue(config);
    }

    public void execute(Connection connection) throws XMLDBException, EXistException {
        Collection collection = connection.getCollection(collectionPath);
        if (collection == null)
            throw new EXistException("Collection not found: " + collectionPath);
        Resource resource = collection.createResource(name, type.getXMLDBType());
        ((EXistResource)resource).setMimeType(type.getName());
        resource.setContent(content);
        collection.storeResource(resource);
    }
}
