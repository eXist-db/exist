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

import org.apache.commons.io.IOUtils;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.xquery.FunctionDSL.*;

public class FunUnparsedText extends BasicFunction {

    private final static FunctionParameterSequenceType PARAM_HREF = optParam("href", Type.STRING, "the URI to load text from");
    private final static FunctionParameterSequenceType PARAM_ENCODING = optParam("encoding", Type.STRING, "character encoding of the resource");

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
        returns(Type.BOOLEAN),
        arities(
                arity(PARAM_HREF),
                arity(PARAM_HREF, PARAM_ENCODING)
        ));

    public FunUnparsedText(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        @Nullable final String encoding = (args.length == 2 && !args[1].isEmpty()) ? args[1].getStringValue() : null;
        if (args[0].isEmpty()) {
            // Per spec: if $href is empty, unparsed-text-available returns false,
            // unparsed-text and unparsed-text-lines return empty sequence
            if (isCalledAs("unparsed-text-available")) {
                return BooleanValue.FALSE;
            }
            return Sequence.EMPTY_SEQUENCE;
        }
        final String href = args[0].getStringValue();
        if (isCalledAs("unparsed-text-lines")) {
            return readLines(href, encoding);
        } else if (isCalledAs("unparsed-text-available")) {
            return BooleanValue.valueOf(contentAvailable(href, encoding));
        } else {
            return new StringValue(this, stripBOM(readContent(href, encoding)));
        }
    }

    private boolean contentAvailable(final String uri, final String encoding) {
        try {
            final String resolvedUri = toUri(uri).toString();

            if (encoding != null) {
                final Charset charset;
                try {
                    charset = resolveCharset(encoding);
                } catch (final IllegalArgumentException e) {
                    return false;
                }
                try (final Reader dynamicTextResource = context.getDynamicallyAvailableTextResource(resolvedUri, charset)) {
                    if (dynamicTextResource != null) {
                        return true;
                    }
                }
            } else {
                // No encoding — try URI-only lookup
                try (final Reader dynamicTextResource = context.getDynamicallyAvailableTextResourceByUri(resolvedUri)) {
                    if (dynamicTextResource != null) {
                        return true;
                    }
                }
                try (final Reader dynamicTextResource = context.getDynamicallyAvailableTextResource(resolvedUri, UTF_8)) {
                    if (dynamicTextResource != null) {
                        return true;
                    }
                }
            }

            readContent(getSource(uri), encoding);
            return true;
        } catch (final XPathException | IOException e) {
            return false;
        }
    }

    private String readContent(final String uri, final String encoding) throws XPathException {
        final String resolvedUri = toUri(uri).toString();

        if (encoding != null) {
            // Explicit encoding specified — look up with exact charset
            final Charset charset;
            try {
                charset = resolveCharset(encoding);
            } catch (final IllegalArgumentException e) {
                throw new XPathException(this, ErrorCodes.FOUT1190, e.getMessage());
            }

            try (final Reader dynamicTextResource = context.getDynamicallyAvailableTextResource(resolvedUri, charset)) {
                if (dynamicTextResource != null) {
                    return readAll(dynamicTextResource);
                }
            } catch (final IOException e) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "Cannot read text resource");
            }
        } else {
            // No encoding specified — try URI-only lookup (any registered charset)
            try (final Reader dynamicTextResource = context.getDynamicallyAvailableTextResourceByUri(resolvedUri)) {
                if (dynamicTextResource != null) {
                    return readAll(dynamicTextResource);
                }
            } catch (final IOException e) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "Cannot read text resource");
            }

            // Also try with UTF-8 (in case registered with exact UTF-8 key)
            try (final Reader dynamicTextResource = context.getDynamicallyAvailableTextResource(resolvedUri, UTF_8)) {
                if (dynamicTextResource != null) {
                    return readAll(dynamicTextResource);
                }
            } catch (final IOException e) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "Cannot read text resource");
            }
        }

        return readContent(getSource(uri), encoding);
    }

    private String readAll(final Reader reader) throws IOException {
        final StringBuilder builder = new StringBuilder();
        final char[] buf = new char[4096];
        int read = -1;
        while ((read = reader.read(buf)) > 0) {
            builder.append(buf, 0, read);
        }
        return builder.toString();
    }

    /**
     * Validate that a string contains only XML-legal characters.
     * Per XQuery spec, FOUT1190 is raised if the text contains characters
     * that are not permitted in XML.
     */
    private void validateXmlCharacters(final String text) throws XPathException {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            // XML 1.0 legal characters: #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
            // Surrogate pairs (0xD800-0xDFFF) are handled as pairs for supplementary chars
            if (c == 0x9 || c == 0xA || c == 0xD) {
                continue;
            }
            if (c >= 0x20 && c <= 0xD7FF) {
                continue;
            }
            if (c >= 0xE000 && c <= 0xFFFD) {
                continue;
            }
            // Check for valid surrogate pairs (supplementary characters U+10000 to U+10FFFF)
            if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                i++; // skip the low surrogate
                continue;
            }
            throw new XPathException(this, ErrorCodes.FOUT1190,
                    "Text resource contains character not permitted in XML: U+" + String.format("%04X", (int) c));
        }
    }

    private String readContent(final Source source, final String encoding) throws XPathException {
        try {
            final Charset charset = getCharset(encoding, source);
            final StringWriter output = new StringWriter();
            try (final InputStream is = source.getInputStream()) {
                // InputStream can have value NULL for data retrieved from URL
                IOUtils.copy(is, output, charset);
            }
            final String result = output.toString();
            validateXmlCharacters(result);
            return result;
        } catch (final IOException | NullPointerException e) {
            throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
        }
    }

    private Sequence readLines(final String uriParam, final String encoding) throws XPathException {
        final String resolvedUri = toUri(uriParam).toString();

        // Try dynamic text resources first (same as readContent)
        if (encoding != null) {
            final Charset charset;
            try {
                charset = resolveCharset(encoding);
            } catch (final IllegalArgumentException e) {
                throw new XPathException(this, ErrorCodes.FOUT1190, e.getMessage());
            }
            try (final Reader dynamicTextResource = context.getDynamicallyAvailableTextResource(resolvedUri, charset)) {
                if (dynamicTextResource != null) {
                    return readLinesFromReader(new BufferedReader(dynamicTextResource));
                }
            } catch (final IOException | RuntimeException e) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "Cannot read text resource");
            }
        } else {
            try (final Reader dynamicTextResource = context.getDynamicallyAvailableTextResourceByUri(resolvedUri)) {
                if (dynamicTextResource != null) {
                    return readLinesFromReader(new BufferedReader(dynamicTextResource));
                }
            } catch (final IOException | RuntimeException e) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "Cannot read text resource");
            }
            try (final Reader dynamicTextResource = context.getDynamicallyAvailableTextResource(resolvedUri, UTF_8)) {
                if (dynamicTextResource != null) {
                    return readLinesFromReader(new BufferedReader(dynamicTextResource));
                }
            } catch (final IOException | RuntimeException e) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "Cannot read text resource");
            }
        }

        // Fall back to source resolution
        try {
            final Sequence result = new ValueSequence();
            final Source source = getSource(uriParam);
            final Charset sourceCharset = getCharset(encoding, source);

            try (final InputStream inputStream = source.getInputStream()) {
                if (inputStream == null) {
                    throw new XPathException(this, ErrorCodes.FOUT1170, "Unable to retrieve bytestream from " + uriParam);
                }

                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, sourceCharset))) {
                    String line;
                    boolean firstLine = true;
                    while ((line = reader.readLine()) != null) {
                        if (firstLine) {
                            line = stripBOM(line);
                            firstLine = false;
                        }
                        result.add(new StringValue(this, line));
                    }
                }
            }
            return result;
        } catch (final IOException e) {
            throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
        }
    }

    private Sequence readLinesFromReader(final BufferedReader reader) throws XPathException, IOException {
        final Sequence result = new ValueSequence();
        String line;
        boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            if (firstLine) {
                line = stripBOM(line);
                firstLine = false;
            }
            result.add(new StringValue(this, line));
        }
        return result;
    }

    private Charset getCharset(final String encoding, final Source source) throws XPathException {
        Charset charset;
        if (encoding == null) {
            try {
                charset = source.getEncoding();
            } catch (final IOException e) {
                throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
            }
            if (charset == null) {
                charset = UTF_8;
            }
        } else {
            try {
                charset = resolveCharset(encoding);
            } catch (final IllegalArgumentException e) {
                throw new XPathException(this, ErrorCodes.FOUT1190, e.getMessage());
            }
        }
        return charset;
    }

    /**
     * Resolve a charset name, mapping common aliases that Java doesn't recognize.
     */
    /**
     * Strip the Unicode BOM (U+FEFF) from the beginning of a string.
     * Per XQuery spec: "If the text resource has a BOM, the BOM is excluded from the result."
     */
    private static String stripBOM(final String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private static Charset resolveCharset(final String encoding) {
        try {
            return Charset.forName(encoding);
        } catch (final UnsupportedCharsetException e) {
            if ("iso-8859".equalsIgnoreCase(encoding)) {
                return StandardCharsets.ISO_8859_1;
            }
            throw e;
        }
    }

    private Source getSource(final String uriParam) throws XPathException {
        try {
            URI uri = new URI(uriParam);
            if (uri.getFragment() != null) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "href argument may not contain fragment identifier");
            }

            // Resolve relative URIs against file: base URI directory
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

            // Only use direct file: access for URIs resolved from a relative path
            // against a file: base URI. Absolute file: URIs (e.g., file:///etc/passwd)
            // must go through SourceFactory which enforces security checks.
            if (resolvedFromBaseUri && resolvedUri.startsWith("file:")) {
                final String filePath = resolvedUri.replaceFirst("^file:(?://[^/]*)?", "");
                final Path path = Path.of(filePath);
                if (java.nio.file.Files.isReadable(path)) {
                    return new FileSource(path, false);
                }
                throw new XPathException(this, ErrorCodes.FOUT1170, "Could not find source for: " + uriParam);
            }

            final Source source = SourceFactory.getSource(context.getBroker(), "", resolvedUri, false);
            if (source == null) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "Could not find source for: " + uriParam);
            }

            if (source instanceof FileSource && !context.getBroker().getCurrentSubject().hasDbaRole()) {
                throw new PermissionDeniedException("non-dba user not allowed to read from file system");
            }

            return source;
        } catch (final IOException | PermissionDeniedException | URISyntaxException e) {
            throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
        }
    }

    private URI toUri(final String uriStr) throws XPathException {
        try {
            URI uri = new URI(uriStr);
            if (!uri.isAbsolute()) {
                final AnyURIValue baseXdmUri = context.getBaseURI();
                if (baseXdmUri != null && !baseXdmUri.equals(AnyURIValue.EMPTY_URI)) {
                    URI baseUri = baseXdmUri.toURI();
                    if (!baseUri.toString().endsWith("/")) {
                        baseUri = new URI(baseUri.toString() + '/');
                    }
                    uri = baseUri.resolve(uri);
                } else if (!XmldbURI.create(uri).isAbsolute()) {
                    throw new XPathException(this, ErrorCodes.FOUT1170, "$uri is a relative URI but there is no base-URI set");
                }
            }
            return uri;
        } catch (final URISyntaxException e) {
            throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
        }
    }
}
