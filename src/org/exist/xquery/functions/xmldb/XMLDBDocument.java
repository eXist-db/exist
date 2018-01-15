/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.collections.ManagedLocks;
import org.exist.dom.persistent.*;
import org.exist.dom.QName;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.UpdateListener;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Iterator;

/**
 * Implements eXist's xmldb:document() function.
 *
 * @author wolf
 * @author aretter
 */
public class XMLDBDocument extends BasicFunction {
    private static final Logger logger = LogManager.getLogger(XMLDBDocument.class);

    public static final FunctionSignature signature =
            new FunctionSignature(
                    new QName("document", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
                    "Returns the documents indicated by $document-uris in the input sequence. " +
                            XMLDBModule.COLLECTION_URI +
                            "If the input sequence is empty, " +
                            "the function will load all documents in the database (WARNING this is a very expensive operation!).",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("document-uris", Type.STRING, Cardinality.ONE_OR_MORE, "The document URIs")
                    },
                    new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the documents"),
                    true, "See the standard fn:doc() function");

    private UpdateListener listener = null;

    public XMLDBDocument(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public int getDependencies() {
        return Dependency.CONTEXT_SET;
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final MutableDocumentSet mdocs;
        if (args.length == 0) {
            mdocs = allDocs();
        } else {
            mdocs = docs(args);
        }

        final boolean lockOnLoad = context.lockDocumentsOnLoad();
        ManagedLocks<ManagedDocumentLock> docLocks = null;
        try {

            // wait for pending updates
            docLocks = mdocs.lock(context.getBroker(), lockOnLoad);

            final Sequence results = new ExtArrayNodeSet(mdocs.getDocumentCount(), 1);
            for (final Iterator<DocumentImpl> i = mdocs.getDocumentIterator(); i.hasNext(); ) {
                final DocumentImpl doc = i.next();
                results.add(new NodeProxy(doc)); //, -1, Node.DOCUMENT_NODE));
                if (lockOnLoad) {
                    context.addLockedDocument(doc);
                }
            }
            return results;
        } catch (final LockException e) {
            logger.error("Could not acquire lock on document set", e);
            throw new XPathException(this, "Could not acquire lock on document set.");
        } finally {
            if (!lockOnLoad) {
                // release all locks
                if (docLocks != null) {
                    docLocks.close();
                }
            }
        }
    }

    private MutableDocumentSet allDocs() throws XPathException {
        final MutableDocumentSet docs = new DefaultDocumentSet();
        try {
            context.getBroker().getAllXMLResources(docs);
            return docs;
        } catch (final PermissionDeniedException | LockException e) {
            LOG.error(e.getMessage(), e);
            throw new XPathException(this, e);
        }
    }

    private MutableDocumentSet docs(final Sequence args[]) throws XPathException {
        final MutableDocumentSet mdocs = new DefaultDocumentSet();
        for (final Sequence arg : args) {
            final XmldbURI docUri = toURI(arg.itemAt(0).getStringValue());

            try(final LockedDocument lockedDocument = context.getBroker().getXMLResource(docUri, Lock.LockMode.READ_LOCK)) {
                if (lockedDocument == null) {
                    if (context.isRaiseErrorOnFailedRetrieval()) {
                        throw new XPathException(this, ErrorCodes.FODC0002, "can not access '" + docUri + "'");
                    }
                } else {
                    final DocumentImpl doc = lockedDocument.getDocument();
                    if (!doc.getPermissions().validate(context.getBroker().getCurrentSubject(), Permission.READ)) {
                        throw new XPathException(this, "Permission denied: unable to load document " + docUri);
                    }
                    mdocs.add(doc);
                }
            } catch (final PermissionDeniedException e) {
                logger.error("Permission denied", e);
                throw new XPathException(this, "Permission denied: unable to load document " + docUri);
            }
        }
        return mdocs;
    }


    private XmldbURI toURI(final String strUri) throws XPathException {
        XmldbURI uri = XmldbURI.create(strUri);
        if (uri.getCollectionPath().length() == 0) {
            throw new XPathException(this, "Invalid argument to " + XMLDBModule.PREFIX + ":document() function: empty string is not allowed here.");
        }
        if (uri.numSegments() == 1) {
            uri = context.getBaseURI().toXmldbURI().resolveCollectionPath(uri);
        }
        return uri;
    }
}
