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
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.validation.ValidationReport;
import org.exist.xquery.*;
import org.exist.xquery.functions.validation.Shared;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;

public class Parse extends BasicFunction {
	
	protected static final FunctionReturnSequenceType RESULT_TYPE = new FunctionReturnSequenceType( Type.DOCUMENT, Cardinality.ZERO_OR_ONE, "the XML fragment parsed from the string" );

	protected static final FunctionParameterSequenceType TO_BE_PARSED_PARAMETER = new FunctionParameterSequenceType( "to-be-parsed", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be parsed" );

	protected static final Logger logger = LogManager.getLogger(Parse.class);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName( "parse", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Parses the passed string value into an XML fragment. The string has to be " +
            "well-formed XML. An empty sequence is returned if the argument is an " +
            "empty string or sequence. If the XML is not well-formed, the function throws an " +
            "error (EXXQDY0002). An XML-formatted description of the error is contained in the error value and " +
            "can be accessed using XQuery 3.0 try-catch statement.",
            new SequenceType[] { TO_BE_PARSED_PARAMETER },
            RESULT_TYPE
        ),
        new FunctionSignature(
            new QName( "parse-html", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Parses the passed string value into an XML fragment. The HTML string may not be " +
            "well-formed XML. It will be passed through the Neko HTML parser to make it well-formed. " +
            "An empty sequence is returned if the argument is an " +
            "empty string or sequence.",
            new SequenceType[] { TO_BE_PARSED_PARAMETER },
            RESULT_TYPE
        )
    };

    public Parse(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	
        if (args[0].getItemCount() == 0) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final String xmlContent = args[0].itemAt(0).getStringValue();
        if (xmlContent.length() == 0) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final StringReader reader = new StringReader(xmlContent);
        final ValidationReport report = new ValidationReport();
        final SAXAdapter adapter = new SAXAdapter(context);
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            final InputSource src = new InputSource(reader);

            XMLReader xr = null;
            if (isCalledAs("parse-html")) {
                try {
                    final Class<?> clazz = Class.forName( "org.cyberneko.html.parsers.SAXParser" );
                    xr = (XMLReader) clazz.newInstance();
                    //do not modify the case of elements and attributes
                    xr.setProperty("http://cyberneko.org/html/properties/names/elems", "match");
                    xr.setProperty("http://cyberneko.org/html/properties/names/attrs", "no-change");
                } catch (final Exception e) {
                    logger.warn("Could not instantiate neko HTML parser for function util:parse-html, falling back to " +
                            "default XML parser.", e);
                }
            }
            if (xr == null) {
                final SAXParser parser = factory.newSAXParser();
                xr = parser.getXMLReader();
            }

            xr.setErrorHandler(report);
            xr.setContentHandler(adapter);
            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            xr.parse(src);
        } catch (final ParserConfigurationException e) {
            throw new XPathException(this, ErrorCodes.EXXQDY0002, "Error while constructing XML parser: " + e.getMessage(), args[0], e);
        } catch (final SAXException e) {
            logger.debug("Error while parsing XML: " + e.getMessage(), e);
        } catch (final IOException e) {
            throw new XPathException(this, ErrorCodes.EXXQDY0002, "Error while parsing XML: " + e.getMessage(), args[0], e);
        }
        
        if (report.isValid())
        	{return (DocumentImpl) adapter.getDocument();}
        else {
        	final MemTreeBuilder builder = context.getDocumentBuilder();
            final NodeImpl result = Shared.writeReport(report, builder);
    		throw new XPathException(this, ErrorCodes.EXXQDY0002, report.toString(), result);
        }
    }
}
