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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.functions.xmldb.XMLDBModule;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.exist.xquery.util.DocUtils;

import static org.exist.xquery.FunctionDSL.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ExtCollection extends BasicFunction {

    private static final Logger LOG = LogManager.getLogger(ExtCollection.class);

    private static final String FS_COLLECTION_NAME = "collection";
    static final FunctionSignature[] FS_COLLECTION = functionSignatures(
            new QName(FS_COLLECTION_NAME, Function.BUILTIN_FUNCTION_NS),
            "Returns the documents contained in the Collection specified in the input sequence. "
                    + XMLDBModule.COLLECTION_URI + " Documents contained in sub-collections are also included. "
                    + "If no value is supplied, the statically know documents are used; for the REST Server this could be the collection in the URI path.",
            returnsOptMany(Type.ITEM, "The items indicated by the Collection URI"),
            arities(
                    arity(),
                    arity(
                            optParam("source", Type.STRING,"The Collection URI")
                    )
            )
    );

    private final boolean includeSubCollections;

    public ExtCollection(final XQueryContext context, final FunctionSignature signature) {
        this(context, signature, true);
    }

    public ExtCollection(final XQueryContext context, final FunctionSignature signature, final boolean inclusive) {
        super(context, signature);
        includeSubCollections = inclusive;
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final URI collectionUri;
        final String rawQueryString;
        if (args.length == 0 || args[0].isEmpty()) {
            collectionUri = null;
            rawQueryString = null;
        } else {
            // Split off any Saxon-style query string before parsing the URI.
            // The query may contain regex characters (^, [, ], +, etc.) that
            // Java's URI class rejects, so we strip and pass it separately.
            final String input = args[0].itemAt(0).getStringValue();
            final int q = input.indexOf('?');
            if (q >= 0) {
                collectionUri = asUri(input.substring(0, q));
                rawQueryString = input.substring(q + 1);
            } else {
                collectionUri = asUri(input);
                rawQueryString = null;
            }
        }

        return getCollectionItems(collectionUri, rawQueryString);
    }

    private Sequence getCollectionItems(final URI collectionUri, final String rawQueryString) throws XPathException {
        if (collectionUri == null) {
            return getDefaultCollectionItems();
        }
        final Sequence result = new ValueSequence();
        getCollectionItems(collectionUri, rawQueryString, result);
        return result;
    }

    protected Sequence getCollectionItems(final URI[] collectionUris) throws XPathException {
        if (collectionUris == null) {
            // no collection-uri(s)
            return getDefaultCollectionItems();
        }

        final Sequence result = new ValueSequence();
        for (final URI collectionUri : collectionUris) {
            // No raw query string from this code path; subclasses (e.g. FunXCollection)
            // do not split off the query string before calling
            getCollectionItems(collectionUri, null, result);
        }
        return result;
    }

    private Sequence getDefaultCollectionItems() throws XPathException {
        final Sequence docs = new ValueSequence();
        addAll(context.getStaticallyKnownDocuments(), docs);
        final Sequence dynamicCollection = context.getDynamicallyAvailableCollection("");
        if (dynamicCollection != null) {
            final Sequence result = new ValueSequence();
            result.addAll(docs);
            result.addAll(dynamicCollection);
            return result;

        } else {
            return docs;
        }
    }

    private void getCollectionItems(final URI collectionUri, final String rawQueryString, final Sequence items) throws XPathException {
        final Sequence dynamicCollection = context.getDynamicallyAvailableCollection(collectionUri.toString());
        if (dynamicCollection != null) {
            items.addAll(dynamicCollection);

        } else if ("file".equals(collectionUri.getScheme())) {
            // file: URI — scan directory for XML files
            getFileCollectionItems(collectionUri, rawQueryString, items);

        } else {
            final MutableDocumentSet ndocs = new DefaultDocumentSet();
            final XmldbURI uri = XmldbURI.create(collectionUri);
            try (final Collection coll = context.getBroker().openCollection(uri, Lock.LockMode.READ_LOCK)) {
                if (coll == null) {
                    if (context.isRaiseErrorOnFailedRetrieval()) {
                        throw new XPathException(this, ErrorCodes.FODC0002, "Can not access collection '" + uri + "'");
                    }
                } else {
                    if (context.inProtectedMode()) {
                        context.getProtectedDocs().getDocsByCollection(coll, ndocs);
                    } else {
                        coll.allDocs(context.getBroker(), ndocs,
                                includeSubCollections, context.getProtectedDocs());
                    }
                }
            } catch (final PermissionDeniedException e) {
                throw new XPathException(this, ErrorCodes.FODC0002, "Can not access collection '" + e.getMessage() + "'", new StringValue(collectionUri.toString()), e);
            } catch (final LockException e) {
                throw new XPathException(this, ErrorCodes.FODC0002, e.getMessage(), new StringValue(collectionUri.toString()), e);
            }

            // add the docs to the items
            addAll(ndocs, items);
        }
    }

    /**
     * Scan a file: URI directory for documents and parse XML files into in-memory documents.
     * <p>
     * Supports Saxon-style query string parameters (aligned with fn:uri-collection):
     * </p>
     * <ul>
     *   <li>{@code select=*.xml} — glob pattern for filename matching (default: {@code *.xml})</li>
     *   <li>{@code match=regex} — additional regex filter on filenames</li>
     *   <li>{@code content-type=...} — MIME filter; for file: URIs:
     *     {@code application/vnd.existdb.document+xml} or {@code application/vnd.existdb.document}
     *     selects XML files (the only kind fn:collection returns)</li>
     *   <li>{@code stable=yes|no} — when {@code yes} (default), files are returned in alphabetical order</li>
     * </ul>
     * <p>
     * Only DBA users can access the file system directly.
     * </p>
     */
    private void getFileCollectionItems(final URI collectionUri, final String rawQueryString, final Sequence items) throws XPathException {
        // Security: only DBA users can access file: URIs
        if (!context.getBroker().getCurrentSubject().hasDbaRole()) {
            throw new XPathException(this, ErrorCodes.FODC0002,
                    "Permission denied: only DBA users can access file: URIs in fn:collection()");
        }

        // Parse Saxon-style query parameters from the raw query string.
        // We use rawQueryString (passed separately from the URI) because the query
        // may contain regex characters like ^, [, ], +, $ that Java's URI class rejects.
        final CollectionQueryParameters params = CollectionQueryParameters.parse(
                rawQueryString != null ? "?" + rawQueryString : null,
                CollectionQueryParameters.FILE_COLLECTION_KEYS,
                this);

        // fn:collection() returns documents (XML), so a content-type that excludes XML
        // would yield an empty result. Detect that early.
        if (params.hasContentType() && !params.includesXmlDocuments()) {
            return;
        }

        // Default glob pattern is *.xml (XML files only). User-supplied select overrides.
        final String globPattern = params.getSelect() != null ? params.getSelect() : "*.xml";

        // Compile match regex if present
        final Pattern matchPattern = (params.getMatch() != null && !params.getMatch().isEmpty())
                ? Pattern.compile(params.getMatch())
                : null;

        final Path dir = Paths.get(collectionUri.getPath());
        if (!Files.isDirectory(dir)) {
            throw new XPathException(this, ErrorCodes.FODC0002,
                    "Directory does not exist: " + dir);
        }

        // Collect candidate files matching all filters
        final List<Path> candidates = new ArrayList<>();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, globPattern)) {
            for (final Path file : stream) {
                if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
                    continue;
                }
                if (matchPattern != null && !matchPattern.matcher(file.getFileName().toString()).find()) {
                    continue;
                }
                candidates.add(file);
            }
        } catch (final IOException e) {
            throw new XPathException(this, ErrorCodes.FODC0002,
                    "Error reading directory: " + e.getMessage());
        }

        // Apply stable ordering (alphabetical by filename) when stable=yes (the default)
        if (params.isStable()) {
            candidates.sort(Comparator.comparing(p -> p.getFileName().toString()));
        }

        // Parse each candidate as XML and add to items; skip non-parseable files
        for (final Path file : candidates) {
            try (final InputStream is = Files.newInputStream(file)) {
                final org.exist.dom.memtree.DocumentImpl doc =
                        DocUtils.parse(context, is, this);
                doc.setDocumentURI(file.toUri().toString());
                items.add(doc);
            } catch (final XPathException | IOException e) {
                // Skip non-parseable files (they may not be well-formed XML)
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Skipping non-parseable file in collection: {}", file, e);
                }
            }
        }
    }

    protected URI asUri(final String path) throws XPathException {
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
                } else if (!XmldbURI.create(uri).isAbsolute()) {
                    throw new XPathException(this, ErrorCodes.FODC0003, "$uri is a relative URI but there is no base-URI set");
                }
            }
            return uri;
        } catch (final URISyntaxException e) {
            throw new XPathException(this, ErrorCodes.FODC0004, e);
        }
    }

    private void addAll(final DocumentSet docs, final Sequence items) throws XPathException {
        final LockManager lockManager = context.getBroker().getBrokerPool().getLockManager();
        for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl doc = i.next();

            // filter out binary documents, fn:collection should only return XML documents
            if (doc.getResourceType() == DocumentImpl.XML_FILE) {

                ManagedDocumentLock dlock = null;
                try {
                    if (!context.inProtectedMode()) {
                        dlock = lockManager.acquireDocumentReadLock(doc.getURI());
                    }
                    items.add(new NodeProxy(this, doc));
                } catch (final LockException e) {
                    throw new XPathException(this, ErrorCodes.FODC0002, e);
                } finally {
                    if (dlock != null) {
                        dlock.close();
                    }
                }
            }
        }
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        //Nothing more to do (previously was cache management)
    }
}
