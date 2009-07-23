
/*
 *  eXist Native XML Database
 *  Copyright (C) 2001-06,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xquery;

import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

/**
 * Represents an enclosed expression <code>{expr}</code> inside element
 * content. Enclosed expressions within attribute values are processed by
 * {@link org.exist.xquery.AttributeConstructor}.
 *  
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class EnclosedExpr extends PathExpr {

	/**
	 * 
	 */
	public EnclosedExpr(XQueryContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence)
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

        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }
        // evaluate the expression
		context.pushDocumentContext();
        Sequence result;
        try {
            result = super.eval(contextSequence, null);
        } finally {
            context.popDocumentContext();
        }
        
		// create the output
		MemTreeBuilder builder = context.getDocumentBuilder();
		DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);		
		try {
			SequenceIterator i = result.iterate();
			Item next = i.nextItem();			
			StringBuilder buf = null;
            boolean allowAttribs = true;
            while (next != null) {
			    context.proceed(this, builder);
				// if item is an atomic value, collect the string values of all
				// following atomic values and seperate them by a space. 
				if (Type.subTypeOf(next.getType(), Type.ATOMIC)) {
				    if(buf == null)
				        buf = new StringBuilder();
					else if (buf.length() > 0)
						buf.append(' ');
					buf.append(next.getStringValue());
                    allowAttribs = false;
                    next = i.nextItem();
                // if item is a node, flush any collected character data and
				//	copy the node to the target doc. 
				} else if (Type.subTypeOf(next.getType(), Type.NODE)) {
                    if (buf != null && buf.length() > 0) {
						receiver.characters(buf);
						buf.setLength(0);
					}
                    if (next.getType() == Type.ATTRIBUTE && !allowAttribs)
                        throw new XPathException(this, "XQTY0024: An attribute may not appear after " +
                            "another child node.");
                    next.copyTo(context.getBroker(), receiver);
                    allowAttribs = next.getType() == Type.ATTRIBUTE;
                    next = i.nextItem();
				}
			}
			// flush remaining character data
			if (buf != null && buf.length() > 0)
				receiver.characters(buf);
		} catch (SAXException e) {
		    LOG.warn("SAXException during serialization: " + e.getMessage(), e);
            throw new XPathException(this, e.getMessage());
			//throw new XPathException(getASTNode(),
			//	"Encountered SAX exception while serializing enclosed expression: "
			//		+ ExpressionDumper.dump(this));
		}
        
       if (context.getProfiler().isEnabled())           
            context.getProfiler().end(this, "", result);              
           
		return result;
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("{");
        dumper.startIndent();
        super.dump(dumper);
        dumper.endIndent();
        dumper.nl().display("}");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("{");    	
    	result.append(super.toString());        
    	result.append("}");
    	return result.toString();
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitPathExpr(this);
    }
}
