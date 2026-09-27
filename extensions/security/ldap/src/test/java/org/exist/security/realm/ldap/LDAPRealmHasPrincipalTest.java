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
package org.exist.security.realm.ldap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression test for https://github.com/eXist-db/exist/issues/5942.
 *
 * <p>{@code ConfigurationDocumentTrigger} calls {@code hasAccount}/{@code hasGroup} as a guard to
 * check whether a principal already exists <em>before</em> fetching it, specifically so that
 * checking a not-yet-cached LDAP principal doesn't itself trigger LDAP account/group creation
 * (which would recurse: create -> store -> validate -> fire trigger -> check exists -> create
 * again -> ... -> {@code StackOverflowError}, see {@code ConfigurationDocumentTrigger.java}'s
 * comment on {@code processPrincipal}). That guard only works if {@code hasAccount}/
 * {@code hasGroup} are pure cache checks - they must never fall through to
 * {@code getAccount}/{@code getGroup}, which perform the LDAP lookup and, on a cache miss,
 * create the principal.
 *
 * <p>This is deliberately server-free: the realm below is constructed with no
 * {@code SecurityManager} (only {@code hasAccount}/{@code hasGroup} are exercised, which by
 * contract touch only the realm's own local cache). If either method regressed to calling the
 * creating method, the very first thing it would do on this cache miss is dereference the null
 * {@code SecurityManager} - so a regression here fails fast with an NPE instead of silently
 * reproducing the recursion (that requires a live directory and a real account-creation cycle;
 * see the LDAP module's docs for a Docker-based reproduction harness).
 */
public class LDAPRealmHasPrincipalTest {

    private static final String CONFIG =
            "<realm xmlns=\"http://exist-db.org/Configuration\" id=\"LDAP\">" +
            "  <context>" +
            "    <url>ldap://localhost:1</url>" +
            "  </context>" +
            "</realm>";

    private static LDAPRealm realm;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        try (final InputStream is = UnsynchronizedByteArrayInputStream.builder().setByteArray(CONFIG.getBytes(UTF_8)).get()) {
            final Configuration config = Configurator.parse(is);
            realm = new LDAPRealm(null, config);
        }
    }

    @Test
    public void hasAccountOnCacheMissDoesNotTriggerAccountCreation() {
        // no SecurityManager is set on the realm; getAccount() would NPE the instant it fell
        // through to the LDAP lookup, so this only passes if hasAccount() stayed a pure cache
        // check.
        assertFalse(realm.hasAccount("nobody@example.org"));
    }

    @Test
    public void hasGroupOnCacheMissDoesNotTriggerGroupCreation() {
        assertFalse(realm.hasGroup("nogroup"));
    }
}
