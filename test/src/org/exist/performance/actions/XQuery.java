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

import org.exist.performance.AbstractAction;
import org.exist.performance.Connection;
import org.exist.performance.Runner;
import org.exist.EXistException;
import org.exist.xmldb.XQueryService;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.Resource;
import org.w3c.dom.Element;

public class XQuery extends AbstractAction {

    private String query = null;
    private String collectionPath;
    private boolean retrieve = false;

    public void configure(Runner runner, Action parent, Element config) throws EXistException {
        super.configure(runner, parent, config);
        if (config.hasAttribute("query"))
            query = config.getAttribute("query");
        if (!config.hasAttribute("collection"))
            throw new EXistException(StoreFromFile.class.getName() + " requires an attribute 'collection'");
        collectionPath = config.getAttribute("collection");
        if (config.hasAttribute("retrieve-results")) {
            String option = config.getAttribute("retrieve-results");
            retrieve = option.equalsIgnoreCase("yes") || option.equalsIgnoreCase("true");
        }
    }

    public void execute(Connection connection) throws XMLDBException, EXistException {
        Collection collection = connection.getCollection(collectionPath);
        if (collection == null)
            throw new EXistException("collection " + collectionPath + " not found");
        XQueryService service = (XQueryService) collection.getService("XQueryService", "1.0");
        ResourceSet result = service.query(query);
        if (retrieve) {
            for (ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
                Resource r = i.nextResource();
                System.out.println(r.getContent());
            }
        }
    }


    public String getDescription() {
        return (description == null ? query : description);
    }
}