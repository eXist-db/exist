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

import nu.validator.htmlparser.common.XmlViolationPolicy;
import nu.validator.htmlparser.sax.HtmlParser;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;

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
        final SAXAdapter adapter = new SAXAdapter(this, context);

        try {
            // Use Validator.nu HTML5 parser — SAX-based, same pipeline as NekoHTML
            // but follows the WHATWG HTML5 parsing algorithm. Outputs XHTML namespace
            // by default, handles <template>, <svg>, <math> foreign content.
            final HtmlParser reader = new HtmlParser(XmlViolationPolicy.ALTER_INFOSET);

            reader.setContentHandler(adapter);
            reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);

            final InputSource src = new InputSource(new StringReader(htmlContent));
            reader.parse(src);

        } catch (final SAXException e) {
            if (failOnError) {
                throw new XPathException(this, ErrorCodes.FODC0011,
                        "HTML parsing error: " + e.getMessage());
            }
        } catch (final IOException e) {
            throw new XPathException(this, ErrorCodes.FODC0006,
                    "Error reading HTML input: " + e.getMessage());
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
