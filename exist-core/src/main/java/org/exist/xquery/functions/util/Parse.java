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
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.util.HtmlToXmlParser;
import com.evolvedbinary.j8fu.Either;
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

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;

public class Parse extends BasicFunction {

    private static final FunctionReturnSequenceType RESULT_TYPE = new FunctionReturnSequenceType( Type.DOCUMENT, Cardinality.ZERO_OR_ONE, "the XML fragment parsed from the string" );

	private static final FunctionParameterSequenceType TO_BE_PARSED_PARAMETER = new FunctionParameterSequenceType( "to-be-parsed", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be parsed" );

	private static final Logger logger = LogManager.getLogger(Parse.class);

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName( "parse-html", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Parses the passed string value into an XML fragment. The HTML string may not be " +
            "well-formed XML. It will be passed through the Neko HTML parser to make it well-formed. " +
            "An empty sequence is returned if the argument is an " +
            "empty string or sequence.",
            new SequenceType[] { TO_BE_PARSED_PARAMETER },
            RESULT_TYPE
        );

    public Parse(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

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
        XMLReader xr = null;
        try {
            final InputSource src = new InputSource(reader);

            final Optional<Either<Throwable, XMLReader>> maybeReaderInst = HtmlToXmlParser.getHtmlToXmlParser(context.getBroker().getConfiguration());

            if(maybeReaderInst.isPresent()) {
                final Either<Throwable, XMLReader> readerInst = maybeReaderInst.get();
                if (readerInst.isLeft()) {
                    final String msg = "Unable to parse HTML to XML please ensure the parser is configured in conf.xml and is present on the classpath";
                    final Throwable t = readerInst.left().get();
                    LOG.error(msg, t);
                    throw new XPathException(this, ErrorCodes.EXXQDY0002, t);
                } else {
                    xr = readerInst.right().get();
                }
            } else {
                throw new XPathException(this, ErrorCodes.EXXQDY0002, "There is no HTML to XML parser configured in conf.xml");
            }

            xr.setErrorHandler(report);
            xr.setContentHandler(adapter);
            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            xr.parse(src);
        } catch (final SAXException e) {
            logger.debug("Error while parsing XML: " + e.getMessage(), e);
        } catch (final IOException e) {
            throw new XPathException(this, ErrorCodes.EXXQDY0002, "Error while parsing XML: " + e.getMessage(), args[0], e);
        } finally {
            if (!isCalledAs("parse-html") && xr != null) {
                context.getBroker().getBrokerPool().getParserPool().returnXMLReader(xr);
            }
        }

        if (report.isValid()) {
            return adapter.getDocument();
        } else {
        	final MemTreeBuilder builder = context.getDocumentBuilder();
            final NodeImpl result = Shared.writeReport(report, builder);
    		throw new XPathException(this, ErrorCodes.EXXQDY0002, report.toString(), result);
        }
    }
}
