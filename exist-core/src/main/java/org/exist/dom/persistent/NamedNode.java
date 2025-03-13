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
package org.exist.dom.persistent;

import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.xquery.Expression;
import org.w3c.dom.DOMException;

/**
 * A node with a QName, i.e. an element or attribute.
 *
 * @author wolf
 */
public abstract class NamedNode<T extends NamedNode> extends StoredNode<T> {

    protected QName nodeName = null;

    protected NamedNode(final short nodeType) {
        this(null, nodeType);
    }

    protected NamedNode(final Expression expression, final short nodeType) {
        super(expression, nodeType);
    }

    protected NamedNode(final short nodeType, final QName qname) {
        this(null, nodeType, qname);
    }

    protected NamedNode(final Expression expression, final short nodeType, final QName qname) {
        super(expression, nodeType);
        this.nodeName = qname;
    }

    protected NamedNode(final short nodeType, final NodeId nodeId, final QName qname) {
        this(null, nodeType, nodeId, qname);
    }

    protected NamedNode(final Expression expression, final short nodeType, final NodeId nodeId, final QName qname) {
        super(expression, nodeType, nodeId);
        this.nodeName = qname;
    }

    protected NamedNode(final NamedNode<T> other) {
        this(null, other);
    }

    protected NamedNode(final Expression expression, final NamedNode<T> other) {
        super(expression, other);
        this.nodeName = other.nodeName;
    }

    /**
     * Extracts just the details of the NamedNode
     */
    public NamedNode<T> extract() {
        return new NamedNode<>(getExpression(), this) {
        };
    }

    @Override
    public QName getQName() {
        return nodeName;
    }

    @Override
    public void setQName(final QName qname) {
        this.nodeName = qname;
    }

    @Override
    public String getLocalName() {
        return getQName().getLocalPart();
    }

    /**
     * @deprecated use #setQName(qname) instead
     * @param name qname of the node
     */
    @Deprecated
    public void setNodeName(final QName name) {
        nodeName = name;
    }

    public void setNodeName(final QName name, final SymbolTable symbols) throws DOMException {
        this.nodeName = name;
        if(symbols.getSymbol(nodeName.getLocalPart()) < 0) {
            throw new DOMException(DOMException.INVALID_ACCESS_ERR,
                "Too many element/attribute names registered in the database. No of distinct names is limited to 16bit. Aborting store.");
        }
    }

    @Override
    public void clear() {
        super.clear();
        this.nodeName = null;
    }

}
