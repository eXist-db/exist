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
package org.exist.security.realm.ldap.xquery;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
import org.exist.security.realm.Realm;
import org.exist.security.realm.ldap.LDAPRealm;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class AccountFunctions extends BasicFunction {

    public static final FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("update-account", LDAPModule.NAMESPACE_URI, LDAPModule.PREFIX),
                    "Refreshed the cached LDAP account details from the LDAP directory",
                    new SequenceType[]{
                            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
                    },
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
            )
    };


    public AccountFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final SecurityManager sm = context.getBroker().getBrokerPool().getSecurityManager();
        final LDAPRealm ldapRealm = getLdapRealm(sm);
        final String accountName = args[0].itemAt(0).getStringValue();

        final Account ldapAccount = sm.getAccount(accountName);
        if (ldapAccount == null)
            throw new XPathException(this, "The Account '" + accountName + "' does not exist!");

        try {
            ldapRealm.refreshAccountFromLdap(ldapAccount);
        } catch (final PermissionDeniedException | AuthenticationException pde) {
            throw new XPathException(this, pde);
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    private LDAPRealm getLdapRealm(final SecurityManager sm) throws XPathException {
        try {
            final Method mFindRealm = sm.getClass().getDeclaredMethod("findRealmForRealmId", String.class);
            mFindRealm.setAccessible(true);
            final Realm realm = (Realm) mFindRealm.invoke(sm, LDAPRealm.ID);
            if (realm == null) {
                throw new XPathException(this, "The LDAP Realm is not in use!");
            }
            return (LDAPRealm) realm;

        } catch (final NoSuchMethodException ex) {
            throw new XPathException(this, "The LDAP Realm is not in use!", ex);
        } catch (final SecurityException | IllegalArgumentException | IllegalAccessException se) {
            throw new XPathException(this, "Permission to access the LDAP Realm is denied: " + se.getMessage(), se);
        } catch (final InvocationTargetException ite) {
            throw new XPathException(this, "An error occured whilst accessing the LDAP Realm: " + ite.getMessage(), ite);
        }
    }
}