/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.xquery;

import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;


/**
 * Implements a dynamic document constructor. Creates a new
 * document node with its own node identity.
 * 
 * @author wolf
 */
public class DocumentConstructor extends NodeConstructor {

    private final Expression content;
    
    /**
     * @param context
     */
    public DocumentConstructor(XQueryContext context, Expression contentExpr) {
        super(context);
        this.content = contentExpr;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        content.analyze(contextInfo);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
        Sequence contentSeq = content.eval(contextSequence, contextItem);
        
        context.pushDocumentContext();
        
        MemTreeBuilder builder = context.getDocumentBuilder();
        DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
        
        try {        
	        if(!contentSeq.isEmpty()) {
		        
		        StringBuffer buf = null;
		        SequenceIterator i = contentSeq.iterate();
		        Item next = i.nextItem();
		        while(next != null) {
		            context.proceed(this, builder);
		            if(next.getType() == Type.ATTRIBUTE || 
	                   next.getType() == Type.NAMESPACE /*||
		               next.getType() == Type.DOCUMENT*/)
		                throw new XPathException(getASTNode(), "Found a node of type " + Type.getTypeName(next.getType()) +
		                        " inside a document constructor");
		            // if item is an atomic value, collect the string values of all
					// following atomic values and seperate them by a space. 
					if (Type.subTypeOf(next.getType(), Type.ATOMIC)) {
					    if(buf == null)
					        buf = new StringBuffer();
						else if (buf.length() > 0)
							buf.append(' ');
						buf.append(next.getStringValue());
						next = i.nextItem();
					// if item is a node, flush any collected character data and
					//	copy the node to the target doc. 
					} else if (next.getType() == Type.DOCUMENT) {		
						if (buf != null && buf.length() > 0) {
							receiver.characters(buf);
							buf.setLength(0);
						}
						next.copyTo(context.getBroker(), receiver);
						next = i.nextItem();
					} else if (Type.subTypeOf(next.getType(), Type.NODE)) {
						if (buf != null && buf.length() > 0) {
							receiver.characters(buf);
							buf.setLength(0);
						}
						next.copyTo(context.getBroker(), receiver);
						next = i.nextItem();
					}	

				//TODO : design like below ? -pb
	           /* 		        
		        
		        //TODO : wondering whether we shouldn't iterate over a nodeset as the specs would tend to say. -pb	        
		        
		        SequenceIterator i = contentSeq.iterate();
		        Item next = i.nextItem();
		        while(next != null) {
		            context.proceed(this, builder);
		            
					if (Type.subTypeOf(next.getType(), Type.NODE)) {
						//flush any collected character data
						if (buf != null && buf.length() > 0) {
							receiver.characters(buf);
							buf.setLength(0);
						}					
						// copy the node to the target doc
						if(next.getType() == Type.ATTRIBUTE) {
							throw new XPathException(getASTNode(), "XPTY0004 : Found a node of type " + 
								Type.getTypeName(next.getType()) +  " inside a document constructor");							
						} else if (next.getType() == Type.DOCUMENT) {		
							//TODO : definitely broken, but that's the way to do
							for (int j = 0 ; j < ((DocumentImpl)next).getChildCount(); j++) {								
								((DocumentImpl)next).getNode(j).copyTo(context.getBroker(), receiver);
							}							
						} else if (Type.subTypeOf(next.getType(), Type.TEXT)) {
							//TODO
							buf.append("#text");
						} else {
							next.copyTo(context.getBroker(), receiver);
						}
					} else {					
					    if(buf == null)
					        buf = new StringBuffer();
						//else if (buf.length() > 0)
						//	buf.append(' ');
						buf.append(next.getStringValue());						
					}
					next = i.nextItem();
					*/
		        }
		        
		        // flush remaining character data
				if (buf != null && buf.length() > 0) {
					receiver.characters(buf);
					buf.setLength(0);
				}
	        }
        } catch(SAXException e) {
			throw new XPathException(getASTNode(),
				"Encountered SAX exception while processing document constructor: "
					+ ExpressionDumper.dump(this));
        }	        
        
        context.popDocumentContext();
        
        NodeImpl node =  builder.getDocument();
        
        if (context.getProfiler().isEnabled())           
            context.getProfiler().end(this, "", node);
        
        return node;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("document {");
        dumper.startIndent();
        //TODO : is this the required syntax ?
        content.dump(dumper);
        dumper.endIndent();
        dumper.nl().display("}");
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append("document {");
    	//TODO : is this the required syntax ?
    	result.append(content.toString());       
    	result.append("} ");
    	return result.toString();
    }    

    public void resetState(boolean postOptimization) {
    	super.resetState(postOptimization);
    	content.resetState(postOptimization);
    }
}
