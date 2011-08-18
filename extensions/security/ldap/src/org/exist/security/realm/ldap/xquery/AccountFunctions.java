/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-11 The eXist-db Project
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
 * $Id$
 */
package org.exist.security.realm.ldap.xquery;

import org.exist.dom.QName;
import org.exist.security.Account;
import org.exist.security.AuthenticationException;
import org.exist.security.PermissionDeniedException;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.realm.Realm;
import org.exist.security.realm.ldap.LDAPRealm;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class AccountFunctions extends BasicFunction {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("update-account", LDAPModule.NAMESPACE_URI, LDAPModule.PREFIX),
            "Refreshed the cached LDAP account details from the LDAP directory",
            new SequenceType[] {
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.EMPTY, Cardinality.ZERO)
        )
    };

    
    public AccountFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }
    
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        final SecurityManager sm = context.getBroker().getBrokerPool().getSecurityManager();
        final Realm realm = sm.getRealm("LDAP");
        if(realm == null) {
            throw new XPathException("The LDAP Realm is not in use!");
        }
        final LDAPRealm ldapRealm = (LDAPRealm)realm;
        
        final String accountName = args[0].itemAt(0).getStringValue();
        
        
        final Subject invokingUser = context.getSubject();
        final Account ldapAccount = sm.getAccount(invokingUser, accountName);
        if(ldapAccount == null) {
            throw new XPathException("The Account '" + accountName + "' does not exist!");
        }
        
        try {
            ldapRealm.refreshAccountFromLdap(invokingUser, ldapAccount);
        } catch(PermissionDeniedException pde) {
            throw new XPathException(pde.getMessage(), pde);
        } catch(AuthenticationException ae) {
            throw new XPathException(ae.getMessage(), ae);
        }
        
        return Sequence.EMPTY_SEQUENCE;
    }
}