/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import java.net.URISyntaxException;
import org.apache.log4j.Logger;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Dannes Wessels (dannes@exist-db.org)
 */
public class XMLDBSetMimeType extends BasicFunction {
	protected static final Logger logger = Logger.getLogger(XMLDBSetMimeType.class);
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("set-mime-type", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Set the MIME type of the resource $resource-uri." +
            XMLDBModule.ANY_URI,
			new SequenceType[] {
                new FunctionParameterSequenceType("resource-uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The resource URI"),
                new FunctionParameterSequenceType("mime-type", Type.STRING, Cardinality.ZERO_OR_ONE, "The new mime-type, use empty sequence to set default value.")
			},
			new SequenceType(Type.EMPTY, Cardinality.EMPTY)
		);
	
    public XMLDBSetMimeType(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence args[], Sequence contextSequence) throws XPathException {

        // Get handle to Mime-type info
        MimeTable mimeTable = MimeTable.getInstance();

        // Get first parameter
        String pathParameter = new AnyURIValue(args[0].itemAt(0).getStringValue()).toString();

        if (pathParameter.matches("^[a-z]+://.*")) {
            throw new XPathException("Can not set mime-type for resources outside the database.");
        }

        XmldbURI pathUri = null;
        try {
            pathUri = XmldbURI.xmldbUriFor(pathParameter);
        } catch (URISyntaxException ex) {
            LOG.error(ex.getMessage());
            throw new XPathException("Invalid path '" + pathParameter + "'");
        }

        // Verify mime-type input
        MimeType newMimeType = null;
        if (args[1].isEmpty()) {
            // No input, use default mimetype
            newMimeType = mimeTable.getContentTypeFor(pathParameter);

            if (newMimeType == null) {
                throw new XPathException("Unable to determine mimetype for '" + pathParameter + "'");
            }

        } else {
            // Mimetype is provided, check if valid
            newMimeType = mimeTable.getContentType(args[1].getStringValue());

            if (newMimeType == null) {
                throw new XPathException("mime-type '" + args[1].getStringValue() + "' is not supported.");
            }
        }

        // Get mime-type of resource
        String currentValue = getMimeTypeStoredResource(pathUri);
        MimeType currentMimeType = null;
        if (currentValue == null) {
            LOG.error("Resource has no mime-type");
        } else {
            currentMimeType = mimeTable.getContentType(currentValue);
        }

        if (currentMimeType == null) {
            throw new XPathException("Unable to determine mime-type of stored resource.");
        }

        // Check if mimeType are equivalent
        if (newMimeType.isXMLType() == currentMimeType.isXMLType()) {
            // OK
        } else {
            throw new XPathException("New mime-type must be a " + currentMimeType.getXMLDBType() + " mime-type");
        }

        // At this moment it is possible to update the mimetype
        DBBroker broker = context.getBroker();
        BrokerPool brokerPool = broker.getBrokerPool();

        DocumentImpl doc = null;
        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();

        try {
            // relative collection Path: add the current base URI
            pathUri = context.getBaseURI().toXmldbURI().resolveCollectionPath(pathUri);

            // try to open the document and acquire a lock
            doc = (DocumentImpl) broker.getXMLResource(pathUri, Lock.WRITE_LOCK);
            if (doc != null) {
                doc.getMetadata().setMimeType(newMimeType.getName());
                txnManager.commit(txn);
            }

        } catch (Exception e) {
            txnManager.abort(txn);
            logger.error(e.getMessage());
            throw new XPathException(this, e);

        } finally {
            //release all locks
            if (doc != null) {
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            }
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    /**
     * Determine mimetype of currently stored resource. Copied from get-mime-type.
     */
    private String getMimeTypeStoredResource(XmldbURI pathUri) {
        String returnValue = null;
        DocumentImpl doc = null;
        try {
            // relative collection Path: add the current base URI
            pathUri = context.getBaseURI().toXmldbURI().resolveCollectionPath(pathUri);
            // try to open the document and acquire a lock
            doc = (DocumentImpl) context.getBroker().getXMLResource(pathUri, Lock.READ_LOCK);
            if (doc != null) {
                returnValue = ((DocumentImpl) doc).getMetadata().getMimeType();
            }

        } catch (XPathException ex) {
            LOG.error(ex.getMessage());

        } catch (PermissionDeniedException ex) {
            LOG.error(ex.getMessage());

        } finally {

            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }

        return returnValue;
    }
}
