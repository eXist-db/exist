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
package org.exist.xquery.modules.ngram;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.MatchListener;
import org.exist.indexing.ngram.NGramIndex;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.indexing.ngram.NGramMatchCallback;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xml.sax.SAXException;

/**
 */
public class HighlightMatches extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("filter-matches", NGramModule.NAMESPACE_URI, NGramModule.PREFIX),
            "Highlight matching strings within text nodes that resulted from a ngram search. " +
            "The function takes a sequence of nodes as first argument $nodes and a callback function (defined with " +
            "util:function) as second parameter $function-reference. Each node in $nodes will be copied into a new document fragment. " +
            "For each ngram match found while copying a node, the callback function in $function-reference will be called once. The " +
            "callback function should take 2 arguments:\n\n1) the matching text string as xs:string,\n2) the node to which this " +
            "text string belongs.\n\nThe callback function should return zero or more nodes, which will be inserted into the " +
            "resulting node set at the place where the matching text sequence occurred.\n\n" +
            "Note: a ngram match on mixed content may span multiple nodes. In this case, the callback function is called " +
            "once for every text node which is part of the matching text sequence.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE, "The sequence of nodes"),
                    new FunctionParameterSequenceType("function-reference", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The callback function")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "a resulting node set"));

    public HighlightMatches(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;

        context.pushDocumentContext();
        final Serializer serializer = context.getBroker().borrowSerializer();
        try (FunctionReference func = (FunctionReference) args[1].itemAt(0)) {
            MemTreeBuilder builder = context.getDocumentBuilder();
            NGramIndexWorker index = (NGramIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(NGramIndex.ID);
            DocumentBuilderReceiver docBuilder = new DocumentBuilderReceiver(this, builder);
            MatchCallback matchCb = new MatchCallback(func, docBuilder);
            ValueSequence result = new ValueSequence();
            for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
                NodeValue v = (NodeValue) i.nextItem();
                try {
                    int nodeNr = builder.getDocument().getLastNode();
                    if (v.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                        ((NodeImpl) v).copyTo(context.getBroker(), docBuilder);
                    } else {
                        NodeProxy p = (NodeProxy) v;
                        MatchListener ml = index.getMatchListener(context.getBroker(), p, matchCb);
                        Receiver receiver;
                        if (ml == null)
                            receiver = docBuilder;
                        else {
                            ml.setNextInChain(docBuilder);
                            receiver = ml;
                        }
                        serializer.setReceiver(receiver);
                        serializer.toReceiver((NodeProxy) v, false);
                    }
                    result.add(builder.getDocument().getNode(++nodeNr));
                } catch (SAXException e) {
                    LOG.warn(e.getMessage(), e);
                    throw new XPathException(this, e.getMessage());
                }
            }
            return result;
        } finally {
            context.getBroker().returnSerializer(serializer);
            context.popDocumentContext();
        }
    }

    private class MatchCallback implements NGramMatchCallback {
        private FunctionReference callback;
        private DocumentBuilderReceiver docBuilder;

        private MatchCallback(FunctionReference callback, DocumentBuilderReceiver docBuilder) {
            this.callback = callback;
            this.docBuilder = docBuilder;
        }

        public void match(Receiver receiver, String matchingText, NodeProxy node) throws XPathException, SAXException {
            Sequence params[] = {
                    new StringValue(matchingText),
                    node,
                    Sequence.EMPTY_SEQUENCE
            };
            context.pushDocumentContext();
            Sequence seq = callback.evalFunction(null, null, params);
            for (SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                next.copyTo(context.getBroker(), docBuilder);
            }
            context.popDocumentContext();
        }
    }
}
