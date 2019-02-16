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
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.exist.xquery.FunctionDSL.*;

public class FunUnparsedText extends BasicFunction {

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
            if (isCalledAs("unparsed-text-lines")) {
                return readLines(args[0].getStringValue(), encoding);
            } else {
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
                return new StringValue(content);
            }
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private String readContent(final String uriParam, final String encoding) throws XPathException {
        try {
            final Source source = getSource(uriParam);
            Charset charset = getCharset(encoding, source);
            final StringWriter output = new StringWriter();
            try (final InputStream is = source.getInputStream()) {
                // InputStream can have value NULL for data retrieved from URL
                IOUtils.copy(is, output, charset);
            }
            return output.toString();
        } catch (final IOException | NullPointerException e) {
            throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
        }
    }

    private Sequence readLines(final String uriParam, final String encoding) throws XPathException {
        try {
            final Sequence result = new ValueSequence();
            final Source source = getSource(uriParam);
            final Charset charset = getCharset(encoding, source);

            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(source.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.add(new StringValue(line));
                }
            }
            return result;
        } catch (IOException e) {
            throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
        }
    }

    private Charset getCharset(final String encoding, final Source source) throws XPathException {
        Charset charset;
        if (encoding == null) {
            try {
                charset = source.getEncoding();
            } catch (IOException e) {
                throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
            }
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
        return charset;
    }

    private Source getSource(final String uriParam) throws XPathException {
        if (!context.getBroker().getCurrentSubject().hasDbaRole()) {
            throw new XPathException(this, ErrorCodes.FOUT1170, "non-dba user not allowed to read from file system");
        }

        try {
            URI uri = new URI(uriParam);
            if (uri.getScheme() == null) {
                uri = new URI(XmldbURI.EMBEDDED_SERVER_URI_PREFIX + uriParam);
            }
            if (uri.getFragment() != null) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "href argument may not contain fragment identifier");
            }

            final Source source = SourceFactory.getSource(context.getBroker(), "", uri.toASCIIString(), false);
            if (source == null) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "Could not find source for: " + uriParam);
            }
            return source;
        } catch (final IOException | PermissionDeniedException | URISyntaxException e) {
            throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
        }
    }
}
