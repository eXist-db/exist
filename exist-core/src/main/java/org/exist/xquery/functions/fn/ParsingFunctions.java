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
import org.exist.dom.memtree.SAXAdapter;
import org.exist.util.XMLReaderPool;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;

public class ParsingFunctions extends BasicFunction {

    private static final String FRAGMENT_WRAPPER_NAME = "__parse-xml-fragment__";

	protected static final FunctionReturnSequenceType PARSE_RESULT_TYPE = new FunctionReturnSequenceType(Type.DOCUMENT,
			Cardinality.ZERO_OR_ONE, "the document node with the parsed result");

	protected static final FunctionParameterSequenceType TO_BE_PARSED_PARAMETER = new FunctionParameterSequenceType(
			"value", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be parsed");

	protected static final Logger logger = LogManager.getLogger(ParsingFunctions.class);

	public final static FunctionSignature[] signatures = {
			new FunctionSignature(
					new QName("parse-xml", Function.BUILTIN_FUNCTION_NS),
					"Parse an XML document represented as a string. "
							+ "Returns the document node with the parsed document.",
					new SequenceType[] { TO_BE_PARSED_PARAMETER },
                    PARSE_RESULT_TYPE
            ),
			new FunctionSignature(
					new QName("parse-xml-fragment", Function.BUILTIN_FUNCTION_NS),
					"Parse an XML fragment represented as a string. "
                            + "External entities must have been parsed before. "
					        + "Returns the document node with the parsed document fragment.",
					new SequenceType[] { TO_BE_PARSED_PARAMETER },
                    PARSE_RESULT_TYPE
            )
    };

	public ParsingFunctions(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
		if (args[0].getItemCount() == 0) {
			return Sequence.EMPTY_SEQUENCE;
		}
		final String xmlString = args[0].itemAt(0).getStringValue();

        if (isCalledAs("parse-xml-fragment")) {
            return parseFragment(xmlString);
        }
        return parseDocument(xmlString);
    }

    private Sequence parseDocument(final String xmlDocumentString) throws XPathException {
        final SAXAdapter documentAdapter = new SAXAdapter(this, context);
        parse(xmlDocumentString, documentAdapter);
        return documentAdapter.getDocument();
    }

    private Sequence parseFragment(final String xmlFragmentString) throws XPathException {
        final SAXAdapter fragmentAdapter = new FragmentSAXAdapter(this, context);
        parse(wrapXmlFragment(xmlFragmentString), fragmentAdapter);
        return fragmentAdapter.getDocument();
    }

    private void parse(final String xml, final SAXAdapter saxAdapter) throws XPathException {
        try (final StringReader reader = new StringReader(xml)) {
            final InputSource src = new InputSource(reader);

            final XMLReaderPool parserPool = context.getBroker().getBrokerPool().getXmlReaderPool();
            XMLReader xr = null;
            try {
                xr = parserPool.borrowXMLReader();
                xr.setContentHandler(saxAdapter);
                xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, saxAdapter);
                xr.parse(src);
            } catch (final IOException | SAXException e) {
                throw new XPathException(this, ErrorCodes.FODC0006,
                        this.getName().getLocalPart() + " failed with: " + e.getMessage(),
                        new StringValue(this, xml), e);
            } finally {
                if (xr != null) {
                    parserPool.returnXMLReader(xr);
                }
            }
        }
    }

    private static String wrapXmlFragment(final String xmlContent) {
        String xmlHeader = "";
        int offset = 0;
        if (xmlContent.startsWith("<?xml ")) {
            offset = xmlContent.indexOf("?>") + 2;
            xmlHeader = xmlContent.substring(0, offset);
        }
        return xmlHeader + "<" + FRAGMENT_WRAPPER_NAME + ">" + xmlContent.substring(offset) + "</" + FRAGMENT_WRAPPER_NAME + ">";
    }

    private static class FragmentSAXAdapter extends SAXAdapter {
        private boolean strippedFragmentWrapper = false;

        public FragmentSAXAdapter(final Expression expression, final XQueryContext context) {
            super(expression, context);
        }

        @Override
        public void startElement(final String namespaceURI, final String localName, final String qName, final Attributes atts) throws SAXException {
            if (strippedFragmentWrapper || !localName.equals(FRAGMENT_WRAPPER_NAME)) {
                super.startElement(namespaceURI, localName, qName, atts);
            }
            // skip fragment wrapper element
        }

        @Override
        public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
            if (strippedFragmentWrapper || !localName.equals(FRAGMENT_WRAPPER_NAME)) {
                super.endElement(namespaceURI, localName, qName);
            } else {
                strippedFragmentWrapper = true;
            }
        }

        @Override
        public void declaration(final String version, final String encoding, final String standalone) throws SAXException {
            if (standalone != null) {
                throw new SAXException("Pseudo attribute \"standalone\" not allowed in input fragment");
            }
            super.declaration(version, encoding, null);
        }

    }
}
