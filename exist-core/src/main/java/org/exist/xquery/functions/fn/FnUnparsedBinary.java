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
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * fn:unparsed-binary($uri as xs:string?) as xs:base64Binary?
 * Loads binary content from a URI and returns it as xs:base64Binary.
 */
public class FnUnparsedBinary extends BasicFunction {

    public static final FunctionSignature FN_UNPARSED_BINARY = new FunctionSignature(
            new QName("unparsed-binary", Function.BUILTIN_FUNCTION_NS),
            "Loads binary content from a URI and returns it as xs:base64Binary.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("uri", Type.STRING,
                            Cardinality.ZERO_OR_ONE, "The URI of the binary resource")
            },
            new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE,
                    "The binary content"));

    public FnUnparsedBinary(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String uriParam = args[0].getStringValue();

        try {
            URI uri = new URI(uriParam);

            // Resolve relative URIs against file: base URI
            boolean resolvedFromBaseUri = false;
            if (!uri.isAbsolute()) {
                final AnyURIValue baseXdmUri = context.getBaseURI();
                if (baseXdmUri != null && !baseXdmUri.equals(AnyURIValue.EMPTY_URI)) {
                    String baseStr = baseXdmUri.toURI().toString();
                    if (baseStr.startsWith("file:")) {
                        final int lastSlash = baseStr.lastIndexOf('/');
                        if (lastSlash >= 0) {
                            baseStr = baseStr.substring(0, lastSlash + 1);
                        }
                        uri = new URI(baseStr).resolve(uri);
                        resolvedFromBaseUri = true;
                    }
                }
            }

            final String resolvedUri = uri.toASCIIString();

            // Handle file: URIs directly (only for resolved relative paths)
            if (resolvedFromBaseUri && resolvedUri.startsWith("file:")) {
                final String filePath = resolvedUri.replaceFirst("^file:(?://[^/]*)?", "");
                final java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                if (java.nio.file.Files.isReadable(path)) {
                    try (final InputStream is = java.nio.file.Files.newInputStream(path)) {
                        return BinaryValueFromInputStream.getInstance(context,
                                new Base64BinaryValueType(), is, this);
                    }
                }
                throw new XPathException(this, ErrorCodes.FOUT1170,
                        "Could not find binary resource: " + uriParam);
            }

            // Use SourceFactory for other URIs
            final Source source = SourceFactory.getSource(context.getBroker(), "", resolvedUri, false);
            if (source == null) {
                throw new XPathException(this, ErrorCodes.FOUT1170,
                        "Could not find binary resource: " + uriParam);
            }
            try (final InputStream is = source.getInputStream()) {
                return BinaryValueFromInputStream.getInstance(context,
                        new Base64BinaryValueType(), is, this);
            }
        } catch (final IOException | URISyntaxException | org.exist.security.PermissionDeniedException e) {
            throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
        }
    }
}
