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
package org.exist.xquery.functions.fn;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;

/**
 * @author wolf
 * @author Dannes
 */
public class FunDocumentURI extends Function {

    private static final FunctionParameterSequenceType FS_PARAM_NODE = optManyParam("value", Type.NODE, "The document node.");

    private static final String FS_DOCUMENT_URI = "document-uri";
    private static final String FS_DESCRIPTION = "Returns the URI of a resource where a document can be found, if available.";

    private static final String FS_RETURN_DESCRIPTION = "The URI of a resource.";
    static final FunctionSignature FS_DOCUMENT_URI_0 = functionSignature(
            FS_DOCUMENT_URI,
            FS_DESCRIPTION,
            returnsOpt(Type.ANY_URI, FS_RETURN_DESCRIPTION)
    );

    static final FunctionSignature FS_DOCUMENT_URI_1 = functionSignature(
            FS_DOCUMENT_URI,
            FS_DESCRIPTION,
            returnsOpt(Type.ANY_URI, FS_RETURN_DESCRIPTION),
            FS_PARAM_NODE
    );

    public FunDocumentURI(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(final Sequence contextSequence, final Item contextItem)  throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,"CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,"CONTEXT ITEM", contextItem.toSequence());
            }
        }

        final boolean isContextItem = (contextItem != null);

        if(!isContextItem && getArgumentCount()==0){
            // Bug in eXist-db? the 0-parameter function is invoked
            // which effectively means that the zero-argument function is active.
            throw new XPathException(this, ErrorCodes.XPDY0002, "Context item is absent ");
        }

        // Get sequence from contextItem or from parameter
        final Sequence seq = (isContextItem)
                ? contextItem.toSequence()
                : getArgument(0).eval(contextSequence, contextItem);

        if (isContextItem && seq.isEmpty()) {
            // This is the actual empty context item check
            throw new XPathException(this, ErrorCodes.XPDY0002, "Context item is absent.");
        }

        if (isContextItem &&  !Type.subTypeOf(seq.getItemType(), Type.NODE) ) {
            // If context item is provided, it must be a node
            throw new XPathException(this, ErrorCodes.XPTY0004, "Context item is not a node.");
        }

        Sequence result = Sequence.EMPTY_SEQUENCE;
        if (!seq.isEmpty()) {
            final NodeValue value = (NodeValue) seq.itemAt(0);
            if (value.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                final NodeProxy node = (NodeProxy) value;
                //Returns the empty sequence if the node is not a document node.
                if (node.isDocument()) {
                    final XmldbURI path = node.getOwnerDocument().getURI();
                    result = new AnyURIValue(path);
                }

            } else {
                if (value instanceof DocumentImpl && ((DocumentImpl)value).getDocumentURI() != null) {
                    result = new AnyURIValue(((DocumentImpl)value).getDocumentURI());
                }
            }
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;
    }
}
