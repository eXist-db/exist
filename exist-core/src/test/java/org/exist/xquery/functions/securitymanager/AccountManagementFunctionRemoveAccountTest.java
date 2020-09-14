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
package org.exist.xquery.functions.securitymanager;

import com.evolvedbinary.j8fu.function.Runnable3E;
import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.security.AuthenticationException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.Rule;
import org.junit.Test;

import java.util.Optional;

public class AccountManagementFunctionRemoveAccountTest {

    @Rule
    public final ExistEmbeddedServer existWebServer = new ExistEmbeddedServer(true, true);

    @Test(expected = PermissionDeniedException.class)
    public void cannotDeleteSystemAccount() throws XPathException, PermissionDeniedException, EXistException, AuthenticationException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final Subject admin = pool.getSecurityManager().authenticate(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        extractPermissionDenied(() -> {
            xqueryRemoveAccount(SecurityManager.SYSTEM, Optional.of(admin));
        });
    }

    @Test(expected = PermissionDeniedException.class)
    public void cannotDeleteDbaAccount() throws XPathException, PermissionDeniedException, EXistException {
        extractPermissionDenied(() -> {
            xqueryRemoveAccount(SecurityManager.DBA_USER);
        });
    }

    @Test(expected = PermissionDeniedException.class)
    public void cannotDeleteGuestAccount() throws XPathException, PermissionDeniedException, EXistException {
        extractPermissionDenied(() -> {
            xqueryRemoveAccount(SecurityManager.GUEST_USER);
        });
    }

    @Test(expected = PermissionDeniedException.class)
    public void cannotDeleteUnknownAccount() throws XPathException, PermissionDeniedException, EXistException {
        extractPermissionDenied(() -> {
            xqueryRemoveAccount(SecurityManager.UNKNOWN_USER);
        });
    }

    private Sequence xqueryRemoveAccount(final String username) throws XPathException, PermissionDeniedException, EXistException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final Optional<Subject> asUser = Optional.of(pool.getSecurityManager().getSystemSubject());
        return xqueryRemoveAccount(username, asUser);
    }

    private Sequence xqueryRemoveAccount(final String username, final Optional<Subject> asUser) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existWebServer.getBrokerPool();

        final String query =
                "import module namespace sm = 'http://exist-db.org/xquery/securitymanager';\n" +
                        "sm:remove-account('" + username + "')";

        try (final DBBroker broker = pool.get(asUser)) {
            final XQuery xquery = existWebServer.getBrokerPool().getXQueryService();
            final Sequence result = xquery.execute(broker, query, null);
            return result;
        }
    }

    private static void extractPermissionDenied(final Runnable3E<XPathException, PermissionDeniedException, EXistException> runnable) throws XPathException, PermissionDeniedException, EXistException {
        try {
            runnable.run();
        } catch (final XPathException e) {
            if (e.getCause() != null && e.getCause() instanceof PermissionDeniedException) {
                throw (PermissionDeniedException)e.getCause();
            } else {
                throw e;
            }
        }
    }
}
