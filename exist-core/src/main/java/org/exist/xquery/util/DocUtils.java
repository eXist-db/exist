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
package org.exist.xquery.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.nio.file.Files.isReadable;
import static java.nio.file.Files.newInputStream;

import org.exist.Namespaces;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.XMLReaderPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.source.URLSource;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import javax.annotation.Nullable;

import static com.evolvedbinary.j8fu.Try.Try;

/**
 * Utilities for XPath doc related functions
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */
//TODO : many more improvements to handle efficiently any URI
public class DocUtils {

    private static final Pattern PTN_PROTOCOL_PREFIX = Pattern.compile("^[a-z]+:.*");

    public static Sequence getDocument(final XQueryContext context, final String path) throws XPathException, PermissionDeniedException {
        return getDocument(context, path, null);
    }

    public static Sequence getDocument(final XQueryContext context, final String path, final Expression expression) throws XPathException, PermissionDeniedException {
        return getDocumentByPath(context, path, expression);
    }

    public static boolean isDocumentAvailable(final XQueryContext context, final String path) throws XPathException {
        return isDocumentAvailable(context, path, null);
    }

    //TODO(AR) could make this much more efficient... it doesn't need to actually retrieve the document!
    public static boolean isDocumentAvailable(final XQueryContext context, final String path, final Expression expression) throws XPathException {
        try {
            final Sequence seq = getDocumentByPath(context, path, expression);
            return (seq != null && seq.effectiveBooleanValue());
        } catch (final PermissionDeniedException e) {
            return false;
        }

    }

    private static Sequence getDocumentByPath(final XQueryContext context, final String path, final Expression expression) throws XPathException, PermissionDeniedException {
        Sequence doc = getFromDynamicallyAvailableDocuments(context, path, expression);
        if (doc == null) {
            if (PTN_PROTOCOL_PREFIX.matcher(path).matches() && !path.startsWith("xmldb:")) {
                /* URL — use SourceFactory (has security checks) */
                doc = getDocumentByPathFromURL(context, path, expression, false);
            } else if (!PTN_PROTOCOL_PREFIX.matcher(path).matches()) {
                // Relative URI: resolve against static base URI per XQuery spec §2.1.2
                final String resolved = resolveAgainstBaseUri(context, path);
                if (resolved != null && resolved.startsWith("file:")) {
                    doc = getDocumentByPathFromURL(context, resolved, expression, true);
                } else {
                    /* Database documents */
                    doc = getDocumentByPathFromDB(context, path, expression);
                }
            } else {
                /* Database documents (xmldb: prefix) */
                doc = getDocumentByPathFromDB(context, path, expression);
            }
        }

        return doc;
    }

    /**
     * Resolve a relative URI against the static base URI.
     *
     * @return the resolved URI string, or null if resolution is not possible
     */
    public static @Nullable String resolveAgainstBaseUri(final XQueryContext context, final String relativePath) {
        try {
            final AnyURIValue baseXdmUri = context.getBaseURI();
            if (baseXdmUri != null && !baseXdmUri.equals(AnyURIValue.EMPTY_URI)) {
                String baseStr = baseXdmUri.toURI().toString();
                // Strip filename to get directory URI
                final int lastSlash = baseStr.lastIndexOf('/');
                if (lastSlash >= 0) {
                    baseStr = baseStr.substring(0, lastSlash + 1);
                }
                return new URI(baseStr).resolve(relativePath).toString();
            }
        } catch (final URISyntaxException | IllegalArgumentException | XPathException e) {
            // IllegalArgumentException: URI.create(relativePath) (called by URI.resolve) rejects a db
            // resource name containing a raw space or similar -- a valid name under the resource-naming
            // contract (eXist-db/exist#6463, decision 3). Fall through; the caller passes the original
            // path (not this resolved form) to the DB branch, which normalizes and resolves it.
        }
        return null;
    }

    /**
     * Whether {@code path} addresses a database resource (an absolute {@code /db} path or an
     * {@code xmldb:} URI). Used to scope the resource-naming contract's read-side leniency
     * (eXist-db/exist#6463, decision 3): a db resource name may contain a character that is not valid
     * in a {@link URI} but is a valid name, so a parse failure on such a path should defer to the DB
     * normalization rather than raise {@code FODC0005}; any other malformed URI is a genuine error.
     *
     * @param path the path argument to {@code fn:doc} / {@code fn:doc-available}
     * @return true if the path targets the database
     */
    public static boolean isDbPath(final String path) {
        return path.startsWith(XmldbURI.ROOT_COLLECTION) || path.startsWith(XmldbURI.XMLDB_URI_PREFIX);
    }

