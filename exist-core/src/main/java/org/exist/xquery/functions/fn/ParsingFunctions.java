/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.fn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.util.XMLReaderPool;
import org.exist.validation.ValidationReport;
import org.exist.xquery.*;
import org.exist.xquery.functions.validation.Shared;
import org.exist.xquery.value.*;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;

public class ParsingFunctions extends BasicFunction {

    private static final String FRAGMENT_WRAPPER_NAME = "__parse-xml-fragment__";

	protected static final FunctionReturnSequenceType RESULT_TYPE_FOR_PARSE_XML = new FunctionReturnSequenceType(Type.DOCUMENT,
			Cardinality.ZERO_OR_ONE, "the parsed document");
	protected static final FunctionReturnSequenceType RESULT_TYPE_FOR_PARSE_XML_FRAGMENT = new FunctionReturnSequenceType(Type.DOCUMENT,
			Cardinality.ZERO_OR_ONE, "the parsed document fragment");

	protected static final FunctionParameterSequenceType TO_BE_PARSED_PARAMETER = new FunctionParameterSequenceType(
			"arg", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be parsed");

	protected static final Logger logger = LogManager.getLogger(ParsingFunctions.class);

	public final static FunctionSignature[] signatures = {
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

	public ParsingFunctions(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
		if (args[0].getItemCount() == 0) {
			return Sequence.EMPTY_SEQUENCE;
		}
		final String xmlContent = args[0].itemAt(0).getStringValue();
		if (xmlContent.isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}
        
        return parse(xmlContent, args);
	}


    private Sequence parse(final String xmlContent, final Sequence[] args) throws XPathException {
        final SAXAdapter adapter = new FragmentSAXAdapter(this, context, isCalledAs("parse-xml-fragment"));
	    final ValidationReport report = validate(xmlContent, adapter);

        if (report.isValid()) {
            return adapter.getDocument();
        } else {
            try {
                context.pushDocumentContext();
                final MemTreeBuilder builder = context.getDocumentBuilder();
                final NodeImpl result = Shared.writeReport(report, builder);
                throw new XPathException(this, ErrorCodes.FODC0006, ErrorCodes.FODC0006.getDescription() + ": " + report, result);
            } finally {
                context.popDocumentContext();
            }
        }
    }

    private ValidationReport validate(final String xmlContent, final SAXAdapter saxAdapter) throws XPathException {
        final String xml;
	    if (isCalledAs("parse-xml-fragment") && !xmlContent.toLowerCase().startsWith("<?xml ")) {
            xml = "<" + FRAGMENT_WRAPPER_NAME + ">" + xmlContent + "</" + FRAGMENT_WRAPPER_NAME + ">";
        } else {
	        xml = xmlContent;
        }

        final ValidationReport report = new ValidationReport();

        try (final StringReader reader = new StringReader(xml)) {
            final InputSource src = new InputSource(reader);

            final XMLReaderPool parserPool = context.getBroker().getBrokerPool().getParserPool();
            XMLReader xr = null;
            try {
                xr = parserPool.borrowXMLReader();
                xr.setErrorHandler(report);
                xr.setContentHandler(saxAdapter);
                xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, saxAdapter);
                xr.parse(src);
            } catch (final SAXException e) {
                logger.debug("Error while parsing XML: {}", e.getMessage(), e);
            } catch (final IOException e) {
                throw new XPathException(this, ErrorCodes.FODC0006, ErrorCodes.FODC0006.getDescription() + ": " + e.getMessage(),
                        new StringValue(this, xml), e);
            } finally {
                if (xr != null) {
                    parserPool.returnXMLReader(xr);
                }
            }
        }

        return report;
    }

    private static class FragmentSAXAdapter extends SAXAdapter {
        private final boolean hasFragmentWrapper;
        private boolean strippedFramentWrapper = false;

        public FragmentSAXAdapter(final XQueryContext context, final boolean hasFragmentWrapper) {
            this(null, context, hasFragmentWrapper);
        }

        public FragmentSAXAdapter(final Expression expression, final XQueryContext context, final boolean hasFragmentWrapper) {
            super(expression, context);
            this.hasFragmentWrapper = hasFragmentWrapper;
        }

        @Override
        public void startElement(final String namespaceURI, final String localName, final String qName, final Attributes atts) throws SAXException {
            if (hasFragmentWrapper && !strippedFramentWrapper && localName.equals(FRAGMENT_WRAPPER_NAME)) {
                // no-op
            } else {
                super.startElement(namespaceURI, localName, qName, atts);
            }
        }

        @Override
        public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
            if (hasFragmentWrapper && !strippedFramentWrapper && localName.equals(FRAGMENT_WRAPPER_NAME)) {
                strippedFramentWrapper = true;
            } else {
                super.endElement(namespaceURI, localName, qName);
            }
        }
    }
}
