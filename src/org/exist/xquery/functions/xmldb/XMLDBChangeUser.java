/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
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

import org.apache.log4j.Logger;

import java.net.URISyntaxException;

import org.exist.dom.QName;
import org.exist.security.Account;
import org.exist.security.SchemaType;
import org.exist.security.internal.aider.UserAider;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
public class XMLDBChangeUser extends BasicFunction {
    private static final Logger logger = Logger.getLogger(XMLDBChangeUser.class);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("change-user", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Change properties of an existing database user. " +
            XMLDBModule.NEED_PRIV_USER +
            " $user-id is the username, $password is the password, " +
            "$groups is the sequence of group memberships, " +
            "$home-collection is the home collection. The username, " +
            "$user-id, is mandatory. " +
            "Non-empty values for the other parameters are optional, " +
            "where if empty the existing value is used.",
            new SequenceType[]{
                new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.EXACTLY_ONE, "The user-id"),
                new FunctionParameterSequenceType("password", Type.STRING, Cardinality.ZERO_OR_ONE, "The password"),
                new FunctionParameterSequenceType("groups", Type.STRING, Cardinality.ZERO_OR_MORE, "The groups the user is member of"),
                new FunctionParameterSequenceType("home-collection", Type.STRING, Cardinality.ZERO_OR_ONE, "The user's home collection")
            },
            new SequenceType(Type.ITEM, Cardinality.EMPTY),
            "$home-collection has no effect since 2.0. User the simplified version."
        ),
        new FunctionSignature(
            new QName("change-user", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Change properties of an existing database user. " +
            XMLDBModule.NEED_PRIV_USER +
            " $user-id is the username, $password is the password, " +
            "$groups is the sequence of group memberships, " +
            "The username, " +
            "$user-id, is mandatory. " +
            "Non-empty values for the other parameters are optional, " +
            "where if empty the existing value is used.",
            new SequenceType[]{
                new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.EXACTLY_ONE, "The user-id"),
                new FunctionParameterSequenceType("password", Type.STRING, Cardinality.ZERO_OR_ONE, "The password"),
                new FunctionParameterSequenceType("groups", Type.STRING, Cardinality.ZERO_OR_MORE, "The groups the user is member of")
            },
            new SequenceType(Type.ITEM, Cardinality.EMPTY)
        )
    };
	
    public XMLDBChangeUser(XQueryContext context, FunctionSignature signature) {
	super(context, signature);
    }
	
    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) 
	throws XPathException {

	String userName = args[0].getStringValue();
	Collection collection = null;
		
	try {
	    collection = new LocalCollection(context.getSubject(), context.getBroker().getBrokerPool(), XmldbURI.ROOT_COLLECTION_URI, context.getAccessContext());
	    final UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
	    
	    final Account oldUser = ums.getAccount(userName);
	    if(oldUser == null) {
                logger.error("User " + userName + " not found");
                throw new XPathException(this, "User " + userName + " not found");
	    }

	    final UserAider user = new UserAider(oldUser.getName(), oldUser.getPrimaryGroup()); //dont forget to set the default group
	    
            //copy the umask
            user.setUserMask(oldUser.getUserMask());
            
            //copy the metadata
            for(final SchemaType key : oldUser.getMetadataKeys()) {
                user.setMetadataValue(key, oldUser.getMetadataValue(key));
            }
            
            //copy the status
            user.setEnabled(oldUser.isEnabled());
            
            //change the password?
            if(!args[1].isEmpty()) {
                // set password
                user.setPassword(args[1].getStringValue());
	    } else {
                //use the old password
                user.setEncodedPassword(oldUser.getPassword());
                user.setPasswordDigest(oldUser.getDigestPassword());
	    }
	    
            //change the groups?
            if(!args[2].isEmpty()) {
                // set groups
                for(final SequenceIterator i = args[2].iterate(); i.hasNext(); ) {
                    user.addGroup(i.nextItem().getStringValue());
                }
	    } else {
                user.setGroups(oldUser.getGroups());
            }

	    ums.updateAccount(user);
	} catch(final XMLDBException xe) {
	    logger.error("Failed to update user " + userName, xe);
	    throw new XPathException(this, "Failed to update user " + userName, xe);
        } finally {
            if (null != collection) {
                try {
                    collection.close();
                } catch(final XMLDBException xmldbe) {
                    logger.warn(xmldbe);
                }
            }
	}
	
        return Sequence.EMPTY_SEQUENCE;
    }
}
