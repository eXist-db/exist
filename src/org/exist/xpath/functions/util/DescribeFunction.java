/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xpath.functions.util;

import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.functions.UserDefinedFunction;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Describe a built-in function identified by its QName.
 * 
 * @author wolf
 */
public class DescribeFunction extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("describe-function", UTIL_FUNCTION_NS, "util"),
			"Describes a built-in function. Returns an element describing the " +
			"function signature.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
			},
			new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE));
			
	public DescribeFunction(StaticContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		String fname = getArgument(0).eval(contextSequence, contextItem).getStringValue();
		QName qname = QName.parseFunction(context, fname);
		String uri = qname.getNamespaceURI();
		FunctionSignature signature;
		if(uri.equals(Function.BUILTIN_FUNCTION_NS) || uri.equals(Function.UTIL_FUNCTION_NS) |
			uri.equals(Function.XMLDB_FUNCTION_NS) || uri.equals(Function.REQUEST_FUNCTION_NS)) {
			Class clazz = context.getClassForFunction(qname);
			if (clazz == null)
				throw new XPathException("function " + qname.toString() + " ( namespace-uri = " + 
			qname.getNamespaceURI() + ") is not defined");
			Function func = Function.createFunction(context, clazz);
			signature = func.getSignature();
		} else {
			UserDefinedFunction func = context.resolveFunction(qname);
			signature = func.getSignature();
		}
		MemTreeBuilder builder = context.getDocumentBuilder();
		AttributesImpl attribs = new AttributesImpl();
		attribs.addAttribute("", "name", "name", "CDATA", signature.getName().toString());
		attribs.addAttribute("", "module", "module", "CDATA", signature.getName().getNamespaceURI());
		int nodeNr = builder.startElement("", "function", "function", attribs);
		attribs.clear();
		if(signature.getDescription() != null) {
			builder.startElement("", "description", "description", attribs);
			builder.characters(signature.getDescription());
			builder.endElement();
		}
		builder.startElement("", "signature", "signature", attribs);
		builder.characters(signature.toString());
		builder.endElement();
		builder.endElement();
		return ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
	}

}
