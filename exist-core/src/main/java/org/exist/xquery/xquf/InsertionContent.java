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

import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.Atomize;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * The content of an insert expression, or the replacement of a replace node expression, as XQUF
 * specifies it: "evaluated as though it were an enclosed expression in an element constructor
 * (see Rule 1e)". Under that rule each run of adjacent atomic values becomes one text node, their
 * string values separated by single spaces, so {@code insert node (1, 2, 3) into $e} inserts the
 * text {@code 1 2 3}. Nodes are passed through unchanged.
 */
final class InsertionContent {

    private InsertionContent() {
    }

    /**
     * @param expression the insert or replace expression, which supplies the query context
     * @param content the evaluated source or replacement expression
     * @return the content with each run of atomic values replaced by a text node
     * @throws XPathException XQTY0105 if the content contains a function item that is not an array
     */
    static Sequence of(final Expression expression, final Sequence content) throws XPathException {
        if (content.isEmpty() || Type.subTypeOf(content.getItemType(), Type.NODE)) {
            return content;
        }
        final XQueryContext context = expression.getContext();
        final ValueSequence result = new ValueSequence(content.getItemCount());
        final StringBuilder run = new StringBuilder();
        boolean inRun = false;
        for (final SequenceIterator i = content.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                if (inRun) {
                    result.add(textNode(context, run));
                    run.setLength(0);
                    inRun = false;
                }
                result.add(item);
            } else {
                if (Type.subTypeOf(item.getType(), Type.FUNCTION) && !Type.subTypeOf(item.getType(), Type.ARRAY_ITEM)) {
                    throw new XPathException(expression, ErrorCodes.XQTY0105,
                            "A function item cannot be inserted or used as replacement content.");
                }
                for (final SequenceIterator values = Atomize.atomize(item.toSequence()).iterate(); values.hasNext(); ) {
                    if (inRun) {
                        run.append(' ');
                    }
                    run.append(values.nextItem().getStringValue());
                    inRun = true;
                }
            }
        }
        if (inRun) {
            result.add(textNode(context, run));
        }
        return result;
    }

    private static Item textNode(final XQueryContext context, final CharSequence text) {
        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final int nodeNr = builder.characters(text);
            return builder.getDocument().getNode(nodeNr);
        } finally {
            context.popDocumentContext();
        }
    }
}
