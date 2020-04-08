/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
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
 */
package org.exist.xquery.functions.util;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.Objects;

import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.NodeProxy;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryDocument;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returnsOpt;
import static org.exist.xquery.functions.util.UtilModule.functionSignature;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class DocumentNameOrId extends BasicFunction {

    private static final FunctionParameterSequenceType PRAM_NODE_OR_PATH = param(
            "node-or-path",
            Type.ITEM,
            "The node or a string path pointing to a resource in the database.");


    private static final String FSN_DOCUMENT_NAME = "document-name";
    public final static FunctionSignature FS_DOCUMENT_NAME = functionSignature(
            FSN_DOCUMENT_NAME,
            "Returns the name of a document (excluding the collection path). The argument can either be " +
                    "a node or a string path pointing to a resource in the database. If the resource does not exist or the node " +
                    "does not belong to a stored document, the empty sequence is returned.",
            returnsOpt(Type.STRING, "the name of the document"),
            PRAM_NODE_OR_PATH
    );

    private static final String FSN_DOCUMENT_ID = "document-id";
    public final static FunctionSignature FS_DOCUMENT_ID = functionSignature(
            FSN_DOCUMENT_ID,
            "Returns the internal integer id of a document. The argument can either be " +
                    "a node or a string path pointing to a resource in the database. If the resource does not exist or the node " +
                    "does not belong to a stored document, the empty sequence is returned.",
            returnsOpt(Type.INT, "the ID of the document"),
            PRAM_NODE_OR_PATH
    );

    private static final String FSN_ABSOLUTE_RESOURCE_ID = "absolute-resource-id";
    public final static FunctionSignature FS_ABSOLUTE_RESOURCE_ID = functionSignature(
            FSN_ABSOLUTE_RESOURCE_ID,
            "Returns the absolute internal id of a resource as a 65 bit number. The first 32 bits are the collection id, the next 32 bits are the document id, the last bit is the document type. The argument can either be " +
                    "a node or a string path pointing to a resource in the database. If the resource does not exist or the node " +
                    "does not belong to a stored document, the empty sequence is returned.",
            returnsOpt(Type.INTEGER, "the absolute ID of the resource"),
            PRAM_NODE_OR_PATH
    );


    private static final String FSN_GET_RESOURCE_BY_ABSOLUTE_ID = "get-resource-by-absolute-id";
    public final static FunctionSignature FS_GET_RESOURCE_BY_ABSOLUTE_ID = functionSignature(
            FSN_GET_RESOURCE_BY_ABSOLUTE_ID,
            "Returns the resource indicated by its absolute internal id. The first 32 bits are the collection id, the next 32 bits are the document id, the last bit is the document type. If the resource does not exist, the empty sequence is returned.",
            returnsOpt(Type.ITEM, "The resource from the database. A document() if its an XML resource, or an xs:base64binary otherwise"),
            param("absolute-id", Type.INTEGER, "The absolute id of a resource in the database.")
    );

    public DocumentNameOrId(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        Sequence result = Sequence.EMPTY_SEQUENCE;
        try {

            if (Type.subTypeOf(args[0].getItemType(), Type.NODE)) {
                final NodeValue node = (NodeValue) args[0].itemAt(0);
                if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                    final NodeProxy proxy = (NodeProxy) node;
                    final DocumentImpl doc = proxy.getOwnerDocument();
                    if (doc != null) {
                        try (final ManagedDocumentLock docLock = context.getBroker().getBrokerPool().getLockManager().acquireDocumentReadLock(doc.getURI())) {
                            return documentNameOrId(doc);
                        }
                    } else {
                        return resourceById(args);
                    }
                }
            } else if (Type.subTypeOf(args[0].getItemType(), Type.STRING)) {
                final String path = args[0].getStringValue();
                try (final LockedDocument lockedDoc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), LockMode.READ_LOCK)) {
                    if (lockedDoc != null) {
                        return documentNameOrId(lockedDoc.getDocument());
                    } else {
                        return resourceById(args);
                    }
                }
            }

        } catch (final LockException le) {
            throw new XPathException(this, "Unable to lock resource", le);
        } catch (final IOException ioe) {
            throw new XPathException(this, "Unable to read binary resource", ioe);
        } catch (final URISyntaxException e) {
            throw new XPathException(this, "Invalid resource uri: " + args[0].getStringValue(), e);
        } catch (final PermissionDeniedException e) {
            throw new XPathException(this, args[0].getStringValue() + ": permission denied to read resource");
        }

        return result;
    }

    private Sequence documentNameOrId(final DocumentImpl doc) throws XPathException {
        Objects.nonNull(doc);

        final Sequence result;
        final String fnName = getSignature().getName().getLocalPart();
        if (fnName.equals(FSN_DOCUMENT_NAME)) {
            result = new StringValue(doc.getFileURI().toString());
        } else if (fnName.equals(FSN_DOCUMENT_ID)) {
            result = new IntegerValue(doc.getDocId(), Type.INT);
        } else if (fnName.equals(FSN_ABSOLUTE_RESOURCE_ID)) {
            BigInteger absoluteId = BigInteger.valueOf(doc.getCollection().getId());
            absoluteId = absoluteId.shiftLeft(32);
            absoluteId = absoluteId.or(BigInteger.valueOf(doc.getDocId()));
            absoluteId = absoluteId.shiftLeft(1);
            absoluteId = absoluteId.or(BigInteger.valueOf(doc.getResourceType() & 1));
            result = new IntegerValue(absoluteId, Type.INTEGER);
        } else {
            result = Sequence.EMPTY_SEQUENCE;
        }

        return result;
    }

    private Sequence resourceById(final Sequence args[]) throws IOException, PermissionDeniedException, XPathException, LockException {
        final String fnName = getSignature().getName().getLocalPart();
        if (fnName.equals(FSN_GET_RESOURCE_BY_ABSOLUTE_ID)) {
            final IntegerValue absoluteIdParam = (IntegerValue) args[0].itemAt(0);
            BigInteger absoluteId = absoluteIdParam.toJavaObject(BigInteger.class);

            final byte resourceType = absoluteId.testBit(0) ? DocumentImpl.BINARY_FILE : DocumentImpl.XML_FILE;
            absoluteId = absoluteId.shiftRight(1);
            final int documentId = absoluteId.and(BigInteger.valueOf(0xFFFFFFFF)).intValue();
            absoluteId = absoluteId.shiftRight(32);
            final int collectionId = absoluteId.and(BigInteger.valueOf(0xFFFFFFFF)).intValue();

            final DocumentImpl doc = context.getBroker().getResourceById(collectionId, resourceType, documentId);
            if (doc != null) {
                try (final ManagedDocumentLock docLock = context.getBroker().getBrokerPool().getLockManager().acquireDocumentReadLock(doc.getURI())) {
                    if (doc instanceof BinaryDocument) {
                        final BinaryDocument bin = (BinaryDocument) doc;
                        final InputStream is = context.getBroker().getBinaryResource(bin);
                        final Base64BinaryDocument b64doc = Base64BinaryDocument.getInstance(context, is);
                        return b64doc;
                    } else {
                        return new NodeProxy(doc);
                    }
                }
            }

            return Sequence.EMPTY_SEQUENCE;
        } else {
            return Sequence.EMPTY_SEQUENCE;
        }
    }
}