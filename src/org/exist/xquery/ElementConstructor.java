
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

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.util.XMLChar;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Constructor for element nodes. This class handles both, direct and dynamic
 * element constructors.
 * 
 * @author wolf
 */
public class ElementConstructor extends NodeConstructor {

	private Expression qnameExpr;
	private PathExpr content = null;
	private AttributeConstructor attributes[] = null;
	private QName namespaceDecls[] = null;
	
	protected final static Logger LOG =
		Logger.getLogger(ElementConstructor.class);	
	
	public ElementConstructor(XQueryContext context) {
	    super(context);
	}
	
	public ElementConstructor(XQueryContext context, String qname) {
		super(context);
		this.qnameExpr = new LiteralValue(context, new StringValue(qname));
	}
	
	public void setContent(PathExpr path) {
		this.content = path;
	}
	
	public void setNameExpr(Expression expr) {
		//Deferred atomization (we could have a QNameValue)
	    //this.qnameExpr = new Atomize(context, expr);
		this.qnameExpr = expr;
	}
	
	public void addAttribute(AttributeConstructor attr) throws XPathException {
        if(attr.isNamespaceDeclaration()) {
            if(attr.getQName().equals("xmlns"))
                addNamespaceDecl("", attr.getLiteralValue());
            else
                addNamespaceDecl(QName.extractLocalName(attr.getQName()), attr.getLiteralValue());
        } else  if(attributes == null) {
            attributes = new AttributeConstructor[1];
            attributes[0] = attr;
        } else {
            AttributeConstructor natts[] = new AttributeConstructor[attributes.length + 1];
            System.arraycopy(attributes, 0, natts, 0, attributes.length);
            natts[attributes.length] = attr;
            attributes = natts;
        }
	}
	
	public void addNamespaceDecl(String name, String uri) throws XPathException {
        QName qn = new QName(name, uri, "xmlns");

        if (name.equalsIgnoreCase("xml")) {
            throw new XPathException("XQST0070 : can not redefine '" + qn + "'");
        }
        if (name.equalsIgnoreCase("xmlns")) {
            throw new XPathException("XQST0070 : can not redefine '" + qn + "'");
        }
        if (name.length()!=0 && uri.trim().length()==0) {
           throw new XPathException("XQST0085 : cannot undeclare a prefix "+name+".");
        }
        if(namespaceDecls == null) {
            namespaceDecls = new QName[1];
            namespaceDecls[0] = qn;
        } else {
            for(int i = 0; i < namespaceDecls.length; i++) {
                if (qn.equals(namespaceDecls[i]))
                    throw new XPathException("XQST0071 : duplicate definition for '" + qn + "'");
            }
            QName decls[] = new QName[namespaceDecls.length + 1];
            System.arraycopy(namespaceDecls, 0, decls, 0, namespaceDecls.length);
            decls[namespaceDecls.length] = qn;          
            namespaceDecls = decls;
        }
        //context.inScopeNamespaces.put(qn.getLocalName(), qn.getNamespaceURI());
	}
	
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
       context.pushInScopeNamespaces();
       // declare namespaces
       if(namespaceDecls != null) {
               for(int i = 0; i < namespaceDecls.length; i++) {
                       if ("".equals(namespaceDecls[i].getNamespaceURI())) {
                               // TODO: the specs are unclear here: should we throw XQST0085 or not?
                               context.inScopeNamespaces.remove(namespaceDecls[i].getLocalName());
//					if (context.inScopeNamespaces.remove(namespaceDecls[i].getLocalName()) == null)
//		        		throw new XPathException("XQST0085 : can not undefine '" + namespaceDecls[i] + "'");
                       } else
                               context.declareInScopeNamespace(namespaceDecls[i].getLocalName(), namespaceDecls[i].getNamespaceURI());
               }
       }
    	contextInfo.setParent(this);
        qnameExpr.analyze(contextInfo);
        if(attributes != null) {
	        for(int i = 0; i < attributes.length; i++) {
	            attributes[i].analyze(contextInfo);
	        }
        }
        if(content != null)
            content.analyze(contextInfo);
        context.popInScopeNamespaces();
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		context.pushInScopeNamespaces();
		MemTreeBuilder builder = context.getDocumentBuilder();
		// declare namespaces
		if(namespaceDecls != null) {
			for(int i = 0; i < namespaceDecls.length; i++) {
				if ("".equals(namespaceDecls[i].getNamespaceURI())) {
					// TODO: the specs are unclear here: should we throw XQST0085 or not?
					context.inScopeNamespaces.remove(namespaceDecls[i].getLocalName());
//					if (context.inScopeNamespaces.remove(namespaceDecls[i].getLocalName()) == null)
//		        		throw new XPathException("XQST0085 : can not undefine '" + namespaceDecls[i] + "'");
				} else
					context.declareInScopeNamespace(namespaceDecls[i].getLocalName(), namespaceDecls[i].getNamespaceURI());
			}
		}
		// process attributes
		AttributesImpl attrs = new AttributesImpl();
		if(attributes != null) {
			AttributeConstructor constructor;
			Sequence attrValues;
			QName attrQName;
			// first, search for xmlns attributes and declare in-scope namespaces
			for (int i = 0; i < attributes.length; i++) {
				constructor = (AttributeConstructor)attributes[i];
				if(constructor.isNamespaceDeclaration()) {
					int p = constructor.getQName().indexOf(':');
					if(p == Constants.STRING_NOT_FOUND)
						context.declareInScopeNamespace("", constructor.getLiteralValue());
					else {
						String prefix = constructor.getQName().substring(p + 1);
						context.declareInScopeNamespace(prefix, constructor.getLiteralValue());
					}
				}
			}
			// process the remaining attributes
			for (int i = 0; i < attributes.length; i++) {
			    context.proceed(this, builder);
				constructor = (AttributeConstructor)attributes[i];
				attrValues = constructor.eval(contextSequence, contextItem);
				attrQName = QName.parse(context, constructor.getQName(), "");
				if (attrs.getIndex(attrQName.getNamespaceURI(), attrQName.getLocalName()) != -1)
					throw new XPathException("XQST0040 '" + attrQName.getLocalName() + "' is a duplicate attribute name");
				attrs.addAttribute(attrQName.getNamespaceURI(), attrQName.getLocalName(),
						attrQName.getStringValue(), "CDATA", attrValues.getStringValue());
			}
		}
		context.proceed(this, builder);
		
