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

import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Comment;
import org.w3c.dom.Node;

public class CommentImpl extends AbstractCharacterData implements Comment {

    public CommentImpl(final DocumentImpl doc, final int nodeNumber) {
        this(null, doc, nodeNumber);
    }

    public CommentImpl(final Expression expression, final DocumentImpl doc, final int nodeNumber) {
        super(expression, doc, nodeNumber);
    }

    @Override
    public int getItemType() {
        return Type.COMMENT;
    }

    public String getStringValue() {
        return getData();
    }

    public AtomicValue atomize() throws XPathException {
        return new StringValue(getData());
    }

    @Override
    public String getBaseURI() {
        final Node parent = getParentNode();
        if(parent == null) {
            return null;
        }
        return parent.getBaseURI();
    }

    public String toString() {
        return "in-memory#comment {" + getData() + "} ";
    }
}
