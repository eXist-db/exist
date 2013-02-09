/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.text;

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.TextImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.util.FastQSort;
import org.exist.util.XMLString;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.DOMException;

public class HighlightMatches extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("highlight-matches", TextModule.NAMESPACE_URI, TextModule.PREFIX),
            "Highlight matching strings within text nodes that resulted from a fulltext search. " +
            "When searching with one of the fulltext operators or functions, eXist keeps track of " +
            "the fulltext matches within the text. Usually, the serializer will mark those matches by enclosing them " +
            "into an 'exist:match' element. One can then use an XSLT stylesheet to replace those match elements " +
            "and highlight matches to the user. However, this is not always possible, so Instead of using an XSLT " +
            "to post-process the serialized output, the " +
            "highlight-matches function provides direct access to the matching portions of the text within XQuery. " +
            "The function takes a sequence of text nodes as first argument $source and a callback function (defined with " +
            "util:function) as second parameter. $parameters may contain a sequence of additional values that will be passed " +
            "to the callback functions third parameter. Text nodes without matches will be returned as they are. However, " +
            "if the text contains a match marker, the matching character sequence is reported to the callback function, and the " +
            "result of the function call is inserted into the resulting node set where the matching sequence occurred. For example, " +
            "you can use this to mark all matching terms with a <span class=\"highlight\">abc</span>.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("source", Type.TEXT, Cardinality.ZERO_OR_MORE, "The sequence of text nodes"),
                    new FunctionParameterSequenceType("callback-function-ref", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The callback function (defined with util:function)"),
                    new FunctionParameterSequenceType("parameters", Type.ITEM, Cardinality.ZERO_OR_MORE, "The sequence of additional values that will be passed to the callback functions third parameter.")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the source with the added highlights"));
    
    //private final static QName MATCH_ELEMENT = new QName("match", Serializer.EXIST_NS, "exist");
    
    public HighlightMatches(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        if (args[0].isEmpty())
            {return Sequence.EMPTY_SEQUENCE;}
        
        final FunctionReference func = (FunctionReference) args[1].itemAt(0);
        
        context.pushDocumentContext();
        
        final MemTreeBuilder builder = context.getDocumentBuilder();
        final ValueSequence result = new ValueSequence();
        for (final SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
            final NodeValue v = (NodeValue) i.nextItem();
            if (v.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                result.add(v);
            } else {
                final NodeProxy p = (NodeProxy) v;
                processText(builder, p, result, func, args[2]);
            }
        }
        context.popDocumentContext();
        return result;
    }

    private final void processText(MemTreeBuilder builder, NodeProxy proxy, Sequence result, 
            FunctionReference callback, Sequence extraArgs) 
    throws DOMException, XPathException {
        final TextImpl text = (TextImpl) proxy.getNode();
        final Match match = proxy.getMatches();
        int nodeNr;
        if (match == null) {
            nodeNr = builder.characters(text.getXMLString());
            result.add(builder.getDocument().getNode(nodeNr));
        } else {
            List<Match.Offset> offsets = null;
            Match next = match;
            while (next != null) {
                if (next.getNodeId().equals(text.getNodeId())) {
                    if (offsets == null)
                        {offsets = new ArrayList<Match.Offset>();}
                    final int freq = next.getFrequency();
                    for (int i = 0; i < freq; i++) {
                        offsets.add(next.getOffset(i));
                    }
                }
                next = next.getNextMatch();
            }
            
            if (offsets != null) {
                FastQSort.sort(offsets, 0, offsets.size() - 1);
                
                final XMLString str = text.getXMLString();

                int pos = 0;
                for (final Match.Offset offset : offsets) {
                    if (offset.getOffset() > pos) {
                        nodeNr = builder.characters(str.substring(pos, offset.getOffset() - pos));
                        result.add(builder.getDocument().getNode(nodeNr));
                    }
                    
                    final Sequence params[] = { 
                            new StringValue(str.substring(offset.getOffset(), offset.getLength())),
                            proxy,
                            extraArgs
                    };
                    result.addAll(callback.evalFunction(null, null, params));
                    
                    pos = offset.getOffset() + offset.getLength();
                }
                if (pos < str.length()) {
                    nodeNr = builder.characters(str.substring(pos, str.length() - pos));
                    result.add(builder.getDocument().getNode(nodeNr));
                }
            } else {
                nodeNr = builder.characters(text.getXMLString());
                result.add(builder.getDocument().getNode(nodeNr));
            }
        }
    }
}