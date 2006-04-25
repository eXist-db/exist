/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
package org.exist.xquery.functions.xmldb;

import java.net.URISyntaxException;

import org.exist.dom.QName;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
public class XMLDBCollection extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("collection", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Get a reference to a collection. The first argument is either a collection path like '" +
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays' or an XMLDB URI like 'xmldb:exist://localhost:8081/" +
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays'. " +
			"The second argument should specify the name of " +
			"a valid user, the third is the password. The method returns a Java object " +
			"type, which can then be used as argument to the create-collection or store " +
			"functions.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				 new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.JAVA_OBJECT, Cardinality.ZERO_OR_ONE));

	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBCollection(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence args[], Sequence contextSequence)	throws XPathException {
        
		String collectionURI = args[0].getStringValue();
		String user = args[1].getStringValue();
		String passwd = args[2].getStringValue();
		
		Collection collection = null;
		try {
            //TODO : revisit according to XmldbURI implementation -pb
			if (!collectionURI.startsWith("xmldb:")) {
				User localUser = context.getUser();
				// Must be a LOCAL collection
				if (!localUser.getName().equals(user))
					localUser = getUser(user, passwd);
				try {
			        collection = new LocalCollection(localUser, context.getBroker().getBrokerPool(), XmldbURI.xmldbUriFor(collectionURI), context.getAccessContext());
				} catch(URISyntaxException e) {
					throw new XMLDBException(ErrorCodes.INVALID_URI,e);
				}
			} else {
				collection = DatabaseManager.getCollection(collectionURI, user, passwd);
			}
		} catch (XMLDBException e) {
            LOG.debug(e.getMessage(), e);
            throw new XPathException(getASTNode(), 
                "Exception while retrieving collection '" + collectionURI + ": " + e.getMessage(), e);
		}
		return collection == null ? Sequence.EMPTY_SEQUENCE : new JavaObjectValue(collection);
	}
	
	private User getUser(String user, String passwd) throws XPathException {
		SecurityManager secman = context.getBroker().getBrokerPool().getSecurityManager();
		User u = secman.getUser(user);
        if (u == null)
            throw new XPathException(getASTNode(), "Unknown user '" + user + "'");
		if (!u.validate(passwd))
            throw new XPathException(getASTNode(), "Wrong password specified for user '" + user + "'");
		return u;
	}
}
