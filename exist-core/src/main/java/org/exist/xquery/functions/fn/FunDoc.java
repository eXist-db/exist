/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
 *  
 *  $Id$
 */
package org.exist.xquery.functions.fn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.storage.UpdateListener;
import org.exist.xquery.*;
import org.exist.xquery.functions.xmldb.XMLDBModule;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements the built-in fn:doc() function.
 * 
 * This will be replaced by XQuery's fn:doc() function.
 * 
 * @author wolf
 */
public class FunDoc extends Function {

    protected static final Logger logger = LogManager.getLogger(FunDoc.class);

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("doc", Function.BUILTIN_FUNCTION_NS),
            "Returns the document node of $document-uri. " +
            XMLDBModule.ANY_URI,
            new SequenceType[] {
                new FunctionParameterSequenceType("document-uri", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The document URI")
            },
            new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ZERO_OR_ONE,
                "the document node of $document-uri")
        );

    // fixit! - security warning
    private UpdateListener listener = null;

    public FunDoc(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT ITEM", contextItem.toSequence());}
        }
        Sequence result;
        final Sequence arg = getArgument(0).eval(contextSequence, contextItem);
        if (arg.isEmpty())
            {result = Sequence.EMPTY_SEQUENCE;}
        else {
            final String path = arg.itemAt(0).getStringValue();
            // TODO: disabled cache for now as it may cause concurrency issues
            // better use compile-time inspection and maybe a pragma to mark those
            // sections in the query that can be safely cached.
            // check if we can return a cached sequence
            //if (cached != null && path.equals(cachedPath)) {
                //return cached;
            //}
            try {
                result = DocUtils.getDocument(this.context, path);
                if (result.isEmpty() && context.isRaiseErrorOnFailedRetrieval()) {
                    throw new XPathException(this, ErrorCodes.FODC0002,
                        "Can not access '" + path + "'", arg);
                }
                //TODO: we still need a final decision about this. Also check base-uri.
                //if (result == Sequence.EMPTY_SEQUENCE)
                    //throw new XPathException(this, path + " is not an XML document");
                final DocumentSet docs = result.getDocumentSet();
                if (docs != null && DocumentSet.EMPTY_DOCUMENT_SET != docs) {
                    // only cache node sets (which have a non-empty document set)
                    registerUpdateListener();
                }
            } catch (final Exception e) {
                throw new XPathException(this, ErrorCodes.FODC0005, e.getMessage(), arg);
            }
        }
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        return result;
    }

    protected void registerUpdateListener() {
        if (listener == null) {
            listener = new UpdateListener() {

                @Override
                public void documentUpdated(DocumentImpl document, int event) {
                    // clear all
                }

                @Override
                public void unsubscribe() {
                    FunDoc.this.listener = null;
                }

                public void nodeMoved(NodeId oldNodeId, NodeHandle newNode) {
                    // not relevant
                }

                @Override
                public void debug() {
                    logger.debug("UpdateListener: Line: " + getLine() +
                        ": " + FunDoc.this.toString());
                }
            };
            context.registerUpdateListener(listener);
        }
    }

    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        if (!postOptimization) {
            listener = null;
        }
        getArgument(0).resetState(postOptimization);
    }
}
