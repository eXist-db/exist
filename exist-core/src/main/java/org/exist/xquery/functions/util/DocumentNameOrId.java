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
package org.exist.xquery.functions.util;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.function.Function;

import com.evolvedbinary.j8fu.tuple.Tuple3;
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
import org.exist.xquery.value.*;

import javax.annotation.Nullable;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returnsOpt;
import static org.exist.xquery.functions.util.UtilModule.functionSignature;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
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
        final Item arg = args[0].itemAt(0);
        try {
            switch (getSignature().getName().getLocalPart()) {

                case FSN_DOCUMENT_NAME:
                    if (arg.getType() == Type.STRING || arg.getType() == Type.ANY_URI) {
                        return Sequence.of(getFromDocument(XmldbURI.create(arg.getStringValue()), DocumentImpl::getFileURI));
                    } else if (arg instanceof NodeProxy) {
                        return Sequence.of(getFromDocument(((NodeProxy) arg).getOwnerDocument().getURI(), DocumentImpl::getFileURI));
                    } else {
                        return Sequence.EMPTY_SEQUENCE;
                    }

                case FSN_DOCUMENT_ID:
                    if (arg.getType() == Type.STRING || arg.getType() == Type.ANY_URI) {
                        return Sequence.of(getFromDocument(XmldbURI.create(arg.getStringValue()), DocumentImpl::getDocId));
                    } else if (arg instanceof NodeProxy) {
                        return Sequence.of(getFromDocument(((NodeProxy) arg).getOwnerDocument().getURI(), DocumentImpl::getDocId));
                    } else {
                        return Sequence.EMPTY_SEQUENCE;
                    }

                case FSN_ABSOLUTE_RESOURCE_ID:
                    if (arg.getType() == Type.STRING || arg.getType() == Type.ANY_URI) {
                        return Sequence.of(getFromDocument(XmldbURI.create(arg.getStringValue()), DocumentNameOrId::getAbsoluteResourceId));
                    } else if (arg instanceof NodeProxy) {
                        return Sequence.of(getFromDocument(((NodeProxy) arg).getOwnerDocument().getURI(), DocumentNameOrId::getAbsoluteResourceId));
                    } else {
                        return Sequence.EMPTY_SEQUENCE;
                    }

                case FSN_GET_RESOURCE_BY_ABSOLUTE_ID:
                    return getResourceByAbsoluteId((IntegerValue)arg);

                default:
                    throw new XPathException(this, "No function: " + getName() + "#" + getSignature().getArgumentCount());

            }
        } catch (final PermissionDeniedException e) {
            throw new XPathException(this, arg.getStringValue() + ": permission denied to read resource");
        } catch (final LockException le) {
            throw new XPathException(this, "Unable to lock resource", le);
        } catch (final IOException ioe) {
            throw new XPathException(this, "Unable to read binary resource", ioe);
        }
    }

    private @Nullable <T> T getFromDocument(final XmldbURI docUri, final Function<DocumentImpl, T> docFn) throws PermissionDeniedException {
        try (final LockedDocument lockedDoc = context.getBroker().getXMLResource(docUri, LockMode.READ_LOCK)) {
            if (lockedDoc == null) {
                return null;
            } else {
                return docFn.apply(lockedDoc.getDocument());
            }
        }
    }

    private static BigInteger getAbsoluteResourceId(final DocumentImpl doc) {
        return encodeAbsoluteResourceId(doc.getCollection().getId(), doc.getDocId(), doc.getResourceType());
    }

    private Sequence getResourceByAbsoluteId(final IntegerValue ivAbsoluteId)
            throws XPathException, PermissionDeniedException, LockException, IOException {
        final BigInteger absoluteId = ivAbsoluteId.toJavaObject(BigInteger.class);
        final Tuple3<Integer, Integer, Byte> decoded = decodeAbsoluteResourceId(absoluteId);

        final DocumentImpl doc = context.getBroker().getResourceById(decoded._1, decoded._3, decoded._2);
        if (doc != null) {
            try (final ManagedDocumentLock docLock = context.getBroker().getBrokerPool().getLockManager().acquireDocumentReadLock(doc.getURI())) {
                if (doc instanceof BinaryDocument) {
                    final BinaryDocument bin = (BinaryDocument) doc;
                    final InputStream is = context.getBroker().getBinaryResource(bin);
                    return Base64BinaryDocument.getInstance(context, is, this);
                } else {
                    return new NodeProxy(this, doc);
                }
            }
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    private static BigInteger encodeAbsoluteResourceId(final int collectionId, final int documentId, final byte resourceType) {
        BigInteger absoluteId = BigInteger.valueOf(collectionId);
        absoluteId = absoluteId.shiftLeft(32);
        absoluteId = absoluteId.or(BigInteger.valueOf(documentId));
        absoluteId = absoluteId.shiftLeft(1);
        absoluteId = absoluteId.or(BigInteger.valueOf(resourceType & 1));
        return absoluteId;
    }

    private static Tuple3<Integer, Integer, Byte> decodeAbsoluteResourceId(BigInteger absoluteId) {
        final byte resourceType = absoluteId.testBit(0) ? DocumentImpl.BINARY_FILE : DocumentImpl.XML_FILE;
        absoluteId = absoluteId.shiftRight(1);
        final int documentId = absoluteId.and(BigInteger.valueOf(0xFFFFFFFF)).intValue();
        absoluteId = absoluteId.shiftRight(32);
        final int collectionId = absoluteId.and(BigInteger.valueOf(0xFFFFFFFF)).intValue();
        return Tuple(collectionId, documentId, resourceType);
    }
}
