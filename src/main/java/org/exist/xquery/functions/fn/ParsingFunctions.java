/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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
package org.exist.xquery.functions.fn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.util.XMLReaderPool;
import org.exist.validation.ValidationReport;
import org.exist.xquery.*;
import org.exist.xquery.functions.validation.Shared;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;

public class ParsingFunctions extends BasicFunction {

	protected static final FunctionReturnSequenceType RESULT_TYPE_FOR_PARSE_XML = new FunctionReturnSequenceType(Type.DOCUMENT,
			Cardinality.ZERO_OR_ONE, "the parsed document");
	protected static final FunctionReturnSequenceType RESULT_TYPE_FOR_PARSE_XML_FRAGMENT = new FunctionReturnSequenceType(Type.DOCUMENT,
			Cardinality.ZERO_OR_ONE, "the parsed document fragment");	

	protected static final FunctionParameterSequenceType TO_BE_PARSED_PARAMETER = new FunctionParameterSequenceType(
			"arg", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be parsed");

	protected static final Logger logger = LogManager.getLogger(ParsingFunctions.class);

	public final static FunctionSignature signatures[] = {
			new FunctionSignature(
					new QName("parse-xml", Function.BUILTIN_FUNCTION_NS),
					"This function takes as input an XML document represented as a string,"
							+ " and returns the document node at the root of an XDM tree representing the parsed document.",
					new SequenceType[] { TO_BE_PARSED_PARAMETER }, RESULT_TYPE_FOR_PARSE_XML),
			new FunctionSignature(
					new QName("parse-xml-fragment", Function.BUILTIN_FUNCTION_NS),
					"This function takes as input an XML external entity represented as a string," +
					"and returns the document node at the root of an XDM tree representing the parsed document fragment.",
					new SequenceType[] { TO_BE_PARSED_PARAMETER }, RESULT_TYPE_FOR_PARSE_XML_FRAGMENT) };

	public ParsingFunctions(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		
		Sequence resultSequence;

		if (args[0].getItemCount() == 0) {
			return Sequence.EMPTY_SEQUENCE;
		}
		String xmlContent = args[0].itemAt(0).getStringValue();
		if (xmlContent.length() == 0) {
			return Sequence.EMPTY_SEQUENCE;
		}

		if (isCalledAs("parse-xml-fragment")) {
			xmlContent = "<root>" + xmlContent + "</root>";
		}
		
		final StringReader reader = new StringReader(xmlContent);
        final InputSource src = new InputSource(reader);
        
        return parse(src, context, args);
	}

    private Sequence parse(final InputSource src, XQueryContext theContext, Sequence[] args) throws XPathException {
        Sequence resultSequence;
        final ValidationReport report = new ValidationReport();
        final SAXAdapter adapter = new SAXAdapter(theContext);

        final XMLReaderPool parserPool = context.getBroker().getBrokerPool().getParserPool();
        XMLReader xr = null;
        try {
            xr = parserPool.borrowXMLReader();
            xr.setErrorHandler(report);
            xr.setContentHandler(adapter);
            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            xr.parse(src);
        } catch (final SAXException e) {
            logger.debug("Error while parsing XML: " + e.getMessage(), e);
        } catch (final IOException e) {
            throw new XPathException(this, ErrorCodes.FODC0006, ErrorCodes.FODC0006.getDescription() + ": " + e.getMessage(),
                    args[0], e);
        } finally {
            if (xr != null) {
                parserPool.returnXMLReader(xr);
            }
        }
        
        if (report.isValid()) {
            if (isCalledAs("parse-xml-fragment")) {
                resultSequence = new ValueSequence();
                NodeList children = adapter.getDocument().getDocumentElement().getChildNodes();
                for (int i = 0, il = children.getLength(); i < il; i++) {
                    Node child = children.item(i);
                    resultSequence.add((NodeValue)child);
                }
                
                return resultSequence;
            } else {
                return (DocumentImpl) adapter.getDocument();
            }
        } else {
            final MemTreeBuilder builder = theContext.getDocumentBuilder();
            final NodeImpl result = Shared.writeReport(report, builder);
            throw new XPathException(this, ErrorCodes.FODC0006, ErrorCodes.FODC0006.getDescription() + ": " + report.toString(), result);
        }
    }
}
