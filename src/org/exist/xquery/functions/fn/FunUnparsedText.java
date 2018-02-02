/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.functions.fn;

import org.apache.commons.io.IOUtils;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.util.PatternFactory;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.exist.xquery.FunctionDSL.*;

public class FunUnparsedText extends BasicFunction {

    private final static String LINE_REGEX = "\\r\\n|\\r|\\n";

    private final static FunctionParameterSequenceType PARAM_HREF = optParam("href", Type.STRING, "the URI to load text from");
    private final static FunctionParameterSequenceType PARAM_ENCODING = param("encoding", Type.STRING, "character encoding of the resource");

    static final FunctionSignature [] FS_UNPARSED_TEXT = functionSignatures(
        new QName("unparsed-text", Function.BUILTIN_FUNCTION_NS),
        "reads an external resource (for example, a file) and returns a string representation of the resource",
        returnsOpt(Type.STRING),
        arities(
                arity(PARAM_HREF),
                arity(PARAM_HREF, PARAM_ENCODING)
        ));

    static final FunctionSignature[] FS_UNPARSED_TEXT_LINES = functionSignatures(
        new QName("unparsed-text-lines", Function.BUILTIN_FUNCTION_NS),
        "reads an external resource (for example, a file) and returns its contents as a sequence of strings, one for each line of text in the string representation of the resource",
        returnsOptMany(Type.STRING),
        arities(
                arity(PARAM_HREF),
                arity(PARAM_HREF, PARAM_ENCODING)
        ));

    static final FunctionSignature [] FS_UNPARSED_TEXT_AVAILABLE = functionSignatures(
        new QName("unparsed-text-available", Function.BUILTIN_FUNCTION_NS),
        "determines whether a call on the fn:unparsed-text function with identical arguments would return a string",
        returnsOpt(Type.STRING),
        arities(
                arity(PARAM_HREF),
                arity(PARAM_HREF, PARAM_ENCODING)
        ));

    public FunUnparsedText(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final String encoding = args.length == 2 ? args[1].getStringValue() : null;
        if (!args[0].isEmpty()) {
            final String content;
            try {
                content = readContent(args[0].getStringValue(), encoding);
            } catch (XPathException e) {
                if (isCalledAs("unparsed-text-available")) {
                    return BooleanValue.FALSE;
                }
                throw e;
            }
            if (isCalledAs("unparsed-text-available")) {
                return BooleanValue.TRUE;
            }
            if (isCalledAs("unparsed-text-lines")) {
                final Pattern pattern = PatternFactory.getInstance().getPattern(LINE_REGEX);
                final String[] lines = pattern.split(content);
                int limit = lines.length;
                if (lines[limit - 1].length() == 0) {
                    --limit;
                }
                final Sequence result = new ValueSequence();
                for (int i = 0; i < limit; i++) {
                    result.add(new StringValue(lines[i]));
                }
                return result;
            } else {
                return new StringValue(content);
            }
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private String readContent(String uriParam, String encoding) throws XPathException {
        try {
            URI uri = new URI(uriParam);
            if (uri.getScheme() == null) {
                uri = new URI(XmldbURI.EMBEDDED_SERVER_URI_PREFIX + uriParam);
            }
            if (uri.getFragment() != null) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "href argument may not contain fragment identifier");
            }
            final StringWriter output = new StringWriter();
            final Source source = SourceFactory.getSource(context.getBroker(), null, uri.toASCIIString(), false);
            Charset charset;
            if (encoding == null) {
                charset = source.getEncoding();
                if (charset == null) {
                    charset = StandardCharsets.UTF_8;
                }
            } else {
                try {
                    charset = Charset.forName(encoding);
                } catch (IllegalArgumentException e) {
                    throw new XPathException(this, ErrorCodes.FOUT1190, e.getMessage());
                }
            }
            try (final InputStream is = source.getInputStream()) {
                IOUtils.copy(is, output, charset);
            }
            return output.toString();
        } catch (IOException | PermissionDeniedException | URISyntaxException e) {
            throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
        }
    }
}
