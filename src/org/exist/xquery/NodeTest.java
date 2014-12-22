/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamReader;

/**
 * The interface <code>NodeTest</code>
 *
 */
public interface NodeTest {

    /**
     * The method <code>setType</code>
     *
     * @param nodeType an <code>int</code> value
     */
    public void setType(int nodeType);

    /**
     * The method <code>getType</code>
     *
     * @return an <code>int</code> value
     */
    public int getType();

    /**
     * The method <code>matches</code>
     *
     * @param proxy a <code>NodeProxy</code> value
     * @return a <code>boolean</code> value
     */
    public boolean matches(NodeProxy proxy);

    /**
     * The method <code>matches</code>
     *
     * @param node a <code>Node</code> value
     * @return a <code>boolean</code> value
     */
    public boolean matches(Node node);

    public boolean matches(XMLStreamReader reader);

    public boolean matches(QName name);

    /**
     * The method <code>isWildcardTest</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean isWildcardTest();

    /**
     * The method <code>getName</code>
     *
     * @return a <code>QName</code> value
     */
    public QName getName();
}
