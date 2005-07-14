/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.TextImpl;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.serializers.NativeSerializer;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.DOMException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

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
            "The function takes a sequence of text nodes as first argument and a callback function (defined with " +
            "util:function) as second parameter. Text nodes without matches will be returned as they are. However, if the text " +
            "contains a match marker, the matching character sequence is reported to the callback function, and the " +
            "result of the function call is inserted into the resulting node set where the matching sequence occurred. For example, " +
            "you can use this to mark all matching terms with a <span class=\"highlight\">abc</span>.",
            new SequenceType[]{
                    new SequenceType(Type.TEXT, Cardinality.ZERO_OR_MORE),
                    new SequenceType(Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE));
    
    public HighlightMatches(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        if (args[0].getLength() == 0)
            return Sequence.EMPTY_SEQUENCE;
        FunctionReference ref = (FunctionReference) args[1].itemAt(0);
        FunctionCall call = ref.getFunctionCall();
        
        context.pushDocumentContext();
        
        MemTreeBuilder builder = context.getDocumentBuilder();
        ValueSequence result = new ValueSequence();
        for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
            NodeValue v = (NodeValue) i.nextItem();
            if (v.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                result.add(v);
            } else {
                NodeProxy p = (NodeProxy) v;
                String s = processText((TextImpl) p.getNode(), p.getMatches());
                display(s, builder, call, result);
            }
        }
        context.popDocumentContext();
        return result;
    }

    private final String processText(TextImpl text, Match match) {
        if (match == null) return null;
        // prepare a regular expression to mark match-terms
        StringBuffer expr = null;
        Match next = match;
        while (next != null) {
            if (next.getNodeId() == text.getGID()) {
                if (expr == null) {
                    expr = new StringBuffer();
                    expr.append("\\b(");
                }
                if (expr.length() > 5) expr.append('|');
                expr.append(next.getMatchingTerm());
            }
            next = next.getNextMatch();
        }
        if (expr != null) {
            expr.append(")\\b");
            Pattern pattern = Pattern.compile(expr.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(text.getData());
            return matcher.replaceAll("||$1||");
        }
        return null;
    }
    
    private final void display(String data, MemTreeBuilder builder, FunctionCall call, Sequence result)
    throws XPathException {
        int p0 = 0, p1;
        boolean inTerm = false;
        int nodeNr;
        Sequence params[] = new Sequence[1];
        while (p0 < data.length()) {
            p1 = data.indexOf("||", p0);
            if (p1 < 0) {
                nodeNr = builder.characters(data.substring(p0));
                result.add(builder.getDocument().getNode(nodeNr));
                break;
            }
            if (inTerm) {
                params[0] = new StringValue(data.substring(p0, p1));
                result.addAll(call.evalFunction(null, null, params));
                inTerm = false;
            } else {
                inTerm = true;
                nodeNr = builder.characters(data.substring(p0, p1));
                result.add(builder.getDocument().getNode(nodeNr));
            }
            p0 = p1 + 2;
        }
    }
}
