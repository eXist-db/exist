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

import org.exist.dom.QName;
import org.exist.security.*;
import org.exist.xmldb.UserManagementService;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Luigi P. Bai, finder@users.sf.net, 2004
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 */
@Deprecated
public class XMLDBSetCollectionPermissions extends XMLDBAbstractCollectionManipulator {

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("set-collection-permissions", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
        "Sets the permissions of the collection $collection-uri. " +
        XMLDBModule.COLLECTION_URI +
        " $user-id specifies the user which " +
        "will become the owner of the resource, $group-id the group, and " +
        "$permissons the permissions as an xs:integer value. " +
        XMLDBModule.REMEMBER_OCTAL_CALC,
        new SequenceType[] {
            new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
            new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.EXACTLY_ONE, "The user-id"),
            new FunctionParameterSequenceType("group-id", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id"),
            new FunctionParameterSequenceType("permissions", Type.INTEGER, Cardinality.EXACTLY_ONE, "The permissions"),
        },
        new SequenceType(Type.ITEM, Cardinality.EMPTY),
        "You should use sm:chown and sm:chmod from the SecurityManager Module instead."
    );

	
    public XMLDBSetCollectionPermissions(XQueryContext context) {
            super(context, signature);
    }
	
/* (non-Javadoc)
 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
 *
 */
	
    @Override
    public Sequence evalWithCollection(final Collection collection, final Sequence[] args, final Sequence contextSequence) throws XPathException {

        try {
            final UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
            final String user = args[1].getStringValue();
            final String group = args[2].getStringValue();
            final int mode = ((IntegerValue) args[3].convertTo(Type.INTEGER)).getInt();
            
            if (null == user || 0 == user.length()) {
                logger.error("Needs a valid user name, not: " + user);

                throw new XPathException(this, "Needs a valid user name, not: " + user);
            }
            if (null == group || 0 == group.length()) {
                logger.error("Needs a valid group name, not: " + group);

                throw new XPathException(this, "Needs a valid group name, not: " + group);
            }

            final Account usr = ums.getAccount(user);
            if (usr == null) {
                logger.error("Needs a valid user name, not: " + user);
                
                throw new XPathException(this, "Needs a valid user name, not: " + user);
            }

            ums.chown(usr, group);
            ums.chmod(mode);

        } catch(final XMLDBException xe) {
            throw new XPathException(this, "Unable to change collection permissions: " + xe.getMessage(), xe);
        }

        return Sequence.EMPTY_SEQUENCE;
    }

}
