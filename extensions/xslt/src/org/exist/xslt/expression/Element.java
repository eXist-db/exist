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

import java.util.Set;

import org.exist.dom.QName;
import org.exist.interpreter.ContextAtExist;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.util.XMLChar;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.AttributeConstructor;
import org.exist.xquery.Constants;
import org.exist.xquery.ElementConstructor;
import org.exist.xquery.LiteralValue;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xslt.ErrorCodes;
import org.exist.xslt.XSLContext;
import org.w3c.dom.Attr;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <!-- Category: instruction -->
 * <xsl:element
 *   name = { qname }
 *   namespace? = { uri-reference }
 *   inherit-namespaces? = "yes" | "no"
 *   use-attribute-sets? = qnames
 *   type? = qname
 *   validation? = "strict" | "lax" | "preserve" | "strip">
 *   <!-- Content: sequence-constructor -->
 * </xsl:element>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Element extends SimpleConstructor {

    private String name = null;
    private String namespace = null;
    private Boolean inherit_namespaces = null;
    private String use_attribute_sets = null;
    private String type = null;
    private String validation = null;
    
	private PathExpr content = null;

    public Element(XSLContext context) {
		super(context);
	}

    public Element(XSLContext context, String name) {
		super(context);
		
		this.name = name;
	}
    
	public void setContent(PathExpr content) {
		this.content = content;
	}
	
	public void addAttribute(Attr attr) {
		// TODO Auto-generated method stub
		
	}

	public void setToDefaults() {
		name = null;
		namespace = null;
		inherit_namespaces = null;
		use_attribute_sets = null;
	    type = null;
	    validation = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(NAME)) {
			name = attr.getValue();
		} else if (attr_name.equals(NAMESPACE)) {
			namespace = attr.getValue();
		} else if (attr_name.equals(INHERIT_NAMESPACES)) {
			inherit_namespaces = getBoolean(attr.getValue());
		} else if (attr_name.equals(USE_ATTRIBUTE_SETS)) {
			use_attribute_sets = attr.getValue();
		} else if (attr_name.equals(TYPE)) {
			type = attr.getValue();
		} else if (attr_name.equals(VALIDATION)) {
			validation = attr.getValue();
		} else {
			addAttribute(attr);
		}
	}
	
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        context.pushInScopeNamespaces();
        // declare namespaces
//        if(namespaceDecls != null) {
//            for(int i = 0; i < namespaceDecls.length; i++) {
//                if ("".equals(namespaceDecls[i].getNamespaceURI())) {
//                    // TODO: the specs are unclear here: should we throw XQST0085 or not?
//                    context.inScopeNamespaces.remove(namespaceDecls[i].getLocalName());
////					if (context.inScopeNamespaces.remove(namespaceDecls[i].getLocalName()) == null)
////		        		throw new XPathException(getASTNode(), "XQST0085 : can not undefine '" + namespaceDecls[i] + "'");
//                } else
//                    context.declareInScopeNamespace(namespaceDecls[i].getLocalName(), namespaceDecls[i].getNamespaceURI());
//            }
//        }
        AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.setParent(this);
        newContextInfo.addFlag(IN_NODE_CONSTRUCTOR);
//        qnameExpr.analyze(newContextInfo);
//        if(attributes != null) {
//            for(int i = 0; i < attributes.length; i++) {
//                attributes[i].analyze(newContextInfo);
//            }
//        }

        //analyze content
        if (content != null)
        	content.analyze(contextInfo);
        super.analyze(contextInfo);

        context.popInScopeNamespaces();
	}
	
