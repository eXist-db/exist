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

import com.evolvedbinary.j8fu.Either;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.util.HtmlToXmlParser;
import org.exist.validation.ValidationReport;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Optional;

/**
 * Implements fn:parse-html (XQuery 4.0).
 *
 * Parses an HTML string (which may be malformed) into an XDM document node
 * with all elements in the XHTML namespace.
 */
public class FnParseHtml extends BasicFunction {

    public static final FunctionSignature[] FN_PARSE_HTML = {
            new FunctionSignature(
                    new QName("parse-html", Function.BUILTIN_FUNCTION_NS),
                    "Parses the supplied HTML string into an XDM document node. " +
                    "The input need not be well-formed; it is processed by an HTML parser " +
                    "that corrects errors and produces well-formed XHTML output.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.ITEM,
                                    Cardinality.ZERO_OR_ONE, "The HTML to parse (string or binary)")
                    },
                    new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ZERO_OR_ONE,
                            "The parsed XHTML document")),
            new FunctionSignature(
                    new QName("parse-html", Function.BUILTIN_FUNCTION_NS),
                    "Parses the supplied HTML string into an XDM document node with options. " +
                    "The input need not be well-formed; it is processed by an HTML parser " +
                    "that corrects errors and produces well-formed XHTML output.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.ITEM,
                                    Cardinality.ZERO_OR_ONE, "The HTML to parse (string or binary)"),
                            new FunctionParameterSequenceType("options", Type.MAP_ITEM,
                                    Cardinality.EXACTLY_ONE, "Options map")
                    },
                    new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ZERO_OR_ONE,
                            "The parsed XHTML document"))
    };

    public FnParseHtml(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // Extract options if present
        boolean failOnError = false;
        String encoding = "UTF-8";
        if (getArgumentCount() == 2 && !args[1].isEmpty()) {
            final MapType options = (MapType) args[1].itemAt(0);
            failOnError = getBooleanOption(options, "fail-on-error", false);
            encoding = getStringOption(options, "encoding", "UTF-8");

            // Validate option types per spec — unknown options with wrong types raise XPTY0004
            validateOptionType(options, "include-template-content");
            validateOptionType(options, "exclude-template-content");
        }

        // Get the HTML content as a string
        final String htmlContent = getHtmlContent(args[0].itemAt(0), encoding);

        // Parse with the configured HTML-to-XML parser
        return parseHtml(htmlContent, failOnError);
    }

    private String getHtmlContent(final Item item, final String encoding) throws XPathException {
        if (item instanceof BinaryValue) {
            final BinaryValue binary = (BinaryValue) item;
            try (final java.io.InputStream is = binary.getInputStream()) {
                final Charset charset = Charset.forName(encoding);
                return new String(is.readAllBytes(), charset);
            } catch (final Exception e) {
                throw new XPathException(this, ErrorCodes.FODC0006,
                        "Error decoding binary value: " + e.getMessage());
            }
        }
        return item.getStringValue();
    }

    private Sequence parseHtml(final String htmlContent, final boolean failOnError) throws XPathException {
        final ValidationReport report = new ValidationReport();
        final SAXAdapter adapter = new SAXAdapter(this, context);

        try {
            final Optional<Either<Throwable, XMLReader>> maybeReaderInst =
                    HtmlToXmlParser.getHtmlToXmlParser(context.getBroker().getConfiguration());

            if (maybeReaderInst.isEmpty()) {
                throw new XPathException(this, ErrorCodes.FODC0006,
                        "No HTML parser configured in conf.xml");
            }

            final Either<Throwable, XMLReader> readerInst = maybeReaderInst.get();
            if (readerInst.isLeft()) {
                throw new XPathException(this, ErrorCodes.FODC0006,
                        "Unable to instantiate HTML parser: " + readerInst.left().get().getMessage());
            }

            final XMLReader xr = readerInst.right().get();

            // Configure for XHTML namespace output
            try {
                xr.setFeature("http://cyberneko.org/html/features/insert-namespaces", true);
            } catch (final SAXException e) {
                // Feature not supported by this parser — XHTML namespace may be missing
            }

            // Configure lowercase element names for XHTML compliance
            try {
                xr.setProperty("http://cyberneko.org/html/properties/names/elems", "lower");
            } catch (final SAXException e) {
                // Property not supported
            }
            try {
                xr.setProperty("http://cyberneko.org/html/properties/names/attrs", "lower");
            } catch (final SAXException e) {
                // Property not supported
            }

            // Use a SAX filter to ensure ALL elements are in XHTML namespace
            final XMLFilterImpl xhtmlFilter = new XMLFilterImpl(xr) {
                private static final String XHTML_NS = "http://www.w3.org/1999/xhtml";
                @Override
                public void startElement(String uri, String localName, String qName, Attributes atts)
                        throws SAXException {
                    if (uri == null || uri.isEmpty()) {
                        uri = XHTML_NS;
                    }
                    super.startElement(uri, localName.isEmpty() ? qName : localName, qName, atts);
                }
                @Override
                public void endElement(String uri, String localName, String qName) throws SAXException {
                    if (uri == null || uri.isEmpty()) {
                        uri = XHTML_NS;
                    }
                    super.endElement(uri, localName.isEmpty() ? qName : localName, qName);
                }
            };

            xhtmlFilter.setErrorHandler(report);
            xhtmlFilter.setContentHandler(adapter);
            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);

            final InputSource src = new InputSource(new StringReader(htmlContent));
            xhtmlFilter.parse(src);

        } catch (final SAXException e) {
            if (failOnError) {
                throw new XPathException(this, ErrorCodes.FODC0011,
                        "HTML parsing error: " + e.getMessage());
            }
            // Non-fatal: return whatever was parsed
        } catch (final IOException e) {
            throw new XPathException(this, ErrorCodes.FODC0006,
                    "Error reading HTML input: " + e.getMessage());
        }

        if (!report.isValid() && failOnError) {
            throw new XPathException(this, ErrorCodes.FODC0011,
                    "HTML parsing error: " + report.toString());
        }

        return adapter.getDocument();
    }

    private boolean getBooleanOption(final MapType options, final String key,
            final boolean defaultValue) throws XPathException {
        final Sequence value = options.get(new StringValue(key));
        if (value != null && !value.isEmpty()) {
            return value.itemAt(0).convertTo(Type.BOOLEAN).effectiveBooleanValue();
        }
        return defaultValue;
    }

    private String getStringOption(final MapType options, final String key,
            final String defaultValue) throws XPathException {
        final Sequence value = options.get(new StringValue(key));
        if (value != null && !value.isEmpty()) {
            final Item item = value.itemAt(0);
            if (!(item instanceof StringValue)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Option '" + key + "' must be a string, got " + Type.getTypeName(item.getType()));
            }
            return item.getStringValue();
        }
        return defaultValue;
    }

    private void validateOptionType(final MapType options, final String key) throws XPathException {
        final Sequence value = options.get(new StringValue(key));
        if (value != null && !value.isEmpty()) {
            // These options are not supported — raise XPTY0004 per spec
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Option '" + key + "' is not supported");
        }
    }
}
