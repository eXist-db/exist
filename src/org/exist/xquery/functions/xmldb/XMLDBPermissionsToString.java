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

import org.exist.dom.QName;
import org.exist.security.Permission;
import org.exist.security.PermissionFactory;
import org.exist.security.internal.aider.UnixStylePermissionAider;
import org.exist.util.SyntaxException;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;

/**
 * @author wolf
 */
public class XMLDBPermissionsToString extends BasicFunction {
    protected static final Logger logger = Logger.getLogger(XMLDBPermissionsToString.class);
    
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("permissions-to-string", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Formats the resource or collection permissions, $permissions, passed as an integer " +
            "value into a string. The returned string shows the permissions following " +
            "the Unix conventions, i.e. all permissions set is returned as " +
            "rwurwurwu, where the first three chars are for user permissions, " +
            "followed by group and other users. 'r' denotes read, 'w' write and 'u' update " +
            "permissions.",
            new SequenceType[] {
                new FunctionParameterSequenceType("permissions", Type.INTEGER, Cardinality.EXACTLY_ONE, "The permissions in xs:integer format")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the permissions as string 'rwu' for, user, group and other")
        ),

        new FunctionSignature(
            new QName("string-to-permissions", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Converts the resource or collection permissions, $permissions-string, " +
            "into an integer representation suitable for use with set-permissions functions. " +
            "The permissions string should be in the format 'rwurwurwu' where r is read, w is write and u is update.",
            new SequenceType[] {
                new FunctionParameterSequenceType("permissions-string", Type.STRING, Cardinality.EXACTLY_ONE, "The permissions string")
            },
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "The permissions integer")
        )
    };
	
    /**
     * @param context
     */
    public XMLDBPermissionsToString(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        if(isCalledAs("permissions-to-string")) {
            int mode = ((IntegerValue)args[0].itemAt(0)).getInt();
            SecurityManager sm = context.getBroker().getBrokerPool().getSecurityManager();
            Subject currentSubject = sm.getDatabase().getSubject();
            Permission perm = PermissionFactory.getPermission(currentSubject.getId(), currentSubject.getDefaultGroup().getId(), mode);
            return new StringValue(perm.toString());
        } else {
            String permissionsString = args[0].itemAt(0).getStringValue();
            try {
                Permission perm = UnixStylePermissionAider.fromString(permissionsString);
                return new IntegerValue(perm.getMode());
            } catch(SyntaxException se) {
                throw new XPathException(se.getMessage(), se);
            }
        }
    }
}