    private static @Nullable Sequence getFromDynamicallyAvailableDocuments(final XQueryContext context, final String path, @Nullable final Expression expression) throws XPathException {
        try {
            URI uri = new URI(path);
            if (!uri.isAbsolute()) {
                final AnyURIValue baseXdmUri = context.getBaseURI();
                if (baseXdmUri != null && !baseXdmUri.equals(AnyURIValue.EMPTY_URI)) {
                    URI baseUri = baseXdmUri.toURI();
                    if (!baseUri.toString().endsWith("/")) {
                        baseUri = new URI(baseUri.toString() + '/');
                    }
                    uri = baseUri.resolve(uri);
                }
            }
            return context.getDynamicallyAvailableDocument(uri.toString());
        } catch (final URISyntaxException e) {
            // A bare db-path may contain a character (e.g. a raw space) that is a valid resource name
            // under the resource-naming contract (eXist-db/exist#6463, decision 3) but not a valid
            // java.net.URI. Such a path cannot be a key in the dynamically-available-documents map
            // (which is keyed by valid URIs), so skip that lookup and let the DB branch normalize
            // (escape=true) and resolve it. Any other malformed URI keeps the spec-mandated FODC0005.
            if (isDbPath(path)) {
                return null;
            }
            throw new XPathException(expression, ErrorCodes.FODC0005, e);
        }
    }

