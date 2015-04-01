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

import java.util.Optional;
import java.util.StringTokenizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.NodeProxy;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.txn.bridge.InTxnLocalCollection;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * @author Luigi P. Bai, finder@users.sf.net, 2004
 * @author gev
 * @author delirium
 */
public abstract class XMLDBAbstractCollectionManipulator extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(XMLDBAbstractCollectionManipulator.class);

    private final boolean errorIfAbsent;

    private final int paramNumber = 0;  //collecton will be passed as parameter number 0 by default

    public XMLDBAbstractCollectionManipulator(final XQueryContext context, final FunctionSignature signature) {
        this(context, signature, true);
    }

    public XMLDBAbstractCollectionManipulator(final XQueryContext context, final FunctionSignature signature, final boolean errorIfAbsent) {
        super(context, signature);
        this.errorIfAbsent = errorIfAbsent;
    }

    public static LocalCollection getLocalCollection(final XQueryContext context, final String name) throws XMLDBException {
        try {
            return new InTxnLocalCollection(context.getSubject(), context.getBroker().getBrokerPool(), null, new AnyURIValue(name).toXmldbURI());
        } catch (final XPathException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
    }

    public static Collection getCollection(final XQueryContext context, final String collectionUri, final Optional<String> username, final Optional<String> password) throws XMLDBException {
        final Collection collection;
        if (!collectionUri.startsWith("xmldb:")) {
            // Must be a LOCAL collection
            collection = getLocalCollection(context, collectionUri);

        } else if (collectionUri.startsWith("xmldb:exist:///")) {
            // Must be a LOCAL collection
            collection = getLocalCollection(context, collectionUri.replaceFirst("xmldb:exist://", ""));

        } else if (collectionUri.startsWith("xmldb:exist://embedded-eXist-server")) {
            // Must be a LOCAL collection
            collection = getLocalCollection(context, collectionUri.replaceFirst("xmldb:exist://embedded-eXist-server", ""));

        } else if (collectionUri.startsWith("xmldb:exist://localhost")) {
            // Must be a LOCAL collection
            collection = getLocalCollection(context, collectionUri.replaceFirst("xmldb:exist://localhost", ""));

        } else if (collectionUri.startsWith("xmldb:exist://127.0.0.1")) {
            // Must be a LOCAL collection
            collection = getLocalCollection(context, collectionUri.replaceFirst("xmldb:exist://127.0.0.1", ""));

        } else {
            // Right now, the collection is retrieved as GUEST. Need to figure out how to
            // get user information into the URL?
            if (username.isPresent() && password.isPresent()) {
                collection = org.xmldb.api.DatabaseManager.getCollection(collectionUri, username.get(), password.get());
            } else {
                collection = org.xmldb.api.DatabaseManager.getCollection(collectionUri);
            }
        }
        return collection;
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence)
            throws XPathException {

        if (0 == args.length) {
            throw new XPathException(this, "Expected a collection as the argument " + (paramNumber + 1) + ".");
        }

        final boolean collectionNeedsClose = false;

        Collection collection = null;
        final Item item = args[paramNumber].itemAt(0);
        if (Type.subTypeOf(item.getType(), Type.NODE)) {
            final NodeValue node = (NodeValue) item;
            if (logger.isDebugEnabled()) {
                logger.debug("Found node");
            }
            if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                final org.exist.collections.Collection internalCol = ((NodeProxy) node).getOwnerDocument().getCollection();
                if (logger.isDebugEnabled()) {
                    logger.debug("Found node");
                }
                try {
                    //TODO: use xmldbURI
                    collection = getLocalCollection(context, internalCol.getURI().toString());
                    if (logger.isDebugEnabled()) {
                        logger.debug("Loaded collection " + collection.getName());
                    }
                } catch (final XMLDBException e) {
                    throw new XPathException(this, "Failed to access collection: " + internalCol.getURI(), e);
                }
            } else {
                return Sequence.EMPTY_SEQUENCE;
            }
        }

        if (collection == null) {
            //Otherwise, just extract the name as a string:
            final String collectionURI = args[paramNumber].getStringValue();
            if (collectionURI != null) {
                try {
                    collection = getCollection(context, collectionURI, Optional.empty(), Optional.empty());
                } catch (final XMLDBException xe) {
                    if (errorIfAbsent) {
                        throw new XPathException(this, "Could not locate collection: " + collectionURI, xe);
                    }
                    collection = null;
                }
            }
            if (collection == null && errorIfAbsent) {
                throw new XPathException(this, "Unable to find collection: " + collectionURI);
            }
        }

        Sequence s = Sequence.EMPTY_SEQUENCE;
        try {
            s = evalWithCollection(collection, args, contextSequence);
        } finally {
            if (collectionNeedsClose && collection != null) {
                try {
                    collection.close();
                } catch (final Exception e) {
                    throw new XPathException(this, "Unable to close collection", e);
                }
            }
        }
        return s;
    }

    abstract protected Sequence evalWithCollection(final Collection c, final Sequence[] args, final Sequence contextSequence) throws XPathException;

    static public final Collection createCollection(final Collection parentColl, final String name) throws XMLDBException {
        final Collection child = parentColl.getChildCollection(name);
        if (child == null) {
            final CollectionManagementService mgtService = (CollectionManagementService) parentColl.getService("CollectionManagementService", "1.0");
            return mgtService.createCollection(name);
        }
        return child;
    }

    static public final Collection createCollectionPath(final Collection parentColl, final String relPath) throws XMLDBException, XPathException {
        Collection current = parentColl;
        final StringTokenizer tok = new StringTokenizer(new AnyURIValue(relPath).toXmldbURI().toString(), "/");
        while (tok.hasMoreTokens()) {
            final String token = tok.nextToken();
            current = createCollection(current, token);
        }
        return current;
    }

}
