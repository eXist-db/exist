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

import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.functions.xmldb.XMLDBModule;
import org.exist.xquery.value.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import static org.exist.xquery.FunctionDSL.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ExtCollection extends BasicFunction {

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
                            optParam("collection-uri", Type.STRING,"The Collection URI")
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
        if (args.length == 0 || args[0].isEmpty()) {
            collectionUri = null;
        } else {
            collectionUri = asUri(args[0].itemAt(0).getStringValue());
        }

        return getCollectionItems(new URI[] { collectionUri });
    }

    protected Sequence getCollectionItems(final URI[] collectionUris) throws XPathException {
        if (collectionUris == null) {
            // no collection-uri(s)
            return getDefaultCollectionItems();
        }

        final Sequence result = new ValueSequence();
        for (final URI collectionUri : collectionUris) {
            getCollectionItems(collectionUri, result);
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

    private void getCollectionItems(final URI collectionUri, final Sequence items) throws XPathException {
        final Sequence dynamicCollection = context.getDynamicallyAvailableCollection(collectionUri.toString());
        if (dynamicCollection != null) {
            items.addAll(dynamicCollection);

        } else if (context.inProtectedMode()) {
            items.addAll(resolveCollection(collectionUri));
        } else {
            // fn:collection() is stable within a query, so a collection resolved earlier in the query is reused, as
            // long as nothing has been written to the database since
            final String key = (includeSubCollections ? "descendants " : "children ")
                    + context.getBroker().getCurrentSubject().getId() + " " + collectionUri;
            final Sequence resolved = context.getResolvedCollection(key);
            if (resolved != null) {
                items.addAll(resolved);
            } else {
                final long writeLockGeneration = context.getBroker().getBrokerPool().getLockManager().getWriteLockGeneration();
                final Sequence collectionItems = resolveCollection(collectionUri);
                context.setResolvedCollection(key, writeLockGeneration, collectionItems);
                items.addAll(collectionItems);
            }
        }
    }

    private Sequence resolveCollection(final URI collectionUri) throws XPathException {
        final Sequence items = new ValueSequence();
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
        return items;
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

    /**
     * Adds a document node for each XML document in the set.
     *
     * The documents' permissions were checked as the set was built. No lock is taken here: creating a
     * {@link NodeProxy} doesn't read the document, and a lock released straight afterwards wouldn't
     * protect the node's later use either.
     */
    private void addAll(final DocumentSet docs, final Sequence items) throws XPathException {
        for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl doc = i.next();

            // filter out binary documents, fn:collection should only return XML documents
            if (doc.getResourceType() == DocumentImpl.XML_FILE) {
                items.add(new NodeProxy(this, doc));
            }
        }
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        //Nothing more to do (previously was cache management)
    }
}
