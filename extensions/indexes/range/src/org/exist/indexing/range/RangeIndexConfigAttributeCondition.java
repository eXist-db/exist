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
import org.exist.xquery.*;
import org.exist.xquery.modules.range.Lookup;
import org.exist.xquery.modules.range.RangeQueryRewriter;
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
public class RangeIndexConfigAttributeCondition extends RangeIndexConfigCondition {

    private final String attributeString;
    private final QName attribute;
    private final String value;
    private final RangeIndex.Operator operator;

    public RangeIndexConfigAttributeCondition(Element elem, NodePath parentPath) throws DatabaseConfigurationException {

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

    @Override
    public boolean matches(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE && ((Element)node).getAttribute(attributeString).equals(value)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean find(Predicate predicate) {

        Expression inner = this.getInnerExpression(predicate);

        if (inner instanceof GeneralComparison) {
            final GeneralComparison comparison = (GeneralComparison) inner;

            final Expression lhe = comparison.getLeft();
            final Expression rhe = comparison.getRight();
            if (lhe instanceof LocationStep && rhe instanceof LiteralValue) {
                final QName attribute = ((LocationStep)lhe).getTest().getName();
                final String value = ((LiteralValue) rhe).getValue().toString();
                final RangeIndex.Operator operator = RangeQueryRewriter.getOperator(inner);

                if (operator.equals(this.operator) && attribute.equals(this.attribute) && value.equals(value)) {
                    return true;
                }
            }
        }



        return false;
    }
}
