/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryDocument;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 *
 */
public class DocumentNameOrId extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(DocumentNameOrId.class);

        private final static QName QN_DOCUMENT_NAME = new QName("document-name", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);
        private final static QName QN_DOCUMENT_ID = new QName("document-id", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);
        private final static QName QN_ABSOLUTE_RESOURCE_ID = new QName("absolute-resource-id", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);
        private final static QName QN_GET_RESOURCE_BY_ABSOLUTE_ID = new QName("get-resource-by-absolute-id", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);
        
        private final static SequenceType[] PARAMS = new SequenceType[] {
                new FunctionParameterSequenceType("node-or-path", Type.ITEM, Cardinality.EXACTLY_ONE, "The node or a string path pointing to a resource in the database.")
        };
        
	public final static FunctionSignature docNameSignature = new FunctionSignature(
            QN_DOCUMENT_NAME,
            "Returns the name of a document (excluding the collection path). The argument can either be " +
            "a node or a string path pointing to a resource in the database. If the resource does not exist or the node " +
            "does not belong to a stored document, the empty sequence is returned.",
            PARAMS,
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the name of the document")
        );
	
	public final static FunctionSignature docIdSignature = new FunctionSignature(
            QN_DOCUMENT_ID,
            "Returns the internal integer id of a document. The argument can either be " +
            "a node or a string path pointing to a resource in the database. If the resource does not exist or the node " +
            "does not belong to a stored document, the empty sequence is returned.",
            PARAMS,
            new FunctionReturnSequenceType(Type.INT, Cardinality.ZERO_OR_ONE, "the ID of the document")
        );
        
        public final static FunctionSignature absoluteResourceIdSignature = new FunctionSignature(
            QN_ABSOLUTE_RESOURCE_ID,
            "Returns the absolute internal id of a resource as a 65 bit number. The first 32 bits are the collection id, the next 32 bits are the document id, the last bit is the document type. The argument can either be " +
            "a node or a string path pointing to a resource in the database. If the resource does not exist or the node " +
            "does not belong to a stored document, the empty sequence is returned.",
            PARAMS,
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the absolute ID of the resource")
        );
        
        
        public final static FunctionSignature resourceByAbsoluteIdSignature = new FunctionSignature(
            QN_GET_RESOURCE_BY_ABSOLUTE_ID,
            "Returns the resource indicated by its absolute internal id. The first 32 bits are the collection id, the next 32 bits are the document id, the last bit is the document type. If the resource does not exist, the empty sequence is returned.",
            new SequenceType[] {
                new FunctionParameterSequenceType("absolute-id", Type.INTEGER, Cardinality.EXACTLY_ONE, "The absolute id of a resource in the database.")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "The resource from the database. A document() if its an XML resource, or an xs:base64binary otherwise")
        );
	
	public DocumentNameOrId(XQueryContext context, FunctionSignature signature) {
            super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        Sequence result = Sequence.EMPTY_SEQUENCE;
        try {
        
            if(Type.subTypeOf(args[0].getItemType(), Type.NODE)) {
                final NodeValue node = (NodeValue) args[0].itemAt(0);
                if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                    final NodeProxy proxy = (NodeProxy) node;
                    final DocumentImpl doc = proxy.getOwnerDocument();
                    if(doc != null) {
                        try (final ManagedDocumentLock docLock = context.getBroker().getBrokerPool().getLockManager().acquireDocumentReadLock(doc.getURI())){
                            return documentNameOrId(doc);
                        }
                    } else {
                        return resourceById(args);
                    }
                }
            } else if(Type.subTypeOf(args[0].getItemType(), Type.STRING)) {
                final String path = args[0].getStringValue();
                try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), LockMode.READ_LOCK)) {
                    if(lockedDoc != null) {
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
        final QName fnName = getSignature().getName();
        if(fnName.equals(QN_DOCUMENT_NAME)) {
            result = new StringValue(doc.getFileURI().toString());
        } else if(fnName.equals(QN_DOCUMENT_ID)) {
            result = new IntegerValue(doc.getDocId(), Type.INT);
        } else if(fnName.equals(QN_ABSOLUTE_RESOURCE_ID)) {
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
        final QName fnName = getSignature().getName();
        if (fnName.equals(QN_GET_RESOURCE_BY_ABSOLUTE_ID)) {
            final IntegerValue absoluteIdParam = (IntegerValue) args[0].itemAt(0);
            BigInteger absoluteId = absoluteIdParam.toJavaObject(BigInteger.class);

            final byte resourceType = absoluteId.testBit(0) ? DocumentImpl.BINARY_FILE : DocumentImpl.XML_FILE;
            absoluteId = absoluteId.shiftRight(1);
            final int documentId = absoluteId.and(BigInteger.valueOf(0xFFFFFFFF)).intValue();
            absoluteId = absoluteId.shiftRight(32);
            final int collectionId = absoluteId.and(BigInteger.valueOf(0xFFFFFFFF)).intValue();

            final DocumentImpl doc = context.getBroker().getResourceById(collectionId, resourceType, documentId);
            if(doc != null) {
                try(final ManagedDocumentLock docLock = context.getBroker().getBrokerPool().getLockManager().acquireDocumentReadLock(doc.getURI())) {
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