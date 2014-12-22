/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
package org.exist.xslt.expression;

import org.exist.dom.INode;
import org.exist.dom.QName;
import org.exist.interpreter.ContextAtExist;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.ValueSequence;
import org.exist.xslt.XSLContext;
import org.w3c.dom.Attr;

import javax.xml.XMLConstants;

/**
 * <!-- Category: instruction -->
 * <xsl:copy
 *   copy-namespaces? = "yes" | "no"
 *   inherit-namespaces? = "yes" | "no"
 *   use-attribute-sets? = qnames
 *   type? = qname
 *   validation? = "strict" | "lax" | "preserve" | "strip">
 *   <!-- Content: sequence-constructor -->
 * </xsl:copy>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Copy extends Declaration {

    private Boolean copy_namespaces = null;
    private Boolean inherit_namespaces = null;
    private String use_attribute_sets = null;
    private String type = null;
    private String validation = null;

    public Copy(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
		copy_namespaces = null;
		inherit_namespaces = null;
		use_attribute_sets = null;
	    type = null;
	    validation = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(COPY_NAMESPACES)) {
			copy_namespaces = getBoolean(attr.getValue());
		} else if (attr_name.equals(INHERIT_NAMESPACES)) {
			inherit_namespaces = getBoolean(attr.getValue());
		} else if (attr_name.equals(USE_ATTRIBUTE_SETS)) {
			use_attribute_sets = attr.getValue();
		} else if (attr_name.equals(TYPE)) {
			type = attr.getValue();
		} else if (attr_name.equals(VALIDATION)) {
			validation = attr.getValue();
		}
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence result = new ValueSequence();
		
		if (contextItem != null)
			contextSequence = contextItem.toSequence();

		context.pushInScopeNamespaces();
		
        MemTreeBuilder builder = context.getDocumentBuilder();

        try {
//            for (Item item : contextSequence) {
            for (SequenceIterator iterInner = contextSequence.iterate(); iterInner.hasNext();) {
                Item i = iterInner.nextItem();
                
                org.w3c.dom.Node item;
                if (i instanceof NodeValue) {
                	item = ((NodeValue) i).getNode();
				} else 
	                item = (org.w3c.dom.Node) i;

                //UNDERSTAND: strange place to workaround
            	if (item instanceof org.w3c.dom.Document) {
    				org.w3c.dom.Document document = (org.w3c.dom.Document) item;
    				item = document.getDocumentElement();
    			}
    			
    			if (item instanceof org.w3c.dom.Element) {
    				QName qn; 
    	            if (item instanceof QNameValue) {
    	                qn = ((QNameValue)item).getQName();
    	            } else {
                        qn = ((INode)item).getQName();
    	                if (qn.getPrefix() == null && context.getInScopeNamespace(XMLConstants.DEFAULT_NS_PREFIX) != null) {
                            qn = new QName(qn.getLocalPart(), context.getInScopeNamespace(XMLConstants.DEFAULT_NS_PREFIX), qn.getPrefix());
    	                }
    	            }
    				int nodeNr = builder.startElement(qn, null);
    				
    				if (use_attribute_sets != null)
    					getXSLContext().getXSLStylesheet().attributeSet(use_attribute_sets, contextSequence, i);

    				super.eval(contextSequence, i);
    				
    	            builder.endElement();
    	            NodeImpl node = builder.getDocument().getNode(nodeNr);
    	            result.add(node);
    			} else if (item instanceof org.w3c.dom.Text) {
    				int nodeNr = builder.characters(i.getStringValue());
    	            NodeImpl node = builder.getDocument().getNode(nodeNr);
    	            result.add(node);
    			} else {
    				throw new XPathException("not supported node type "+i.getType());
    			}
    		}
        } finally {
            context.popInScopeNamespaces();
        }
		return result;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:copy");

        if (copy_namespaces != null) {
        	dumper.display(" copy_namespaces = ");
        	dumper.display(copy_namespaces);
        }
        if (inherit_namespaces != null) {
        	dumper.display(" inherit_namespaces = ");
        	dumper.display(inherit_namespaces);
        }
        if (use_attribute_sets != null) {
        	dumper.display(" use_attribute_sets = ");
        	dumper.display(use_attribute_sets);
        }
        if (type != null) {
        	dumper.display(" type = ");
        	dumper.display(type);
        }
        if (validation != null) {
        	dumper.display(" validation = ");
        	dumper.display(validation);
        }

        super.dump(dumper);

        dumper.display("</xsl:copy>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:copy");
        
    	if (copy_namespaces != null)
        	result.append(" copy_namespaces = "+copy_namespaces.toString());    
    	if (inherit_namespaces != null)
        	result.append(" inherit_namespaces = "+inherit_namespaces.toString());    
    	if (use_attribute_sets != null)
        	result.append(" use_attribute_sets = "+use_attribute_sets.toString());    
    	if (type != null)
        	result.append(" type = "+type.toString());    
    	if (validation != null)
        	result.append(" validation = "+validation.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:copy> ");
        return result.toString();
    }    
}
