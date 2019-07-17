/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2003 The eXist Project
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
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamReader;

/**
 * The class <code>AnyNodeTest</code>
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 *
 */
public class AnyNodeTest implements NodeTest {

    /* (non-Javadoc)
     * @see org.exist.xquery.NodeTest#getName()
     */
    public QName getName() {
        return null;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.NodeTest#isWildcardTest()
     */
    public boolean isWildcardTest() {
        return true;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.NodeTest#matches(org.w3c.dom.Node)
     */
    public boolean matches(Node node) {
        return (node.getNodeType() != Node.ATTRIBUTE_NODE);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.NodeTest#matches(org.exist.dom.persistent.NodeProxy)
     */
    public boolean matches(NodeProxy proxy) {
        final int type = proxy.getType();
        if (type == Type.ITEM || type == Type.NODE) {		
            if (proxy.getNodeType() != NodeProxy.UNKNOWN_NODE_TYPE) {
                return matches(proxy.getNode());
            }
            return proxy.getNodeType() != Node.ATTRIBUTE_NODE;
        } else if (type == Type.ATTRIBUTE &&
            proxy.getNodeType() == Node.ATTRIBUTE_NODE) {
            return true;
        } else {
            return type != Node.ATTRIBUTE_NODE;
        }
    }

    public boolean matches(XMLStreamReader reader) {
        return reader.getEventType() != XMLStreamReader.ATTRIBUTE;
    }

    public boolean matches(QName name) {
    	return false;
    }

    public void dump(ExpressionDumper dumper) {
        if (dumper.verbosity() > 1) {
            dumper.display("node()"); 
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return "node()";
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.NodeTest#setType(int)
     */
    public void setType(int nodeType) {
        //Nothing to do
    }

    public int getType() {
        return Type.NODE;
    }
}
