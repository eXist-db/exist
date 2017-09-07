/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
 */
package org.exist.xquery;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.ReferenceNode;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamReader;

public class NameTest extends TypeTest {

    protected final QName nodeName;

    public NameTest(final int type, final QName name) throws XPathException {
        super(type);

        if(name.isValid(true) != QName.Validity.VALID.val) {
            throw new XPathException(ErrorCodes.XPST0081, "No namespace defined for prefix " + name.getStringValue());
        }

        nodeName = name;
    }

    @Override
    public QName getName() {
        return nodeName;
    }

    @Override
    public boolean matches(final NodeProxy proxy) {
        Node node = null;
        short type = proxy.getNodeType();
        if (proxy.getType() == Type.ITEM) {
            node = proxy.getNode();
            type = node.getNodeType();
        }
        if (!isOfType(type)) {
            return false;
        }
        if (node == null) {
            node = proxy.getNode();
        }
        return matchesName(node);
    }

    @Override
    public boolean matches(final Node other) {
        if (other.getNodeType() == NodeImpl.REFERENCE_NODE) {
            return matches(((ReferenceNode) other).getReference());
        }

        if (!isOfType(other.getNodeType())) {
            return false;
        }

        return matchesName(other);
    }

    @Override
    public boolean matches(final QName name) {
        return nodeName.matches(name);
    }

    public boolean matchesName(final Node other) {
        if (other.getNodeType() == NodeImpl.REFERENCE_NODE) {
            return matchesName(((ReferenceNode) other).getReference().getNode());
        }

        if (nodeName == QName.WildcardQName.getInstance()) {
            return true;
        }

        if (!(nodeName instanceof QName.WildcardNamespaceURIQName)) {
            String otherNs = other.getNamespaceURI();
            if (otherNs == null) {
                otherNs = XMLConstants.NULL_NS_URI;
            }
            if (!nodeName.getNamespaceURI().equals(otherNs)) {
                return false;
            }
        }

        if (!(nodeName instanceof QName.WildcardLocalPartQName)) {
            if (other.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                return nodeName.getLocalPart().equals(((ProcessingInstruction)other).getTarget());
            } else {
                return nodeName.getLocalPart().equals(other.getLocalName());
            }
        }

        return true;
    }

    @Override
    public boolean matches(final XMLStreamReader reader) {
        final int ev = reader.getEventType();
        if (!isOfEventType(ev)) {
            return false;
        }

        if (nodeName == QName.WildcardQName.getInstance()) {
            return true;
        }

        switch (ev) {
            case XMLStreamReader.START_ELEMENT:
                if (!(nodeName instanceof QName.WildcardNamespaceURIQName)) {
                    String readerNs = reader.getNamespaceURI();
                    if (readerNs == null) {
                        readerNs = XMLConstants.NULL_NS_URI;
                    }
                    if (!nodeName.getNamespaceURI().equals(readerNs)) {
                        return false;
                    }
                }

                if (!(nodeName instanceof QName.WildcardLocalPartQName)) {
                    return nodeName.getLocalPart().equals(reader.getLocalName());
                }
                break;

            case XMLStreamReader.PROCESSING_INSTRUCTION:
                if (!(nodeName instanceof QName.WildcardLocalPartQName)) {
                    return nodeName.getLocalPart().equals(reader.getPITarget());
                }
                break;
        }
        return true;
    }

    @Override
    public boolean isWildcardTest() {
        return nodeName instanceof QName.PartialQName;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof NameTest) {
            final NameTest other = (NameTest) obj;
            return other.nodeType == nodeType && other.nodeName.equals(nodeName);
        }
        return false;
    }

    public void dump(final ExpressionDumper dumper) {
        dumper.display(nodeName.getStringValue());
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();

        if (nodeName.getPrefix() != null) {
            result.append(nodeName.getPrefix());
            result.append(":");
        } else if (!(nodeName instanceof QName.WildcardNamespaceURIQName)) {
            result.append("{");
            result.append(nodeName.getNamespaceURI());
            result.append("}");
        }

        result.append(nodeName.getLocalPart());

        return result.toString();
    }
}