		// create the element
		Sequence qnameSeq = qnameExpr.eval(contextSequence, contextItem);
		if(!qnameSeq.hasOne())
		    throw new XPathException("Type error: the node name should evaluate to a single item");
		
		QName qn;
		if (qnameSeq instanceof QNameValue) {
			qn = ((QNameValue)qnameSeq).getQName();
		} else {		
			//Do we have the same result than Atomize there ? -pb
			qn = QName.parse(context, qnameSeq.getStringValue());
			//Use the default namespace if specified
		 	if (qn.getPrefix() == null && context.inScopeNamespaces.get("xmlns") != null) {
	 			qn.setNamespaceURI((String)context.inScopeNamespaces.get("xmlns"));
	 		}
	 	}
		
		//Not in the specs but... makes sense
		if(!XMLChar.isValidName(qn.getLocalName()))
			throw new XPathException("XPTY0004 '" + qnameSeq.getStringValue() + "' is not a valid element name");
	 	
	 	// add namespace declaration nodes
		int nodeNr = builder.startElement(qn, attrs);
		if(namespaceDecls != null) {
			for(int i = 0; i < namespaceDecls.length; i++) {
				builder.namespaceNode(namespaceDecls[i]);
			}
		}
		// process element contents
		if(content != null) {
            content.eval(contextSequence, contextItem);
		}
		builder.endElement();
		NodeImpl node = ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
		context.popInScopeNamespaces();
		return node;
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("element ");
        //TODO : remove curly braces if Qname
        dumper.display("{");
        qnameExpr.dump(dumper);
        dumper.display("} ");
        dumper.display("{");
        dumper.startIndent();
        if(attributes != null) {
			AttributeConstructor attr;
			for(int i = 0; i < attributes.length; i++) {
			    if(i > 0)
			        dumper.nl();
				attr = (AttributeConstructor)attributes[i];
				attr.dump(dumper);
			}
	        dumper.endIndent();
	        dumper.startIndent();
	    }
        if(content != null) {
            for(Iterator i = content.steps.iterator(); i.hasNext(); ) {
                Expression expr = (Expression) i.next();
                expr.dump(dumper);
                if(i.hasNext())
                    dumper.nl();
            }
            dumper.endIndent().nl();
        }        
        dumper.display("} ");
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append("element ");
        //TODO : remove curly braces if Qname
        result.append("{");    
    	result.append(qnameExpr.toString());
        result.append("} ");    
        result.append("{");        
        if(attributes != null) {
			AttributeConstructor attr;
			for(int i = 0; i < attributes.length; i++) {
			    if(i > 0)
			    	result.append(" ");
				attr = (AttributeConstructor)attributes[i];
				result.append(attr.toString());
			}
		}
        if(content != null) {
            for(Iterator i = content.steps.iterator(); i.hasNext(); ) {
                Expression expr = (Expression) i.next();
                result.append(expr.toString());
                if(i.hasNext())
                	result.append(" ");
            }
        }        
        result.append("} ");
        return result.toString();
    }    
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#setPrimaryAxis(int)
	 */
	public void setPrimaryAxis(int axis) {
		if(content != null)
			content.setPrimaryAxis(axis);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState() {
		super.resetState();
		qnameExpr.resetState();
		if(content != null)
			content.resetState();
		if(attributes != null)
		    for(int i = 0; i < attributes.length; i++) {
				Expression next = (Expression)attributes[i];
				next.resetState();
			}
	}
}
