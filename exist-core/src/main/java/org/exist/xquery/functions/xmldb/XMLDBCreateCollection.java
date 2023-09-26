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
package org.exist.xquery.functions.xmldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.Cardinality;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.Item;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Implements eXist's xmldb:create-collection() function.
 *
 * @author wolf
 */
public class XMLDBCreateCollection extends XMLDBAbstractCollectionManipulator {
	private static final Logger logger = LogManager.getLogger(XMLDBCreateCollection.class);

	public final static FunctionSignature SIGNATURE_WITH_PARENT = new FunctionSignature(
			new QName("create-collection", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
			"Create a new collection with name $new-collection as a child of " +
					"$target-collection-uri. " + XMLDBModule.COLLECTION_URI +
					"Returns the path to the new collection if successfully created, " +
					"otherwise the empty sequence.",
			new SequenceType[]{
					new FunctionParameterSequenceType("target-collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The target collection URI"),
					new FunctionParameterSequenceType("new-collection", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the new collection to create")},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the path to the new collection if successfully created, otherwise the empty sequence"));

	public final static FunctionSignature SIGNATURE_WITH_URI = new FunctionSignature(
			new QName("create-collection", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
			"Create a new collection by specifying the full uri  $collection-uri. " + XMLDBModule.COLLECTION_URI +
					"Returns the path to the new collection if successfully created, " +
					"otherwise the empty sequence.",
			new SequenceType[]{
					new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The new collection URI")},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the path to the new collection if successfully created, otherwise the empty sequence"));

	public XMLDBCreateCollection(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet,
	 *         org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
			throws XPathException {
		String collectionName = null;
		if(2 == args.length) {
			collectionName = args[1].getStringValue();
		} else if(1 == args.length) {
			collectionName = args[0].getStringValue();
		}

		try {
			final Collection newCollection = createCollectionPath(collection, collectionName);

			if (newCollection == null)
			{return Sequence.EMPTY_SEQUENCE;}
			else
			{return new StringValue(this, newCollection.getName());}

		} catch (final XMLDBException e) {
			logger.error("Unable to create new collection {}", collectionName, e);
			throw new XPathException(this, "failed to create new collection " + collectionName + ": " + e.getMessage(), e);
		}
	}


	/**
	 * Override of the eval method so we can create
	 * a collection using just its path.
	 * */
	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence)
			throws XPathException {
		int paramNumber = 0;

		if (0 == args.length) {
			throw new XPathException(this, "Expected a collection as the argument " + (paramNumber + 1) + ".");
		} else if (2 == args.length) {
			return super.eval(args, contextSequence);
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
					collection = getLocalCollection(this, context, internalCol.getURI().toString());
					return new StringValue(this, collection.getName());
				} catch (final XMLDBException e) {
					if (logger.isDebugEnabled()) {
						logger.debug("Couldn't find parent collection, creating a new one.");
					}
				}
			} else {
				return Sequence.EMPTY_SEQUENCE;
			}
		}

		final String collectionURI = args[paramNumber].getStringValue();
		if (collection == null && collectionURI != null) {
			try {
				collection = getCollection(this, context, collectionURI, Optional.empty(), Optional.empty());
				return new StringValue(this, collection.getName());
			} catch (final XMLDBException xe) {
				if (logger.isDebugEnabled()) {
					logger.debug("Couldn't find parent collection, creating a new one.");
				}
			}
		} else {
			try {
				return new StringValue(this, collection.getName());
			} catch (XMLDBException e) {
                throw new RuntimeException(e);
            }
        }

		Sequence s = Sequence.EMPTY_SEQUENCE;
		try {
			collection = getRootCollection(context, collectionURI);
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

	public  Collection getRootCollection(final XQueryContext context, final String collectionUri) throws XPathException {
		Collection rootCollection = null;
		try {
			rootCollection = new LocalCollection(context.getSubject(), context.getBroker().getBrokerPool(),  XmldbURI.xmldbUriFor("/db", false));
		} catch (final XMLDBException | URISyntaxException e) {
			throw new XPathException(this, "Failed to access the root collection", e);
		}
		return rootCollection;
	}
}