    private static Sequence getDocumentByPathFromURL(final XQueryContext context, final String path, final Expression expression, final boolean resolvedFromBaseUri) throws XPathException, PermissionDeniedException {
        try {
            // Only use direct file: access for URIs resolved from a relative path
            // against a file: base URI. Absolute file: URIs go through SourceFactory
            // which enforces security checks (e.g., blocking file:///etc/passwd).
            if (resolvedFromBaseUri && path.startsWith("file:")) {
                final String filePath = path.replaceFirst("^file:(?://[^/]*)?", "");
                final Path nioPath = Path.of(filePath);
                if (isReadable(nioPath)) {
                    try (final InputStream fis = newInputStream(nioPath)) {
                        final org.exist.dom.memtree.DocumentImpl memtreeDoc = parse(
                                context.getBroker().getBrokerPool(), context, fis, expression);
                        memtreeDoc.setDocumentURI(path);
                        return memtreeDoc;
                    }
                }
                return Sequence.EMPTY_SEQUENCE;
            }

            final Source source = SourceFactory.getSource(context.getBroker(), "", path, false);
            if (source == null) {
                return Sequence.EMPTY_SEQUENCE;
            }

            try (final InputStream is = source.getInputStream()) {
                if (source instanceof URLSource urlSource) {
                    final int responseCode = urlSource.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        // Special case: '404'
                        return Sequence.EMPTY_SEQUENCE;
                    } else if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw new PermissionDeniedException("Server returned code " + responseCode);
                    }
                }

                final org.exist.dom.memtree.DocumentImpl memtreeDoc = parse(
                        context.getBroker().getBrokerPool(),
                        context,
                        is,
                        expression);
                memtreeDoc.setDocumentURI(path);
                return memtreeDoc;
            }
        } catch (final ConnectException e) {
            // prevent long stack traces
            throw new XPathException(expression, e.getMessage() + " (" + path + ")");
        } catch (final MalformedURLException e) {
            throw new XPathException(expression, e.getMessage(), e);
        } catch (final IOException e) {
            throw new XPathException(expression, "An error occurred while parsing " + path + ": " + e.getMessage(), e);
        }
    }

    private static Sequence getDocumentByPathFromDB(final XQueryContext context, final String path, final Expression expression) throws XPathException, PermissionDeniedException {
        // check if the loaded documents should remain locked
        final LockMode lockType = context.lockDocumentsOnLoad() ? LockMode.WRITE_LOCK : LockMode.READ_LOCK;
        try {
            final XmldbURI baseURI = context.getBaseURI().toXmldbURI();
            final XmldbURI pathUri;
            // Resource-naming contract (eXist-db/exist#6463, decisions 1 + 2 + 5): canonicalize the
            // db-path to the key xmldb:store wrote by treating it as decoded UTF-8 (decision 1) and
            // re-encoding it leniently. decode-then-encode maps BOTH a decoded display name and an
            // already-encoded path to the same stored key, so doc("/db/x/café.xml") and
            // doc("/db/x/caf%C3%A9.xml") both resolve cafe.xml; and a literal '%' name resolves by its
            // decoded form -- doc("/db/x/50%.xml") -> 50%25.xml (decision 2, read side). decodeForURI is
            // the exact inverse of encodeForURILenient and never throws/truncates, so this is idempotent
            // on the canonical stored form (the case a pre-encoding caller such as xmldb:encode-uri hits).
            final String canonicalPath = URIUtils.encodePathForURILenient(URIUtils.decodePathForURI(path));
            if (baseURI != null && !(baseURI.equals("") || baseURI.equals("/db"))) {
                // relative collection Path: add the current base URI
                pathUri = baseURI.resolveCollectionPath(XmldbURI.xmldbUriFor(canonicalPath, false));
            } else {
                pathUri = XmldbURI.xmldbUriFor(canonicalPath, false);
            }

            // relative collection Path: add the current module call URI if applicable
            final XmldbURI resourceUri = Optional.ofNullable(context.getModuleLoadPath())
                    .filter(moduleLoadPath -> !moduleLoadPath.isEmpty())
                    .flatMap(moduleLoadPath -> Try(() -> XmldbURI.xmldbUriFor(moduleLoadPath)).toOption())
                    .map(moduleLoadPath -> moduleLoadPath.resolveCollectionPath(pathUri))
                    .orElse(pathUri);

            // try to open the document and acquire a lock
            try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(resourceUri, lockType)){
                if (lockedDoc == null) {
                    return Sequence.EMPTY_SEQUENCE;
                } else {
                    final DocumentImpl doc = lockedDoc.getDocument();
                    if (!doc.getPermissions().validate(context.getSubject(), Permission.READ)) {
                        throw new PermissionDeniedException("Insufficient privileges to read resource " + path);
                    }

                    if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                        throw new XPathException(expression, "Document " + path + " is a binary resource, not an XML document. Please consider using the function util:binary-doc() to retrieve a reference to it.");
                    }

                    return new NodeProxy(expression, doc);
                }
            }
        } catch (final URISyntaxException e) {
            throw new XPathException(expression, e);
        }
    }

    /**
     * Utility function to parse an input stream into an in-memory DOM document.
     *
     * @param context The XQuery context
     * @param is      The input stream to parse from
     * @return document The document that was parsed
     * @throws XPathException in case of dynamic error
     */
    public static org.exist.dom.memtree.DocumentImpl parse(final XQueryContext context, final InputStream is) throws XPathException {
        return parse(context, is, null);
    }

    /**
     * Utility function to parse an input stream into an in-memory DOM document.
     *
     * @param context    The XQuery context
     * @param is         The input stream to parse from
     * @param expression The expression of the input stream
     * @return document  The document that was parsed
     * @throws XPathException in case of dynamic error
     */
    public static org.exist.dom.memtree.DocumentImpl parse(final XQueryContext context, final InputStream is, final Expression expression) throws XPathException {
        return parse(context.getBroker().getBrokerPool(), context, is, expression);
    }

    /**
     * Utility function to parse an input stream into an in-memory DOM document.
     *
     * @param pool    The broker pool
     * @param context The XQuery context
     * @param is      The input stream to parse from
     * @return document The document that was parsed
     * @throws XPathException in case of dynamic error
     */
    public static org.exist.dom.memtree.DocumentImpl parse(final BrokerPool pool, final XQueryContext context,
            final InputStream is) throws XPathException {
        return parse(pool, context, is, null);
    }

    /**
     * Utility function to parse an input stream into an in-memory DOM document.
     *
     * @param pool       The broker pool
     * @param context    The XQuery context
     * @param is         The input stream to parse from
     * @param expression The expression of the input stream
     * @return document The document that was parsed
     * @throws XPathException in case of dynamic error
     */
    public static org.exist.dom.memtree.DocumentImpl parse(final BrokerPool pool, final XQueryContext context,
            final InputStream is, final Expression expression) throws XPathException {
        // we use eXist's in-memory DOM implementation
        final XMLReaderPool parserPool = pool.getParserPool();
        XMLReader reader = null;
        try {
            reader = pool.getParserPool().borrowXMLReader();
            final InputSource src = new InputSource(is);
            final SAXAdapter adapter = new SAXAdapter(expression, context);
            reader.setContentHandler(adapter);
            try {
                reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
                reader.parse(src);
            } catch (final SAXNotRecognizedException | SAXNotSupportedException e) {
                throw new XPathException(expression, "Error creating XML parser: " + e.getMessage(), e);
            } catch (final IOException | SAXException e) {
                throw new XPathException(expression, "Error while parsing XML: " + e.getMessage(), e);
            }

            return adapter.getDocument();

        } finally {
            if (reader != null) {
                parserPool.returnXMLReader(reader);
            }
        }
    }
}
