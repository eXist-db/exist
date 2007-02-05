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
package org.exist.performance;

import org.exist.performance.actions.Action;
import org.exist.EXistException;
import org.exist.util.serializer.DOMSerializer;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.apache.log4j.Logger;

import javax.xml.transform.TransformerException;
import java.io.StringWriter;
import java.util.Properties;

public abstract class AbstractAction implements Action {

    protected final static Logger LOG = Logger.getLogger(Action.class);

    protected Action parent = null;

    protected String description = null;

    protected String id;

    public void configure(Runner runner, Action parent, Element config) throws EXistException {
        this.parent = parent;
        if (config.hasAttribute("description"))
            description = config.getAttribute("description");
        if (config.hasAttribute("id"))
            id = config.getAttribute("id");
        else
            id = "A" + runner.getNextId();
    }

    public String getId() {
        return id;
    }

    public Action getParent() {
        return parent;
    }

    public String getDescription() {
        return description;
    }

    public String getLastResult() {
        return null;
    }

    public static boolean getBooleanValue(Element config, String name, boolean defaultValue) {
        if (config.hasAttribute(name)) {
            String val = config.getAttribute(name);
            return val.equalsIgnoreCase("true") || val.equalsIgnoreCase("yes");
        }
        return defaultValue;
    }

    public static String getContent(Element config) throws EXistException {
        NodeList children = config.getChildNodes();
        Element root = null;
        for (int i = 0;  i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                root = (Element) node;
                break;
            }
        }
        if (root == null)
            throw new EXistException("no content element found for " + config.getNodeName());
        StringWriter writer = new StringWriter();
        DOMSerializer serializer = new DOMSerializer(writer, new Properties());
        try {
            serializer.serialize(root);
        } catch (TransformerException e) {
            throw new EXistException("exception while serializing content: " + e.getMessage(), e);
        }
        return writer.toString();
    }
}
