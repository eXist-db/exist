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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.security.Account;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.security.realm.Realm;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual reproduction harness for https://github.com/eXist-db/exist/issues/5942 - drives
 * {@link SecurityManagerImpl#getAccount(String)} against a live LDAP directory to check whether
 * a freshly-created LDAP account is actually cached, or whether the second lookup re-triggers
 * the whole LDAP round-trip (it originally reproduced a {@code StackOverflowError}, fixed by
 * making {@link LDAPRealm#hasAccount(String)}/{@link LDAPRealm#hasGroup(String)} pure cache
 * checks - see {@link LDAPRealmHasPrincipalTest} for the CI-safe regression test of that fix).
 *
 * <p>{@code @Disabled}d: this needs a live directory on {@code localhost:3389} and is not meant to
 * run as part of the normal build (it also matches maven-failsafe-plugin's default
 * {@code **&#47;*IT.java} pattern, so without this annotation it would run - and fail - during
 * the "integration" CI job, which has no LDAP server). Run manually against:
 * <pre>
 *   docker run -d --name exist-test-ldap -p 3389:389 \
 *     -e LDAP_DOMAIN=example.org -e LDAP_ADMIN_PASSWORD=admin osixia/openldap:1.5.0
 * </pre>
 * then seed:
 * <pre>
 *   dn: ou=people,dc=example,dc=org
 *   objectClass: organizationalUnit
 *   ou: people
 *
 *   dn: ou=groups,dc=example,dc=org
 *   objectClass: organizationalUnit
 *   ou: groups
 *
 *   dn: uid=foo,ou=people,dc=example,dc=org
 *   objectClass: inetOrgPerson
 *   cn: foo
 *   sn: foo
 *   uid: foo@example.org
 *   description: uid=foo,ou=people,dc=example,dc=org
 *   employeeNumber: S-1-5-21-111-222-333
 *   employeeType: 500
 *
 *   dn: cn=testgroup,ou=groups,dc=example,dc=org
 *   objectClass: groupOfNames
 *   objectClass: extensibleObject
 *   cn: testgroup
 *   member: uid=foo,ou=people,dc=example,dc=org
 *   employeeNumber: S-1-5-21-111-222-500
 * </pre>
 * (the group's {@code employeeNumber} is the domain prefix of the user's SID with the user's own
 * last RID replaced by {@code employeeType} - see {@code LDAPRealm.getPrimaryGroupSID}; it is
 * <em>not</em> the user's SID with the RID appended).
 */
@Disabled("Requires a live LDAP directory on localhost:3389 - see class Javadoc. Not run as part of"
        + " the normal build; run manually to reproduce/verify fixes for issue #5942.")
@SuppressWarnings("PMD.ClassNamingConventions") // Failsafe *IT suffix; not a JUnit *Test class
public class LDAPAccountCachingIT {

    @RegisterExtension
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    private static final String CONFIG =
            "<realm xmlns=\"http://exist-db.org/Configuration\" id=\"LDAP\" version=\"1.0\" principals-are-case-insensitive=\"true\">" +
            "  <context>" +
            "    <authentication>simple</authentication>" +
            "    <url>ldap://localhost:3389</url>" +
            "    <domain>example.org</domain>" +
            "    <search>" +
            "      <base>dc=example,dc=org</base>" +
            "      <default-username>cn=admin,dc=example,dc=org</default-username>" +
            "      <default-password>admin</default-password>" +
            "      <account>" +
            "        <search-filter-prefix>(objectClass=inetOrgPerson)</search-filter-prefix>" +
            "        <search-attribute key=\"name\">uid</search-attribute>" +
            "        <search-attribute key=\"dn\">description</search-attribute>" +
            "        <search-attribute key=\"objectSid\">employeeNumber</search-attribute>" +
            "        <search-attribute key=\"primaryGroupID\">employeeType</search-attribute>" +
            "      </account>" +
            "      <group>" +
            "        <search-filter-prefix>(objectClass=groupOfNames)</search-filter-prefix>" +
            "        <search-attribute key=\"name\">cn</search-attribute>" +
            "        <search-attribute key=\"objectSid\">employeeNumber</search-attribute>" +
            "        <search-attribute key=\"member\">member</search-attribute>" +
            "      </group>" +
            "    </search>" +
            "  </context>" +
            "</realm>";

    @Test
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // test-only plumbing, see comment below
    public void repeatedGetAccountUsesCacheInsteadOfRecreatingTheAccount() throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        final SecurityManagerImpl sm = (SecurityManagerImpl) pool.getSecurityManager();

        final LDAPRealm realm;
        try (final InputStream is = UnsynchronizedByteArrayInputStream.builder().setByteArray(CONFIG.getBytes(UTF_8)).get()) {
            final Configuration config = Configurator.parse(is);
            realm = new LDAPRealm(sm, config);
        }

        // register the realm the same way SecurityManagerImpl.attach() would if it had been
        // present in /db/system/security/config.xml from the start (see the @ConfigurationFieldClassMask
        // on SecurityManagerImpl.realms - this is test-only plumbing, not a change in behaviour).
        final Field realmsField = SecurityManagerImpl.class.getDeclaredField("realms");
        realmsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<Realm> realms = (List<Realm>) realmsField.get(sm);
        realms.add(realm);

        try (final DBBroker broker = pool.get(Optional.of(sm.getSystemSubject()))) {
            final TransactionManager txnMgr = pool.getTransactionManager();
            try (final Txn txn = txnMgr.beginTransaction()) {
                realm.start(broker, txn);
                txnMgr.commit(txn);
            }

            final Account first = sm.getAccount("foo@example.org");
            assertNotNull(first, "Account should be found and created in the database on first lookup");

            final Account second = sm.getAccount("foo@example.org");
            assertNotNull(second, "Account should still be found on second lookup");
            assertSame(first, second, "Second lookup should hit the realm's cache, not recreate the account");
        } finally {
            realms.remove(realm);
        }
    }
}
