/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2007-09 The eXist Project
 * http://exist-db.org
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
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import java.util.Arrays;
import java.util.Comparator;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.helpers.AttributesImpl;

public class ExtractDocs extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(ExtractDocs.class);

    public final static FunctionSignature signature =
        new FunctionSignature(
			new QName("extract-docs", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns an XML document which describes the functions available in a given module. " +
            "The module is identified through its module namespace URI, which is passed as an argument. " +
            "The function returns a module documentation in XQDoc format.",
			new SequenceType[] {
                    new FunctionParameterSequenceType("uri", Type.STRING, Cardinality.EXACTLY_ONE, "The namespace URI of the function module")
            },
			new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the xqdocs for the function module"));

    private final String XQDOC_NS = "http://www.xqdoc.org/1.0";

    public ExtractDocs(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        String moduleURI = args[0].getStringValue();
        Module module = context.getModule(moduleURI);
        if (module == null) {
            return Sequence.EMPTY_SEQUENCE;
        }
        MemTreeBuilder builder = context.getDocumentBuilder();
        int nodeNr = builder.startElement(XQDOC_NS, "xqdoc", "xqdoc", null);
        module(module, builder);
        functions(module, builder);
        builder.endElement();
        return ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
    }

	private void functions(Module module, MemTreeBuilder builder) {
		builder.startElement(XQDOC_NS, "functions", "functions", null);
		FunctionSignature[] functions = module.listFunctions();
		Arrays.sort(functions, new FunctionSignatureComparator());
		for (int i = 0; i < functions.length; i++) {
			FunctionSignature function = functions[i];
			builder.startElement(XQDOC_NS, "function", "function", null);
			simpleElement(builder, "name", function.getName().getLocalName());
			simpleElement(builder, "signature", function.toString());
			builder.startElement(XQDOC_NS, "comment", "comment", null);
			String functionDescription = function.getDescription();
			simpleElement(builder, "description", functionDescription);
			int index = -1;
			if (function.getArgumentTypes() != null) {
				for (SequenceType parameter : function.getArgumentTypes()) {
					simpleElement(builder, "param", parameterText(parameter, ++index));
				}
			} else {
				for (; index < function.getArgumentCount();) {
					simpleElement(builder, "param", parameterText(null, ++index));
				}
			}
			if (function.isOverloaded()) {
				simpleElement(builder, "param", "overloaded");
			}
			SequenceType returnValue = function.getReturnType();
			if (returnValue instanceof FunctionReturnSequenceType) {
				simpleElement(builder, "return", ((FunctionReturnSequenceType) returnValue).getDescription());
			}

			String deprecated = function.getDeprecated();
			if (deprecated != null && deprecated.length() > 0) {
				simpleElement(builder, "deprecated", deprecated);
			}
			builder.endElement();
			builder.endElement();
		}
		builder.endElement();
	}
    
    private String parameterText(SequenceType parameter, int index) {
        char var = 'a';
    	StringBuilder buf = new StringBuilder("$");
        if (parameter != null && parameter instanceof FunctionParameterSequenceType) {
        	FunctionParameterSequenceType funcType = (FunctionParameterSequenceType)parameter;
        	buf.append(funcType.getAttributeName());
        		buf.append(" ");
        		buf.append(funcType.getDescription());
        } else {
        	buf.append((char)(var + index));
        }
    	return buf.toString();
    }

    private void module(Module module, MemTreeBuilder builder) {
        AttributesImpl attribs = new AttributesImpl();
        attribs.addAttribute("", "type", "type", "CDATA", "library");
        builder.startElement(XQDOC_NS, "module", "module", attribs);
        simpleElement(builder, "uri", module.getNamespaceURI());
        simpleElement(builder, "name", module.getDefaultPrefix());
        builder.startElement(XQDOC_NS, "comment", "comment", null);
        simpleElement(builder, "description", module.getDescription());
        try {
			simpleElement(builder, "release-version", module.getReleaseVersion());
        } catch (AbstractMethodError e) {
			logger.error("Problem with function module for [" + module.getNamespaceURI() + "]", e);
		} catch (Exception e) {
			logger.error("Problem with function module for [" + module.getNamespaceURI() + "]", e);
		}
        builder.endElement();
        builder.endElement();
    }

    private void simpleElement(MemTreeBuilder builder, String tag, String value) {
        builder.startElement(XQDOC_NS, tag, tag, null);
        builder.characters(value == null ? "" : value);
        builder.endElement();
    }
}

//////////////////////////////////////////////////FunctionSignatureComparator
//To sort directories before funcSigs, then alphabetically.
class FunctionSignatureComparator implements Comparator<FunctionSignature> {

 // Comparator interface requires defining compare method.
 public int compare(FunctionSignature funcSiga, FunctionSignature funcSigb) {
     //... Sort directories before funcSigs,
     //    otherwise alphabetical ignoring case.
     return funcSiga.toString().compareToIgnoreCase(funcSigb.toString());
 }
}
