/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-06 Wolfgang M. Meier
 * meier@ifs.tu-darmstadt.de
 * http://exist.sourceforge.net
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
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */

package org.exist.xquery.functions;

import java.util.Iterator;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.NodeSetHelper;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

/**
 * Built-in function fn:lang().
 *
 */
public class FunLang extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("lang", Function.BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE));

	public FunLang(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
        
        Sequence result;
		if (!(Type.subTypeOf(contextSequence.getItemType(), Type.NODE)))
            result = Sequence.EMPTY_SEQUENCE;
        else {
            String lang = getArgument(0).eval(contextSequence).getStringValue();
            QName qname = new QName("lang", context.getURIForPrefix("xml"), "xml");
    		NodeSet attribs = context.getBroker().getElementIndex().getAttributesByName(contextSequence.toNodeSet().getDocumentSet(), qname, null);
    		NodeSet temp = new ExtArrayNodeSet(); 
    		for (Iterator i = attribs.iterator(); i.hasNext();) {    			
                NodeProxy p = (NodeProxy) i.next();
                String langValue = p.getNodeValue();
                boolean include = lang.equalsIgnoreCase(langValue);
    			if (!include) {
                    int hyphen = langValue.indexOf('-');
    				if (hyphen != Constants.STRING_NOT_FOUND) {
    					langValue = langValue.substring(0, hyphen);
    					include = lang.equalsIgnoreCase(langValue);
    				}
    			}
    			if (include) {
                    NodeId parentID = p.getNodeId().getParentId();                
    				if (parentID != NodeId.DOCUMENT_NODE) {
                        NodeProxy parent = new NodeProxy(p.getDocument(), parentID, Node.ELEMENT_NODE);                       
    					temp.add(parent);
    				}
    			}
    		}
    		if (temp.getLength() > 0) {
    			result = ((NodeSet) contextSequence).selectAncestorDescendant(
    					temp, NodeSet.DESCENDANT, true, contextId);
    			for (Iterator i = ((NodeSet)result).iterator(); i.hasNext();) {
                    NodeProxy p = (NodeProxy) i.next();
    				p.addContextNode(contextId, p);
    			}
    		}
            else result = Sequence.EMPTY_SEQUENCE;
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;          
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET;
	}
}
