/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.indexing.range;

import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;

/**
 *
 * A condition that can be defined for complex range config elements.
 * As of now, limited to attribute equality.
 *
 * @author Marcel Schaeben
 */
public class RangeIndexConfigCondition {

    public String getValue() {
        return value;
    }
    public QName getAttribute() {
        return attribute;
    }
    public RangeIndex.Operator getOperator() { return operator; }

    private final String attributeString;
    private final QName attribute;
    private final String value;
    private final RangeIndex.Operator operator;

    public RangeIndexConfigCondition(Element elem, NodePath parentPath) throws DatabaseConfigurationException {

        this.operator = RangeIndex.Operator.EQ;

        this.attributeString = elem.getAttribute("attribute");
        if (parentPath.getLastComponent().getNameType() == ElementValue.ATTRIBUTE) {
            throw new DatabaseConfigurationException("Range index module: Attribute condition cannot be defined for an attribute:" + parentPath.toString());
        }
        if (this.attributeString == null || this.attributeString.length() == 0) {
            throw new DatabaseConfigurationException("Range index module: Empty or no attribute qname in condition");
        } else {
            this.attribute = new QName(QName.extractLocalName(this.attributeString), XMLConstants.NULL_NS_URI, QName.extractPrefix(this.attributeString), ElementValue.ATTRIBUTE);
            this.value = elem.getAttribute("value");
        }

    }

    /**
     * Test if a node matches this condition. Used by the indexer.
     * @param node The node to test.
     * @return true if the node is an element node and an attribute matches this condition.
     */
    public boolean matches(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE && ((Element)node).getAttribute(attributeString).equals(value)) {
            return true;
        }

        return false;
    }

    /**
     * Test if an expression defined by the arguments matches this condition. Used by the query rewriter.
     * @param lhe The QName of the attribute to test
     * @param rhe The expected value of the attribute
     * @param operator The operator of the comparison expression (defaults to equals for now)
     * @return
     */
    public boolean find(QName lhe, String rhe, RangeIndex.Operator operator) {
        if (operator.equals(this.operator) && lhe.equals(this.attribute) && rhe.equals(value)) {
                return true;
        }

        return false;
    }
}
