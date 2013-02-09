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
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.util.FastQSort;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xml.sax.SAXException;

public class KWICDisplay extends BasicFunction {

	protected static final FunctionParameterSequenceType TEXT_ARG = new FunctionParameterSequenceType("text", Type.TEXT, Cardinality.ZERO_OR_MORE, "The text nodes");
	protected static final FunctionParameterSequenceType WIDTH_ARG = new FunctionParameterSequenceType("width", Type.POSITIVE_INTEGER, Cardinality.EXACTLY_ONE, "The width");
	protected static final FunctionParameterSequenceType CALLBACK_ARG = new FunctionParameterSequenceType("callback-function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The callback function");
    protected static final FunctionParameterSequenceType RESULT_CALLBACK_ARG = new FunctionParameterSequenceType("result-callback", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The result callback function");
	protected static final FunctionParameterSequenceType PARAMETERS_ARG = new FunctionParameterSequenceType("parameters", Type.ITEM, Cardinality.ZERO_OR_MORE, "The parameters passed into the last argument of the callback function");

	public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("kwic-display", TextModule.NAMESPACE_URI, TextModule.PREFIX),
            "Deprecated: kwic functionality is now provided by an XQuery module, see " +
            "http://exist-org/kwic.html." +
            "This function takes a sequence of text nodes in $a, containing matches from a fulltext search. " +
            "It highlights matching strings within those text nodes in the same way as the text:highlight-matches " +
            "function. However, only a defined portion of the text surrounding the first match (and maybe following matches) " +
            "is returned. If the text preceding the first match is larger than the width specified in the second argument $b, " +
            "it will be truncated to fill no more than (width - keyword-length) / 2 characters. Likewise, the text following " +
            "the match will be truncated in such a way that the whole string sequence fits into width characters. " +
            "The third parameter $c is a callback function (defined with util:function). $d may contain an additional sequence of " +
            "values that will be passed to the last parameter of the callback function. Any matching character sequence is reported " +
            "to the callback function, and the " +
            "result of the function call is inserted into the resulting node set where the matching sequence occurred. " +
            "For example, you can use this to mark all matching terms with a <span class=\"highlight\">abc</span>. " +
            "The callback function should take 3 or 4 arguments: 1) the text sequence corresponding to the match as xs:string, " +
            "2) the text node to which this match belongs, 3) the sequence passed as last argument to kwic-display. " +
            "If the callback function accepts 4 arguments, the last argument will contain additional " + 
            "information on the match as a sequence of 4 integers: a) the number of the match if there's more than " +
            "one match in a text node - the first match will be numbered 1; b) the offset of the match into the original text node " +
            "string; c) the length of the match as reported by the index.",
            new SequenceType[]{ TEXT_ARG, WIDTH_ARG, CALLBACK_ARG, PARAMETERS_ARG },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the results"),
            "Improved kwic functionality is now provided by a separate XQuery module, see " +
            "http://exist-db.org/kwic.html."),
        new FunctionSignature(
                new QName("kwic-display", TextModule.NAMESPACE_URI, TextModule.PREFIX),
                "This function takes a sequence of text nodes in $a, containing matches from a fulltext search. " +
                "It highlights matching strings within those text nodes in the same way as the text:highlight-matches " +
                "function. However, only a defined portion of the text surrounding the first match (and maybe following matches) " +
                "is returned. If the text preceding the first match is larger than the width specified in the second argument $b, " +
                "it will be truncated to fill no more than (width - keyword-length) / 2 characters. Likewise, the text following " +
                "the match will be truncated in such a way that the whole string sequence fits into width characters. " +
                "The third parameter $c is a callback function (defined with util:function). $d may contain an additional sequence of " +
                "values that will be passed to the last parameter of the callback function. Any matching character sequence is reported " +
                "to the callback function, and the " +
                "result of the function call is inserted into the resulting node set where the matching sequence occurred. " +
                "For example, you can use this to mark all matching terms with a <span class=\"highlight\">abc</span>. " +
                "The callback function should take 3 or 4 arguments: 1) the text sequence corresponding to the match as xs:string, " +
                "2) the text node to which this match belongs, 3) the sequence passed as last argument to kwic-display. " +
                "If the callback function accepts 4 arguments, the last argument will contain additional " + 
                "information on the match as a sequence of 4 integers: a) the number of the match if there's more than " +
                "one match in a text node - the first match will be numbered 1; b) the offset of the match into the original text node " +
                "string; c) the length of the match as reported by the index.",
                new SequenceType[]{ TEXT_ARG, WIDTH_ARG, CALLBACK_ARG, RESULT_CALLBACK_ARG, PARAMETERS_ARG },
                new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the results"),
                "Improved kwic functionality is now provided by a separate XQuery module, see " +
                "http://exist-db.org/kwic.html.")
    };
    
    public KWICDisplay(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        if (args[0].isEmpty())
            {return Sequence.EMPTY_SEQUENCE;}
        
        final FunctionReference call = (FunctionReference) args[2].itemAt(0);
        
        FunctionReference resultCallback = null;
        if (getArgumentCount() == 5) {
            resultCallback = (FunctionReference) args[3].itemAt(0);
        }
        
        final int width = ((IntegerValue)args[1].itemAt(0)).getInt();
        
        context.pushDocumentContext();
        
        final MemTreeBuilder builder = context.getDocumentBuilder();
        final Sequence result = processText(builder, args[0], width, call, resultCallback, args[getArgumentCount() - 1]);
        context.popDocumentContext();
        return result;
    }

    private final Sequence processText(MemTreeBuilder builder, Sequence nodes, int width, 
            FunctionReference callback, FunctionReference resultCallback, Sequence extraArgs) throws XPathException {
        final StringBuilder str = new StringBuilder();
        NodeValue node;
        List<Match.Offset> offsets = null;
        NodeProxy firstProxy = null;
        
        // First step: scan the passed node sequence and collect the string values of all nodes.
        // Translate the relative offsets into absolute offsets.
        for (final SequenceIterator i = nodes.iterate(); i.hasNext(); ) {
            node = (NodeValue) i.nextItem();
            if (node.getImplementationType() == NodeValue.IN_MEMORY_NODE)
                {throw new XPathException(this, "Function kwic-display" +
                        " can not be invoked on constructed nodes");}
            NodeProxy proxy = (NodeProxy) node;
            // remember the first node, we need it later
            if (firstProxy == null)
                {firstProxy = proxy;}
            final TextImpl text = (TextImpl) proxy.getNode();
            
            Match next = proxy.getMatches();
            while (next != null) {
                if (next.getNodeId().equals(text.getNodeId())) {
                    if (offsets == null)
                        {offsets = new ArrayList<Match.Offset>();}
                    final int freq = next.getFrequency();
                    for (int j = 0; j < freq; j++) {
                        // translate the relative offset into an absolute offset and add it to the list
                        final Match.Offset offset = next.getOffset(j);
                        offset.setOffset(str.length() + offset.getOffset());
                        offsets.add(offset);
                    }
                }
                next = next.getNextMatch();
            }
            
            // append the string value of the node to the buffer
            str.append(text.getData());
        }
        
        // Second step: output the text
        ValueSequence result = new ValueSequence();
        final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
        int nodeNr;
        int currentWidth = 0;
        if (offsets == null) {
            // no matches: just output the entire text
            if (width > str.length())
                {width = str.length();}
            nodeNr = builder.characters(str.substring(0, width));
            result.add(builder.getDocument().getNode(nodeNr));
            currentWidth += width;
        } else {
            // sort the offsets
            FastQSort.sort(offsets, 0, offsets.size() - 1);
            
            int nextOffset = 0;
            int pos = 0;
            int lastNodeNr = -1;
            
            // prepare array for callback function arguments
            final Sequence params[] = new Sequence[callback.getSignature().getArgumentCount()];
            params[1] = firstProxy;
            params[2] = extraArgs;
            
            // handle the first match: if the text to the left of the match
            // is larger than half of the width, truncate it. 
            if (str.length() > width) {
                final Match.Offset firstMatch = offsets.get(nextOffset++);
                if (firstMatch.getOffset() > 0) {
                    int leftWidth = (width - firstMatch.getLength()) / 2;
                    if (firstMatch.getOffset() > leftWidth) {
                        pos = truncateStart(str, firstMatch.getOffset() - leftWidth, firstMatch.getOffset());
                        leftWidth = firstMatch.getOffset() - pos;
                    } else
                        {leftWidth = firstMatch.getOffset();}
                    nodeNr = builder.characters(str.substring(pos, pos + leftWidth));
                    // adjacent chunks of text will be merged into one text node. we may
                    // thus get duplicate nodes here. check the nodeNr to avoid adding
                    // the same node twice.
                    if (lastNodeNr != nodeNr)
                    	{result.add(builder.getDocument().getNode(nodeNr));}
                    lastNodeNr = nodeNr;
                    currentWidth += leftWidth;
                    pos += leftWidth;
                }
    
                // put the matching term into argument 0 of the callback function
                params[0] = new StringValue(str.substring(firstMatch.getOffset(), firstMatch.getOffset() + firstMatch.getLength()));
                // if the callback function accepts 4 arguments, the last argument should contain additional
                // information on the match:
                if (callback.getSignature().getArgumentCount() == 4) {
                	params[3] = new ValueSequence();
                	params[3].add(new IntegerValue(nextOffset - 1));
                	params[3].add(new IntegerValue(firstMatch.getOffset()));
                	params[3].add(new IntegerValue(firstMatch.getLength()));
                }
                // now execute the callback func.
                final Sequence callbackResult = callback.evalFunction(null, null, params);
                // iterate through the result of the callback
                for (final SequenceIterator iter = callbackResult.iterate(); iter.hasNext(); ) {
                	final Item next = iter.nextItem();
                	if (Type.subTypeOf(next.getType(), Type.NODE)) {
                		nodeNr = builder.getDocument().getLastNode();
                		try {
							next.copyTo(context.getBroker(), receiver);
							result.add(builder.getDocument().getNode(++nodeNr));
							lastNodeNr = nodeNr;
						} catch (final SAXException e) {
							throw new XPathException(this, "Internal error while copying nodes: " + e.getMessage(), e);
						}
                	}
                }
                currentWidth += firstMatch.getLength();
                pos += firstMatch.getLength();
            } else
                {width = str.length();}
            
            // output the rest of the text and matches
            Match.Offset offset;
            for (int i = nextOffset; i < offsets.size() && currentWidth < width; i++) {
                offset = offsets.get(i);
                if (offset.getOffset() > pos) {
                    int len = offset.getOffset() - pos;
                    if (currentWidth + len > width)
                        {len = width - currentWidth;}
                    nodeNr = builder.characters(str.substring(pos, pos + len));
                    if (lastNodeNr != nodeNr)
                    	{result.add(builder.getDocument().getNode(nodeNr));}
                    currentWidth += len;
                    pos += len;
                }
                
                if (currentWidth + offset.getLength() < width) {
                	// put the matching term into argument 0 of the callback function
                    params[0] = new StringValue(str.substring(offset.getOffset(), offset.getOffset() + offset.getLength()));
                    // if the callback function accepts 4 arguments, the last argument should contain additional
                    // information on the match:
                    if (callback.getSignature().getArgumentCount() == 4) {
                    	params[3] = new ValueSequence();
                    	params[3].add(new IntegerValue(i));
                    	params[3].add(new IntegerValue(offset.getOffset()));
                    	params[3].add(new IntegerValue(offset.getLength()));
                    }
                    // execute the callback function
                    final Sequence callbackResult = callback.evalFunction(null, null, params);
                    for (final SequenceIterator iter = callbackResult.iterate(); iter.hasNext(); ) {
                    	final Item next = iter.nextItem();
                    	if (Type.subTypeOf(next.getType(), Type.NODE)) {
                    		nodeNr = builder.getDocument().getLastNode();
                    		try {
    							next.copyTo(context.getBroker(), receiver);
    							result.add(builder.getDocument().getNode(++nodeNr));
    							lastNodeNr = nodeNr;
    						} catch (final SAXException e) {
    							throw new XPathException(this, "Internal error while copying nodes: " + e.getMessage(), e);
    						}
                    	}
                    }
                    currentWidth += offset.getLength();
                    pos += offset.getLength();
                } else
                    {break;}
            }
            // print the final text chunk if more space is available
            if (currentWidth < width && pos < str.length()) {
                boolean truncated = false;
                int len = str.length() - pos;
                if (len > width - currentWidth) {
                    truncated = true;
                    len = width - currentWidth;
                }
                nodeNr = builder.characters(str.substring(pos, pos + len));
                if (lastNodeNr != nodeNr)
                	{result.add(builder.getDocument().getNode(nodeNr));}
                lastNodeNr = nodeNr;
                currentWidth += len;
                
                if (truncated) {
                    nodeNr = builder.characters(" ...");
                    if (lastNodeNr != nodeNr)
                    	{result.add(builder.getDocument().getNode(nodeNr));}
                    lastNodeNr = nodeNr;
                }
            }
        }
        
        // if the user specified a result callback function, call it now
        if (resultCallback != null) {
            final Sequence params[] = new Sequence[3];
            params[0] = result;
            params[1] = new IntegerValue(currentWidth);
            params[2] = extraArgs;
            return resultCallback.evalFunction(null, null, params);
        } else
            {return result;}
    }
    
    private final static int truncateStart(StringBuilder buf, int start, int end) {
        if (start > 0 && !Character.isLetterOrDigit(buf.charAt(start - 1)))
            {return start;}
        while (start < end && Character.isLetterOrDigit(buf.charAt(start))) {
            start++;
        }
        
        while (start < end && !Character.isLetterOrDigit(buf.charAt(start))) {
            start++;
        }
        return start;
    }
}