//	private boolean internalCall = false;
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
//		if (!internalCall) {
//			internalCall = true;
//			return constructor.eval(contextSequence, contextItem);
//		}
//		internalCall = false;
//		return super.eval(contextSequence, contextItem); 

        context.expressionStart(this);
		context.pushInScopeNamespaces();
        if (newDocumentContext)
            context.pushDocumentContext();
        try {
            MemTreeBuilder builder = context.getDocumentBuilder();
            
            // declare namespaces
//            if(namespaceDecls != null) {
//                for(int i = 0; i < namespaceDecls.length; i++) {
//                    //if ("".equals(namespaceDecls[i].getNamespaceURI())) {
//                        // TODO: the specs are unclear here: should we throw XQST0085 or not?
//                    //	context.inScopeNamespaces.remove(namespaceDecls[i].getLocalName());
////					if (context.inScopeNamespaces.remove(namespaceDecls[i].getLocalName()) == null)
////		        		throw new XPathException(getAS      TNode(), "XQST0085 : can not undefine '" + namespaceDecls[i] + "'");
//                    //} else
//                        context.declareInScopeNamespace(namespaceDecls[i].getLocalName(), namespaceDecls[i].getNamespaceURI());
//                }
//            }
//            AttributesImpl attrs = new AttributesImpl();
//            if(attributes != null) {
//                AttributeConstructor constructor;
//                Sequence attrValues;
//                QName attrQName;
//                // first, search for xmlns attributes and declare in-scope namespaces
//                for (int i = 0; i < attributes.length; i++) {
//                    constructor = attributes[i];
//                    if(constructor.isNamespaceDeclaration()) {
//                        int p = constructor.getQName().indexOf(':');
//                        if(p == Constants.STRING_NOT_FOUND)
//                            context.declareInScopeNamespace("", constructor.getLiteralValue());
//                        else {
//                            String prefix = constructor.getQName().substring(p + 1);
//                            context.declareInScopeNamespace(prefix, constructor.getLiteralValue());
//                        }
//                    }
//                }
//                // process the remaining attributes
//                for (int i = 0; i < attributes.length; i++) {
//                    context.proceed(this, builder);
//                    constructor = attributes[i];
//                    attrValues = constructor.eval(contextSequence, contextItem);
//                    attrQName = QName.parse(context, constructor.getQName(), "");
//                    if (attrs.getIndex(attrQName.getNamespaceURI(), attrQName.getLocalName()) != -1)
//                        throw new XPathException(this, "XQST0040 '" + attrQName.getLocalName() + "' is a duplicate attribute name");
//                    attrs.addAttribute(attrQName.getNamespaceURI(), attrQName.getLocalName(),
//                            attrQName.getStringValue(), "CDATA", attrValues.getStringValue());
//                }
//            }
            context.proceed(this, builder);

            // create the element
//            Sequence qnameSeq = qnameExpr.eval(contextSequence, contextItem);
//            if(!qnameSeq.hasOne())
//		    throw new XPathException(this, "Type error: the node name should evaluate to a single item");
//            Item qnitem = qnameSeq.itemAt(0);
            QName qn;
//            if (qnitem instanceof QNameValue) {
//                qn = ((QNameValue)qnitem).getQName();
//            } else {
//                //Do we have the same result than Atomize there ? -pb
			try {
				qn = QName.parse(context, name);
			} catch (IllegalArgumentException e) {
				throw new XPathException(this, ErrorCodes.XPTY0004, "'"+name+"' is not a valid element name");
			}
//            	
//                //Use the default namespace if specified
//                /*
//                 if (qn.getPrefix() == null && context.inScopeNamespaces.get("xmlns") != null) {
//                     qn.setNamespaceURI((String)context.inScopeNamespaces.get("xmlns"));
//                 }
//                 */
//                if (qn.getPrefix() == null && context.getInScopeNamespace("") != null) {
//                     qn.setNamespaceURI(context.getInScopeNamespace(""));
//                }
//             }
//
            //Not in the specs but... makes sense
            if(!XMLChar.isValidName(name))
            	throw new XPathException(this, ErrorCodes.XPTY0004, "'" + name + "' is not a valid element name");

            int nodeNr = builder.startElement(qn, null);

            // process attributes
            if (use_attribute_sets != null) {
            	Set<AttributeSet> sets = ((XSLContext)context).getXSLStylesheet().getAttributeSet(use_attribute_sets);
            	for (AttributeSet set  : sets) {
            		set.eval(contextSequence, contextItem);
            	}
            }

            // add namespace declaration nodes
//            if(namespaceDecls != null) {
//                for(int i = 0; i < namespaceDecls.length; i++) {
//                    builder.namespaceNode(namespaceDecls[i]);
//                }
//            }
//            // do we need to add a namespace declaration for the current node?
//            if (qn.needsNamespaceDecl()) {
//                if (context.getInScopePrefix(qn.getNamespaceURI()) == null) {
//                    String prefix = qn.getPrefix();
//                    if (prefix == null || prefix.length() == 0)
//                        prefix = "";
//                    context.declareInScopeNamespace(prefix, qn.getNamespaceURI());
//                    builder.namespaceNode(new QName(prefix, qn.getNamespaceURI(), "xmlns"));
//                }
//            } else if ((qn.getPrefix() == null || qn.getPrefix().length() == 0) &&
//                context.getInheritedNamespace("") != null) {
//                context.declareInScopeNamespace("", "");
//                builder.namespaceNode(new QName("", "", "xmlns"));
//            }
            // process element contents
            if(content != null) {
                content.eval(contextSequence, contextItem);
            }
            builder.endElement();
            NodeImpl node = builder.getDocument().getNode(nodeNr);
            return node;
        } finally {
            context.popInScopeNamespaces();
            if (newDocumentContext)
                context.popDocumentContext();
            context.expressionEnd(this);
        }
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:element");

        if (name != null) {
        	dumper.display(" name = ");
        	dumper.display(name);
        }
        if (namespace != null) {
        	dumper.display(" namespace = ");
        	dumper.display(namespace);
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
        dumper.display(">");

        super.dump(dumper);

        dumper.display("</xsl:element>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:element");
        
    	if (name != null)
        	result.append(" name = "+name.toString());    
    	if (namespace != null)
        	result.append(" namespace = "+namespace.toString());    
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

        result.append("</xsl:element> ");
        return result.toString();
    }

}
