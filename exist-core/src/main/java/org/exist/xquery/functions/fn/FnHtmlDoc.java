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

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * fn:html-doc($uri) — Like fn:doc but for HTML.
 * Loads HTML from a URI, parses it through fn:parse-html, returns XHTML document.
 */
public class FnHtmlDoc extends BasicFunction {

    public static final FunctionSignature FN_HTML_DOC = new FunctionSignature(
            new QName("html-doc", Function.BUILTIN_FUNCTION_NS),
            "Loads an HTML resource from a URI and returns the parsed XHTML document.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("uri", Type.STRING,
                            Cardinality.ZERO_OR_ONE, "The URI of the HTML resource")
            },
            new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ZERO_OR_ONE,
                    "The parsed XHTML document"));

    public FnHtmlDoc(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String uri = args[0].getStringValue();

        // Load text content using unparsed-text logic
        final FunUnparsedText unparsedText = new FunUnparsedText(context,
                FunUnparsedText.FS_UNPARSED_TEXT[0]);
        final Sequence textResult = unparsedText.eval(
                new Sequence[]{new StringValue(this, uri)}, contextSequence);

        if (textResult.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // Parse through fn:parse-html
        final FnParseHtml parseHtml = new FnParseHtml(context,
                FnParseHtml.FN_PARSE_HTML[0]);
        return parseHtml.eval(new Sequence[]{textResult}, contextSequence);
    }
}
