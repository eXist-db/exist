/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2010 The eXist Project
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
import org.exist.security.Group;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.securitymanager.FindGroupFunction;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
@Deprecated
public class XMLDBGroupExists extends BasicFunction {
	
    protected static final Logger logger = LogManager.getLogger(XMLDBCreateUser.class);

    public final static FunctionSignature signatures[] = {

        new FunctionSignature(
            new QName("group-exists", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Determines whether a group exists. $group is the group name, The current user must have permission to access the group, i.e. be a member of the group or DBA",
            new SequenceType[]{
                new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group name")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() or false() indicating the outcome of the operation"),
            FindGroupFunction.FNS_GROUP_EXISTS
        ),
    };
	
    /**
     * @param context
     */
    public XMLDBGroupExists(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet,
     *         org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    @Override
    public Sequence eval(Sequence args[], Sequence contextSequence) throws XPathException {

        final String groupName = args[0].getStringValue();

        final DBBroker broker = context.getBroker();
        final Subject currentUser = broker.getCurrentSubject();

        if(currentUser.hasGroup(groupName)) {
            return BooleanValue.TRUE;
        } else if(currentUser.hasDbaRole()) {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            final Group group = sm.getGroup(groupName);
            return BooleanValue.valueOf(group != null);
        } else {
            throw new XPathException("You do not have permission to determine if the group exists");
        }
    }
}