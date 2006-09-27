/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
package org.exist.xquery.functions.util;

import java.util.Iterator;

import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Describe a built-in function identified by its QName.
 * 
 * @author wolf
 */
public class DescribeFunction extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("describe-function", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Describes a built-in function. Returns an element describing the " +
			"function signature.",
			new SequenceType[] {
				new SequenceType(Type.QNAME, Cardinality.EXACTLY_ONE),
			},
			new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE));
			
	public DescribeFunction(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		String fname = getArgument(0).eval(contextSequence, contextItem).getStringValue();
		QName qname = QName.parse(context, fname, context.getDefaultFunctionNamespace());
		String uri = qname.getNamespaceURI();
		
		MemTreeBuilder builder = context.getDocumentBuilder();
		AttributesImpl attribs = new AttributesImpl();
		attribs.addAttribute("", "name", "name", "CDATA", qname.getStringValue());
		attribs.addAttribute("", "module", "module", "CDATA", uri);
		int nodeNr = builder.startElement("", "function", "function", attribs);
		
		FunctionSignature signature;
		Module module = context.getModule(uri);
		if(module != null) {
			Iterator i = module.getSignaturesForFunction(qname);
			while(i.hasNext()) {
				signature = (FunctionSignature) i.next();
				writeSignature(signature, builder);
			}
		} else {
			Iterator i = context.getSignaturesForFunction(qname);
			while(i.hasNext()) {
				signature = (FunctionSignature) i.next();
				writeSignature(signature, builder);
			}
		}
		builder.endElement();
		return ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
	}

	/**
	 * @param signature
	 * @param builder
	 * @param attribs
	 */
	private void writeSignature(FunctionSignature signature, MemTreeBuilder builder) {
		AttributesImpl attribs = new AttributesImpl();
		attribs.addAttribute("", "arguments", "arguments", "CDATA", Integer.toString(signature.getArgumentCount()));
		builder.startElement("", "prototype", "prototype", attribs);
		attribs.clear();
		builder.startElement("", "signature", "signature", attribs);
		builder.characters(signature.toString());
		builder.endElement();
		if(signature.getDescription() != null) {
			builder.startElement("", "description", "description", attribs);
			builder.characters(signature.getDescription());
			builder.endElement();
		}
		if (signature.getDeprecated() != null) {
			builder.startElement("", "deprecated", "deprecated", attribs);
			builder.characters(signature.getDeprecated());
			builder.endElement();
		}
		builder.endElement();
	}

}
