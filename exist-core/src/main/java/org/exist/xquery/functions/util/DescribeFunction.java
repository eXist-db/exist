/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.*;
import org.exist.xquery.Module;
import org.exist.xquery.functions.inspect.InspectFunction;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.XMLConstants;

/**
 * Describe a built-in function identified by its QName.
 * 
 * @author wolf
 */
public class DescribeFunction extends Function {
	
	protected static final Logger logger = LogManager.getLogger(DescribeFunction.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("describe-function", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Describes a built-in function. Returns an element describing the " +
			"function signature.",
			new SequenceType[] {
				new FunctionParameterSequenceType("function-name", Type.QNAME, Cardinality.EXACTLY_ONE, "The name of the function to get the signature of"),
			},
			new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the signature of the function"),
            InspectFunction.SIGNATURE);
	
	private final static QName ANNOTATION_QNAME = new QName("annotation", XMLConstants.NULL_NS_URI);
	private final static QName ANNOTATION_VALUE_QNAME = new QName("value", XMLConstants.NULL_NS_URI);
	
	public DescribeFunction(XQueryContext context) {
		super(context, signature);
	}
	
	@Override
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		
		final String fname = getArgument(0).eval(contextSequence, contextItem).getStringValue();
		final QName qname;
		try {
			qname = QName.parse(context, fname, context.getDefaultFunctionNamespace());
		} catch (final QName.IllegalQNameException e) {
			throw new XPathException(this, ErrorCodes.XPST0081, "No namespace defined for prefix " + fname);
		}
		final String uri = qname.getNamespaceURI();
		
		final MemTreeBuilder builder = context.getDocumentBuilder();
		final AttributesImpl attribs = new AttributesImpl();
		attribs.addAttribute("", "name", "name", "CDATA", qname.getStringValue());
		attribs.addAttribute("", "module", "module", "CDATA", uri);
		final int nodeNr = builder.startElement("", "function", "function", attribs);
		
		FunctionSignature signature;
		final Module module = context.getModule(uri);
		if(module != null) {
			final Iterator<FunctionSignature> i = module.getSignaturesForFunction(qname);
			while(i.hasNext()) {
				signature = i.next();
				writeSignature(signature, builder);
			}
		} else {
			final Iterator<FunctionSignature> i = context.getSignaturesForFunction(qname);
			while(i.hasNext()) {
				signature = i.next();
				writeSignature(signature, builder);
			}
		}
		builder.endElement();
		return ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
	}

	/**
	 * @param signature
	 * @param builder
	 * @throws XPathException if an internal error occurs
	 */
	private void writeSignature(FunctionSignature signature, MemTreeBuilder builder) throws XPathException {
		final AttributesImpl attribs = new AttributesImpl();
		attribs.addAttribute("", "arguments", "arguments", "CDATA", Integer.toString(signature.getArgumentCount()));
		builder.startElement("", "prototype", "prototype", attribs);
		attribs.clear();
		builder.startElement("", "signature", "signature", attribs);
		builder.characters(signature.toString());
		builder.endElement();
		
		writeAnnotations(signature, builder);
		
		if(signature.getDescription() != null) {
			builder.startElement("", "description", "description", attribs);

            final StringBuilder description = new StringBuilder();
            description.append(signature.getDescription());

            description.append("\n\n");
            
            final SequenceType argumentTypes[] = signature.getArgumentTypes();
            
            if(argumentTypes != null && argumentTypes.length>0){

                final StringBuilder args = new StringBuilder();
                int noArgs=0;
                
                for (final SequenceType argumentType : argumentTypes) {
                    if (argumentType instanceof FunctionParameterSequenceType) {
                        noArgs++;
                        final FunctionParameterSequenceType fp
                                = (FunctionParameterSequenceType) argumentType;
                        args.append("$");
                        args.append(fp.getAttributeName());
                        args.append(" : ");
                        args.append(fp.getDescription());
                        args.append("\n");
                    }
                }

                // only add if there were good arguments
                if(noArgs>0){
                    description.append("Parameters:\n");
                    description.append(args);
                }
            }

            final SequenceType returnType = signature.getReturnType();
            if(returnType != null){             
                if (returnType instanceof FunctionReturnSequenceType) {
                    description.append("\n");
                    description.append("Returns ");
                    final FunctionReturnSequenceType fp
                                = (FunctionReturnSequenceType) returnType;
                        description.append(fp.getDescription());
                        description.append("\n");
                }

            }
            
            builder.characters(description.toString());
			builder.endElement();
		}
        
		if (signature.getDeprecated() != null) {
			builder.startElement("", "deprecated", "deprecated", attribs);
			builder.characters(signature.getDeprecated());
			builder.endElement();
		}
        
		builder.endElement();
	}

	private void writeAnnotations(FunctionSignature signature, MemTreeBuilder builder) throws XPathException {
		final AttributesImpl attribs = new AttributesImpl();
		final Annotation[] annots = signature.getAnnotations();
		if (annots != null) {
			for (final Annotation annot : annots) {
				attribs.clear();
				attribs.addAttribute(null, "name", "name", "CDATA", annot.getName().toString());
				attribs.addAttribute(null, "namespace", "namespace", "CDATA", annot.getName().getNamespaceURI());
				builder.startElement(ANNOTATION_QNAME, attribs);
				final LiteralValue[] value = annot.getValue();
				if (value != null) {
					for (final LiteralValue literal : value) {
						builder.startElement(ANNOTATION_VALUE_QNAME, null);
						builder.characters(literal.getValue().getStringValue());
						builder.endElement();
					}
				}
				builder.endElement();
			}
		}
	}
}
