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
package org.exist.xquery.xquf;

import org.exist.dom.QName;
import org.exist.xquery.Expression;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Node;

import javax.annotation.Nullable;

/**
 * Represents a single update primitive in the W3C XQuery Update Facility 3.0
 * Pending Update List (PUL).
 *
 * Each primitive carries a target node, optional content/value, and the
 * expression that created it (for error reporting).
 */
public class UpdatePrimitive {

    public enum Type {
        INSERT_INTO,
        INSERT_INTO_AS_FIRST,
        INSERT_INTO_AS_LAST,
        INSERT_BEFORE,
        INSERT_AFTER,
        INSERT_ATTRIBUTES,
        DELETE,
        REPLACE_NODE,
        REPLACE_VALUE,
        RENAME,
        PUT
    }

    private final Type type;
    private final Node targetNode;
    private final @Nullable Sequence content;
    private final @Nullable QName newName;
    private final @Nullable String uri;
    private final Expression sourceExpr;

    public UpdatePrimitive(final Type type, final Node targetNode, @Nullable final Sequence content,
                           @Nullable final QName newName, @Nullable final String uri,
                           final Expression sourceExpr) {
        this.type = type;
        this.targetNode = targetNode;
        this.content = content;
        this.newName = newName;
        this.uri = uri;
        this.sourceExpr = sourceExpr;
    }

    public Type getType() {
        return type;
    }

    public Node getTargetNode() {
        return targetNode;
    }

    @Nullable
    public Sequence getContent() {
        return content;
    }

    @Nullable
    public QName getNewName() {
        return newName;
    }

    @Nullable
    public String getUri() {
        return uri;
    }

    public Expression getSourceExpression() {
        return sourceExpr;
    }

    // Factory methods

    public static UpdatePrimitive insertInto(final Node target, final Sequence content, final Expression expr) {
        return new UpdatePrimitive(Type.INSERT_INTO, target, content, null, null, expr);
    }

    public static UpdatePrimitive insertIntoAsFirst(final Node target, final Sequence content, final Expression expr) {
        return new UpdatePrimitive(Type.INSERT_INTO_AS_FIRST, target, content, null, null, expr);
    }

    public static UpdatePrimitive insertIntoAsLast(final Node target, final Sequence content, final Expression expr) {
        return new UpdatePrimitive(Type.INSERT_INTO_AS_LAST, target, content, null, null, expr);
    }

    public static UpdatePrimitive insertBefore(final Node target, final Sequence content, final Expression expr) {
        return new UpdatePrimitive(Type.INSERT_BEFORE, target, content, null, null, expr);
    }

    public static UpdatePrimitive insertAfter(final Node target, final Sequence content, final Expression expr) {
        return new UpdatePrimitive(Type.INSERT_AFTER, target, content, null, null, expr);
    }

    public static UpdatePrimitive insertAttributes(final Node target, final Sequence content, final Expression expr) {
        return new UpdatePrimitive(Type.INSERT_ATTRIBUTES, target, content, null, null, expr);
    }

    public static UpdatePrimitive delete(final Node target, final Expression expr) {
        return new UpdatePrimitive(Type.DELETE, target, null, null, null, expr);
    }

    public static UpdatePrimitive replaceNode(final Node target, final Sequence content, final Expression expr) {
        return new UpdatePrimitive(Type.REPLACE_NODE, target, content, null, null, expr);
    }

    public static UpdatePrimitive replaceValue(final Node target, final Sequence content, final Expression expr) {
        return new UpdatePrimitive(Type.REPLACE_VALUE, target, content, null, null, expr);
    }

    public static UpdatePrimitive rename(final Node target, final QName newName, final Expression expr) {
        return new UpdatePrimitive(Type.RENAME, target, null, newName, null, expr);
    }

    public static UpdatePrimitive put(final Node target, final String uri, final Expression expr) {
        return new UpdatePrimitive(Type.PUT, target, null, null, uri, expr);
    }
}
