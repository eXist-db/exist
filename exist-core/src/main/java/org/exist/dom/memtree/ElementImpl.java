/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.dom.memtree;

import org.exist.Namespaces;
import org.exist.dom.NamedNodeMapImpl;
import org.exist.dom.NodeListImpl;
import org.exist.dom.QName;
import org.exist.dom.QName.IllegalQNameException;
import org.exist.storage.ElementValue;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Expression;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.exist.dom.QName.Validity.ILLEGAL_FORMAT;


public class ElementImpl extends NodeImpl implements Element {

    public ElementImpl(final DocumentImpl doc, final int nodeNumber) {
        this(null, doc, nodeNumber);
    }

    public ElementImpl(final Expression expression, final DocumentImpl doc, final int nodeNumber) {
        super(expression, doc, nodeNumber);
    }

    @Override
    public String getTagName() {
        return getNodeName();
    }

    @Override
    public boolean hasChildNodes() {
        return (nodeNumber + 1) < document.size && document.treeLevel[nodeNumber + 1] > document.treeLevel[nodeNumber];
    }

    @Override
    public Node getFirstChild() {
        final short level = document.treeLevel[nodeNumber];
        final int nextNode = nodeNumber + 1;
        if(nextNode < document.size && document.treeLevel[nextNode] > level) {
            return document.getNode(nextNode);
        }
        return null;
    }

    @Override
    @Nonnull
    public NodeList getChildNodes() {
        final NodeListImpl nl = new NodeListImpl(1);  // nil elements are rare, so we use 1 here
        int nextNode = document.getFirstChildFor(nodeNumber);
        while(nextNode > nodeNumber) {
            final Node n = document.getNode(nextNode);
            if(n.getNodeType() != Node.ATTRIBUTE_NODE) {
                nl.add(n);
            }
            nextNode = document.next[nextNode];
        }
        return nl;
    }

    private int getChildCount() {
        return document.getChildCountFor(nodeNumber);
    }

    @Override
    public boolean hasAttributes() {
        return document.alpha[nodeNumber] > -1 || document.alphaLen[nodeNumber] > -1;
    }

