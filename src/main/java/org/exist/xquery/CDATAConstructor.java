/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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

import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Constructs an in-memory CDATA node.
 *
 * @author wolf
 */
public class CDATAConstructor extends NodeConstructor {
    private final String cdata;

    /**
     * @param context the xquery context
     * @param content the content of the CDATA section
     */
    public CDATAConstructor(final XQueryContext context, final String content) {
        super(context);
        this.cdata = content;
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        if (newDocumentContext) {
            context.pushDocumentContext();
        }

        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final NodeImpl node = builder.getDocument().getNode(builder.cdataSection(cdata));

            if (context.getProfiler().isEnabled()) {
                context.getProfiler().end(this, "", node);
            }

            return node;
        } finally {
            if (newDocumentContext) {
                context.popDocumentContext();
            }
        }
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("<![CDATA[").display(cdata).display("]]>");
    }

    @Override
    public String toString() {
        return "<![CDATA[" + cdata + "]]>";
    }
}
