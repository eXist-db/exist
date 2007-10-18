/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  Modifications Copyright (C) 2004 Luigi P. Bai
 *  finder@users.sf.net
 *  Licensed as below under the LGPL.
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

import org.exist.dom.QName;
import org.exist.security.User;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
public class XMLDBCreateUser extends BasicFunction {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("create-user", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
			"Create a new user in the database. Arguments are: username, password, group memberships and " +
			"home collection.",
			new SequenceType[]{
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE),
					new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
            },
			new SequenceType(Type.ITEM, Cardinality.EMPTY));
	
	/**
	 * @param context
	 */
	public XMLDBCreateUser(XQueryContext context) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet,
	 *         org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence args[], Sequence contextSequence)
			throws XPathException {

        String user = args[0].getStringValue();
        String pass = args[1].getStringValue();
        User userObj = new User(user, pass);
        
        LOG.info("Attempting to create user "+user);
        
        // changed by wolf: the first group is always the primary group, so we don't need
        // an additional argument
        Sequence groups = args[2];
        int len = groups.getItemCount();
        for (int x = 0; x < len; x++)
            userObj.addGroup(groups.itemAt(x).getStringValue());
        
        if("".equals(args[3].getStringValue()))
            throw new XPathException(getASTNode(), "Empty user collection");
        try {
        	userObj.setHome(new AnyURIValue(args[3].getStringValue()).toXmldbURI());
        } catch(XPathException e) {
        	throw new XPathException(getASTNode(),"Invalid home collection URI",e);
        }
        Collection collection = null;
		try {
            collection = new LocalCollection(context.getUser(), context.getBroker().getBrokerPool(), XmldbURI.ROOT_COLLECTION_URI, context.getAccessContext());
			UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
			ums.addUser(userObj);
			
		} catch (XMLDBException xe) {
			throw new XPathException(getASTNode(), "Failed to create new user " + user, xe);
        } finally {
            if (null != collection)
                try { collection.close(); } catch (XMLDBException e) { /* ignore */ }
		}
        return Sequence.EMPTY_SEQUENCE;
	}
}