    @Override
    @Nonnull
    public String getAttribute(final String name) {
        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while(attr < document.nextAttr && document.attrParent[attr] == nodeNumber) {
                final QName attrQName = document.attrName[attr];
                if(attrQName.getStringValue().equals(name)) {
                    return document.attrValue[attr];
                }
                ++attr;
            }
        }
        if(name.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
            int ns = document.alphaLen[nodeNumber];
            if(-1 < ns) {
                while(ns < document.nextNamespace && document.namespaceParent[ns] == nodeNumber) {
                    final QName nsQName = document.namespaceCode[ns];
                    if(nsQName.getStringValue().equals(name)) {
                        return nsQName.getNamespaceURI();
                    }
                    ++ns;
                }
            }
        }
        return "";
    }

    @Override
    public void setAttribute(final String name, final String value) throws DOMException {
        final QName qname;
        try {
            if(document.context != null) {
                qname = QName.parse(document.context, name);
            } else {
                qname = new QName(name);
            }
        } catch (final IllegalQNameException e) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, e.getMessage());
        }

        // check the QName is valid for use
        if(qname.isValid(false) != QName.Validity.VALID.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "name is invalid");
        }

        setAttribute(qname, value, qn -> getAttributeNode(qn.getLocalPart()));
    }

    @Override
    public void setAttributeNS(final String namespaceURI, final String qualifiedName, final String value) throws DOMException {
        final QName qname;
        try {
            if(document.context != null) {
                qname = QName.parse(document.context, qualifiedName, namespaceURI);
            } else {
                qname = QName.parse(namespaceURI, qualifiedName);
            }
        } catch (final IllegalQNameException e) {
            final short errCode;
            if(e.getValidity() == ILLEGAL_FORMAT.val || (e.getValidity() & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
                errCode = DOMException.NAMESPACE_ERR;
            } else {
                errCode = DOMException.INVALID_CHARACTER_ERR;
            }
            throw new DOMException(errCode, "qualified name is invalid");
        }

        // check the QName is valid for use
        final byte validity = qname.isValid(false);
        if((validity & QName.Validity.INVALID_LOCAL_PART.val) == QName.Validity.INVALID_LOCAL_PART.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "qualified name is invalid");
        } else if((validity & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "qualified name is invalid");
        }

        setAttribute(qname, value, qn -> getAttributeNodeNS(qn.getNamespaceURI(), qn.getLocalPart()));
    }

    private void setAttribute(final QName name, final String value, final Function<QName, Attr> getFn) {
        final Attr existingAttr = getFn.apply(name);
        if(existingAttr != null) {

            // update an existing attribute
            existingAttr.setValue(value);

        } else {

            // create a new attribute

            final int lastNode = document.getLastNode();
            document.addAttribute(lastNode, name, value, AttrImpl.ATTR_CDATA_TYPE);
        }
    }

    @Override
    public void removeAttribute(final String name) throws DOMException {
    }

    @Override
    public NamedNodeMap getAttributes() {
        final NamedNodeMapImpl map = new NamedNodeMapImpl(document, true);
        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while(attr < document.nextAttr && document.attrParent[attr] == nodeNumber) {
                map.setNamedItem(new AttrImpl(getExpression(), document, attr));
                ++attr;
            }
        }
        // add namespace declarations attached to this element
        int ns = document.alphaLen[nodeNumber];
        if(ns < 0) {
            return (map);
        }
        while(ns < document.nextNamespace && document.namespaceParent[ns] == nodeNumber) {
            final NamespaceNode node = new NamespaceNode(getExpression(), document, ns);
            map.setNamedItem(node);
            ++ns;
        }
        return map;
    }

    @Override
    public Attr getAttributeNode(final String name) {
        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while(attr < document.nextAttr && document.attrParent[attr] == nodeNumber) {
                final QName attrQName = document.attrName[attr];
                if(attrQName.getStringValue().equals(name)) {
                    return new AttrImpl(getExpression(), document, attr);
                }
                ++attr;
            }
        }
        if(name.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
            int ns = document.alphaLen[nodeNumber];
            if(-1 < ns) {
                while(ns < document.nextNamespace && document.namespaceParent[ns] == nodeNumber) {
                    final QName nsQName = document.namespaceCode[ns];
                    if(nsQName.getStringValue().equals(name)) {
                        return new NamespaceNode(getExpression(), document, ns);
                    }
                    ++ns;
                }
            }
        }
        return null;
    }

    @Override
    public Attr setAttributeNode(final Attr newAttr) throws DOMException {
        return setAttributeNode(newAttr, qname -> getAttributeNode(qname.getLocalPart()));
    }

    private Attr setAttributeNode(final Attr newAttr, final Function<QName, Attr> getFn) {
        final QName attrName = new QName(newAttr.getLocalName(), newAttr.getNamespaceURI(), newAttr.getPrefix(), ElementValue.ATTRIBUTE);
        final Attr existingAttr = getFn.apply(attrName);

        if(existingAttr != null) {
            if(existingAttr.equals(newAttr)) {
                return newAttr;
            }

            // update an existing attribute
            existingAttr.setValue(newAttr.getValue());

            return existingAttr;

        } else {

            // create a new attribute

            final int lastNode = document.getLastNode();
            final int attrNodeNum = document.addAttribute(lastNode, attrName, newAttr.getValue(), AttrImpl.ATTR_CDATA_TYPE);
            return new AttrImpl(getExpression(), document, attrNodeNum);
        }
    }

    @Override
    public Attr removeAttributeNode(final Attr oldAttr) throws DOMException {
        return null;
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result) throws XPathException {
        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while(attr < document.nextAttr && document.attrParent[attr] == nodeNumber) {
                final AttrImpl attrib = new AttrImpl(getExpression(), document, attr);
                if(test.matches(attrib)) {
                    result.add(attrib);
                }
                ++attr;
            }
        }
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result) throws XPathException {
        final int treeLevel = document.treeLevel[nodeNumber];
        int nextNode = nodeNumber;
        NodeImpl n = document.getNode(nextNode);
        n.selectAttributes(test, result);
        while(++nextNode < document.size && document.treeLevel[nextNode] > treeLevel) {
            n = document.getNode(nextNode);
            if(n.getNodeType() == Node.ELEMENT_NODE) {
                n.selectAttributes(test, result);
            }
        }
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result) throws XPathException {
        int nextNode = document.getFirstChildFor(nodeNumber);
        while(nextNode > nodeNumber) {
            final NodeImpl n = document.getNode(nextNode);
            if(test.matches(n)) {
                result.add(n);
            }
            nextNode = document.next[nextNode];
        }
    }

    public NodeImpl getFirstChild(final NodeTest test) throws XPathException {
        final ValueSequence seq = new ValueSequence();
        selectChildren(test, seq);
        return seq.isEmpty() ? null : seq.get(0);
    }

    @Override
    public void selectDescendants(final boolean includeSelf, final NodeTest test, final Sequence result)
        throws XPathException {
        final int treeLevel = document.treeLevel[nodeNumber];
        int nextNode = nodeNumber;

        if(includeSelf) {
            final NodeImpl n = document.getNode(nextNode);
            if(test.matches(n)) {
                result.add(n);
            }
        }

        while(++nextNode < document.size && document.treeLevel[nextNode] > treeLevel) {
            final NodeImpl n = document.getNode(nextNode);
            if(test.matches(n)) {
                result.add(n);
            }
        }
    }

    @Override
    @Nonnull
    public NodeList getElementsByTagName(final String name) {
        if(name != null && name.equals(QName.WILDCARD)) {
            return getElementsByTagName(new QName.WildcardLocalPartQName(XMLConstants.DEFAULT_NS_PREFIX));
        } else {
            final QName qname;
            try {
                if (document.getContext() != null) {
                    qname = QName.parse(document.context, name);
                } else {
                    qname = new QName(name);
                }
            } catch (final IllegalQNameException e) {
                throw new DOMException(DOMException.INVALID_CHARACTER_ERR, e.getMessage());
            }
            return getElementsByTagName(qname);
        }
    }

    @Override
    @Nonnull
    public NodeList getElementsByTagNameNS(final String namespaceURI, final String localName) {
        final boolean wildcardNS = namespaceURI != null && namespaceURI.equals(QName.WILDCARD);
        final boolean wildcardLocalPart = localName != null && localName.equals(QName.WILDCARD);

        if(wildcardNS && wildcardLocalPart) {
            return getElementsByTagName(QName.WildcardQName.getInstance());
        } else if(wildcardNS) {
            return getElementsByTagName(new QName.WildcardNamespaceURIQName(localName));
        } else if(wildcardLocalPart) {
            return getElementsByTagName(new QName.WildcardLocalPartQName(namespaceURI));
        } else {
            final QName qname;
            if (document.getContext() != null) {
                try {
                    qname = QName.parse(document.context, localName, namespaceURI);
                } catch (final IllegalQNameException e) {
                    throw new DOMException(DOMException.INVALID_CHARACTER_ERR, e.getMessage());
                }
            } else {
                qname = new QName(localName, namespaceURI);
            }
            return getElementsByTagName(qname);
        }
    }

    private NodeList getElementsByTagName(final QName qname) {
        final NodeListImpl nl = new NodeListImpl();
        int nextNode = nodeNumber;
        final int treeLevel = document.treeLevel[nodeNumber];
        while(++nextNode < document.size && document.treeLevel[nextNode] > treeLevel) {
            if(document.nodeKind[nextNode] == Node.ELEMENT_NODE) {
                final QName qn = document.nodeName[nextNode];
                if(qname.matches(qn)) {
                    nl.add(document.getNode(nextNode));
                }
            }
        }
        return nl;
    }

    @Override
    @Nonnull
    public String getAttributeNS(final String namespaceURI, final String localName) {
        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while(attr < document.nextAttr && document.attrParent[attr] == nodeNumber) {
                final QName name = document.attrName[attr];
                if(name.getLocalPart().equals(localName) && name.getNamespaceURI().equals(namespaceURI)) {
                    return document.attrValue[attr];
                }
                ++attr;
            }
        }
        if(Namespaces.XMLNS_NS.equals(namespaceURI)) {
            int ns = document.alphaLen[nodeNumber];
            if(-1 < ns) {
                while(ns < document.nextNamespace && document.namespaceParent[ns] == nodeNumber) {
                    final QName nsQName = document.namespaceCode[ns];
                    if(nsQName.getLocalPart().equals(localName)) {
                        return nsQName.getNamespaceURI();
                    }
                    ++ns;
                }
            }
        }
        return "";
    }

    @Override
    public void removeAttributeNS(final String namespaceURI, final String localName) throws DOMException {
    }

    @Override
    public Attr getAttributeNodeNS(final String namespaceURI, final String localName) {
        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while((attr < document.nextAttr) && (document.attrParent[attr] == nodeNumber)) {
                final QName name = document.attrName[attr];
                if(name.getLocalPart().equals(localName) && name.getNamespaceURI().equals(namespaceURI)) {
                    return (new AttrImpl(getExpression(), document, attr));
                }
                ++attr;
            }
        }
        if(Namespaces.XMLNS_NS.equals(namespaceURI)) {
            int ns = document.alphaLen[nodeNumber];
            if(-1 < ns) {
                while((ns < document.nextNamespace) && (document.namespaceParent[ns] == nodeNumber)) {
                    final QName nsQName = document.namespaceCode[ns];
                    if(nsQName.getLocalPart().equals(localName)) {
                        return (new NamespaceNode(getExpression(), document, ns));
                    }
                    ++ns;
                }
            }
        }
        return null;
    }

    @Override
    public Attr setAttributeNodeNS(final Attr newAttr) throws DOMException {
        return setAttributeNode(newAttr, qname -> getAttributeNodeNS(qname.getNamespaceURI(), qname.getLocalPart()));
    }

    @Override
    public boolean hasAttribute(final String name) {
        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while(attr < document.nextAttr && document.attrParent[attr] == nodeNumber) {
                final QName attrQName = document.attrName[attr];
                if(attrQName.getStringValue().equals(name)) {
                    return true;
                }
                ++attr;
            }
        }
        if(name.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
            int ns = document.alphaLen[nodeNumber];
            if(-1 < ns) {
                while(ns < document.nextNamespace && document.namespaceParent[ns] == nodeNumber) {
                    final QName nsQName = document.namespaceCode[ns];
                    if(nsQName.getStringValue().equals(name)) {
                        return true;
                    }
                    ++ns;
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasAttributeNS(final String namespaceURI, final String localName) {
        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while(attr < document.nextAttr && document.attrParent[attr] == nodeNumber) {
                final QName name = document.attrName[attr];
                if(name.getLocalPart().equals(localName) && name.getNamespaceURI().equals(namespaceURI)) {
                    return true;
                }
                ++attr;
            }
        }
        if(Namespaces.XMLNS_NS.equals(namespaceURI)) {
            int ns = document.alphaLen[nodeNumber];
            if(-1 < ns) {
                while(ns < document.nextNamespace && document.namespaceParent[ns] == nodeNumber) {
                    final QName nsQName = document.namespaceCode[ns];
                    if(nsQName.getLocalPart().equals(localName)) {
                        return true;
                    }
                    ++ns;
                }
            }
        }
        return false;
    }

    /**
     * The method <code>getPrefixes.</code>
     *
     * @return a <code>Set</code> value
     */
    public Set<String> getPrefixes() {
        final Set<String> set = new HashSet<>();
        int ns = document.alphaLen[nodeNumber];
        if(-1 < ns) {
            while(ns < document.nextNamespace && document.namespaceParent[ns] == nodeNumber) {
                final QName nsQName = document.namespaceCode[ns];
                set.add(nsQName.getStringValue());
                ++ns;
            }
        }
        return set;
    }

    /**
     * The method <code>declaresNamespacePrefixes.</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean declaresNamespacePrefixes() {
        return document.getNamespacesCountFor(nodeNumber) > 0;
    }

    /**
     * The method <code>getNamespaceMap.</code>
     *
     * @return a <code>Map</code> value
     */
    public Map<String, String> getNamespaceMap() {
        return getNamespaceMap(new HashMap<>());
    }

    public Map<String, String> getNamespaceMap(final Map<String, String> map) {
        int ns = document.alphaLen[nodeNumber];
        if(-1 < ns) {
            while(ns < document.nextNamespace && document.namespaceParent[ns] == nodeNumber) {
                final QName nsQName = document.namespaceCode[ns];
                map.put(nsQName.getLocalPart(), nsQName.getNamespaceURI());
                ++ns;
            }
        }

        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while(attr < document.nextAttr && document.attrParent[attr] == nodeNumber) {
                final QName qname = document.attrName[attr];
                if(qname.getPrefix() != null && !qname.getPrefix().isEmpty()) {
                    map.put(qname.getPrefix(), qname.getNamespaceURI());
                }
                ++attr;
            }
        }

        return map;
    }

    @Override
    public int getItemType() {
        return Type.ELEMENT;
    }

    @Override
    public String getBaseURI() {
        final XmldbURI baseURI = calculateBaseURI();
        if(baseURI != null) {
            return baseURI.toString();
        }

        return "";//UNDERSTAND: is it ok?
    }

    //TODO please, keep in sync with org.exist.dom.persistent.ElementImpl
    private XmldbURI calculateBaseURI() {
        XmldbURI baseURI = null;

        final String nodeBaseURI = getAttributeNS(Namespaces.XML_NS, "base");
        if(nodeBaseURI != null) {
            baseURI = XmldbURI.create(nodeBaseURI, false);
            if(baseURI.isAbsolute()) {
                return baseURI;
            }
        }

        int parent = -1;
        final int test = document.getParentNodeFor(nodeNumber);
        if(document.nodeKind[test] != Node.DOCUMENT_NODE) {
            parent = test;
        }

        if(parent != -1) {
            if(nodeBaseURI == null) {
                baseURI = ((ElementImpl) document.getNode(parent))
                    .calculateBaseURI();
            } else {
                XmldbURI parentsBaseURI = ((ElementImpl) document.getNode(parent))
                    .calculateBaseURI();

                if(nodeBaseURI.isEmpty()) {
                    baseURI = parentsBaseURI;
                } else {
                    baseURI = parentsBaseURI.append(baseURI);
                }
            }
        } else {
            if(nodeBaseURI == null) {
                return XmldbURI.create(getOwnerDocument().getBaseURI(), false);
            } else if(nodeNumber == 1) {
                //nothing to do
            } else {
                final String docBaseURI = getOwnerDocument().getBaseURI();
                if(docBaseURI.endsWith("/")) {
                    baseURI = XmldbURI.create(getOwnerDocument().getBaseURI(), false);
                    baseURI.append(baseURI);
                } else {
                    baseURI = XmldbURI.create(getOwnerDocument().getBaseURI(), false);
                    baseURI = baseURI.removeLastSegment();
                    baseURI.append(baseURI);
                }
            }
        }
        return baseURI;
    }

    @Override
    public TypeInfo getSchemaTypeInfo() {
        return null;
    }

    @Override
    public void setIdAttribute(final String name, final boolean isId) throws DOMException {
    }

    @Override
    public void setIdAttributeNS(final String namespaceURI, final String localName, final boolean isId)
        throws DOMException {
    }

    @Override
    public void setIdAttributeNode(final Attr idAttr, final boolean isId) throws DOMException {
    }

    @Override
    public void setTextContent(final String textContent) throws DOMException {
        final int nodeNr = document.addNode(Node.TEXT_NODE, (short) (document.getTreeLevel(nodeNumber) + 1), null);
        document.addChars(nodeNr, textContent.toCharArray(), 0, textContent.length());
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("in-memory#");
        result.append("element {");
        result.append(getQName().getStringValue());
        result.append("} {");
        final NamedNodeMap theAttrs = getAttributes();
        if(theAttrs != null) {
            for(int i = 0; i < theAttrs.getLength(); i++) {
                if(i > 0) {
                    result.append(" ");
                }
                final Node natt = theAttrs.item(i);
                result.append(natt.toString());
            }
        }
        for(int i = 0; i < this.getChildCount(); i++) {
            if(i > 0) {
                result.append(" ");
            }
            final Node child = getChildNodes().item(i);
            result.append(child.toString());
        }
        result.append("} ");
        return result.toString();
    }

    @Override
    public String getTextContent() throws DOMException {
        final StringBuilder result = new StringBuilder();
        for(int i = 0; i < this.getChildCount(); i++) {
            final Node child = getChildNodes().item(i);
            if(child instanceof Text) {
                if(i > 0) {
                    result.append(" ");
                }
                result.append(((Text) child).getData());
            }
        }
        return result.toString();
    }

    @Override
    public Node appendChild(final Node newChild) throws DOMException {
        if(newChild.getNodeType() != Node.DOCUMENT_NODE && newChild.getOwnerDocument() != document) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Owning document IDs do not match");
        }

        if(newChild == this) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "Cannot append an element to itself");
        }

        if(newChild.getNodeType() == DOCUMENT_NODE) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "A Document Node may not be appended to an element");
        }

        if(newChild.getNodeType() == DOCUMENT_TYPE_NODE) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "A Document Type Node may not be appended to an element");
        }

        if(newChild instanceof NodeImpl) {
            final int treeLevel = document.treeLevel[nodeNumber];
            final int newChildTreeLevel = document.treeLevel[((NodeImpl)newChild).nodeNumber];
            if(newChildTreeLevel < treeLevel) {
                throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                        "The node to append is one of this node's ancestors");
            }
        }

        throw unsupported();
    }

    public String getAttributeValue(final String name) {
        int attr = 0;
        while (attr < document.nextAttr) {
            final QName attrQName = document.attrName[attr];
            if (attrQName.getStringValue().equals(name)) {
                return document.attrValue[attr];
            }
            ++attr;
        }
        return null;
    }
}
