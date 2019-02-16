/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2013 The eXist Project
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

import org.exist.dom.QName;
import org.exist.security.Permission;
import org.exist.security.PermissionFactory;
import org.exist.security.Account;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.UserManagementService;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Luigi P. Bai, finder@users.sf.net, 2004
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
@Deprecated
public class XMLDBSetResourcePermissions extends XMLDBAbstractCollectionManipulator {
	protected static final Logger logger = LogManager.getLogger(XMLDBSetResourcePermissions.class);
	
        public final static FunctionSignature signature = new FunctionSignature(
            new QName("set-resource-permissions", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Sets the permissions of the resource $resource in collection $collection-uri. " +
            XMLDBModule.COLLECTION_URI +
            " $user-id specifies the user which " + 
            "will become the owner of the resource, $group-id the group, and " +
            " $permissions the permissions as an xs:integer value. " +
            XMLDBModule.REMEMBER_OCTAL_CALC,
            new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
                new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "The resource"),
                new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.EXACTLY_ONE, "The user-id"),
                new FunctionParameterSequenceType("group-id", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id"),
                new FunctionParameterSequenceType("permissions", Type.INTEGER, Cardinality.EXACTLY_ONE, "The permissions"),
            },
            new SequenceType(Type.ITEM, Cardinality.EMPTY),
            "You should use sm:chown and sm:chmod from the SecurityManager Module instead."
        );
	
	public XMLDBSetResourcePermissions(XQueryContext context) {
		super(context, signature);
	}
	
/* (non-Javadoc)
 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
 *
 */
	
    @Override
    public Sequence evalWithCollection(final Collection collection, final Sequence[] args, final Sequence contextSequence)
		throws XPathException {

        try {
            final Resource res = collection.getResource(new AnyURIValue(args[1].getStringValue()).toXmldbURI().toString());
            if (res != null) {
                final UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
                final String user = args[2].getStringValue();
                final String group = args[3].getStringValue();
                final int mode = ((IntegerValue) args[4].convertTo(Type.INTEGER)).getInt();

                if (null == user || 0 == user.length()) {
                    logger.error("Needs a valid user name, not: " + user);
                    
                    throw new XPathException(this, "Needs a valid user name, not: " + user);
                }
                if (null == group || 0 == group.length()) {
                    logger.error("Needs a valid group name, not: " + group);
                    
                    throw new XPathException(this, "Needs a valid group name, not: " + group);
                }
                
                // Must actually get a User object for the Permission...
                final Account usr = ums.getAccount(user);
                if (usr == null) {
                    logger.error("Needs a valid user name, not: " + user);
                    
                    throw new XPathException(this, "Needs a valid user name, not: " + user);
                }


                ums.chown(res, usr, group);
                ums.chmod(mode);

            } else {
                logger.error("Unable to locate resource " + args[1].getStringValue());

                throw new XPathException(this, "Unable to locate resource " + args[1].getStringValue());
            }
        } catch (final XMLDBException xe) {
            throw new XPathException(this, "Unable to change resource permissions:" + xe.getMessage(), xe);
        }

        return Sequence.EMPTY_SEQUENCE;
    }

}
