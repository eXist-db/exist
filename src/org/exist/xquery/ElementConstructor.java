
/*
 *  eXist Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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

import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.xquery.value.Item;
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
	    this.qnameExpr = new Atomize(context, expr);
	}
	
	public void addAttribute(AttributeConstructor attr) {
		if(attributes == null) {
			attributes = new AttributeConstructor[1];
			attributes[0] = attr;
		} else {
		    AttributeConstructor natts[] = new AttributeConstructor[attributes.length + 1];
		    System.arraycopy(attributes, 0, natts, 0, attributes.length);
		    natts[attributes.length] = attr;
		    attributes = natts;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		context.pushNamespaceContext();
		MemTreeBuilder builder = context.getDocumentBuilder();
		// process attributes
		AttributesImpl attrs = new AttributesImpl();
		if(attributes != null) {
			AttributeConstructor constructor;
			Sequence attrValues;
			QName attrQName;
			// first, search for xmlns attributes and declare in-scope namespaces
			for(int i = 0; i < attributes.length; i++) {
				constructor = (AttributeConstructor)attributes[i];
				if(constructor.isNamespaceDeclaration()) {
					int p = constructor.getQName().indexOf(':');
					if(p < 0)
						context.declareInScopeNamespace("", constructor.getLiteralValue());
					else {
						String prefix = constructor.getQName().substring(p + 1);
						context.declareInScopeNamespace(prefix, constructor.getLiteralValue());
					}
				}
			}
			// process the remaining attributesCharArr
			for(int i = 0; i < attributes.length; i++) {
			    context.proceed(this, builder);
				constructor = (AttributeConstructor)attributes[i];
				if(!constructor.isNamespaceDeclaration()) {
					attrValues = constructor.eval(contextSequence, contextItem);
					attrQName = QName.parse(context, constructor.getQName());
					attrs.addAttribute(attrQName.getNamespaceURI(), attrQName.getLocalName(),
						attrQName.toString(), "CDATA", attrValues.getStringValue());
				}
			}
		}
		context.proceed(this, builder);
		
		// create the element
		Sequence qnameSeq = qnameExpr.eval(contextSequence, contextItem);
		if(qnameSeq.getLength() != 1)
		    throw new XPathException("Type error: the node name should evaluate to a single string");
		QName qn = QName.parse(context, qnameSeq.getStringValue());
		
		int nodeNr = builder.startElement(qn, attrs);
		// process element contents
		if(content != null) {
			content.eval(contextSequence, contextItem);
		}
		builder.endElement();
		NodeImpl node = ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
		context.popNamespaceContext();
		return node;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append('<').append(qnameExpr.pprint());
		if(attributes != null) {
			AttributeConstructor attr;
			for(int i = 0; i < attributes.length; i++) {
				attr = (AttributeConstructor)attributes[i];
				buf.append(' ').append(attr.pprint());
			}
		}
		if(content == null)
			buf.append("/>");
		else {
			buf.append('>');
			buf.append(content.pprint());
			buf.append("</").append(qnameExpr.pprint()).append('>');
		}
		return buf.toString();
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
		if(content != null)
			content.resetState();
		if(attributes != null)
		    for(int i = 0; i < attributes.length; i++) {
				Expression next = (Expression)attributes[i];
				next.resetState();
			}
	}
}
