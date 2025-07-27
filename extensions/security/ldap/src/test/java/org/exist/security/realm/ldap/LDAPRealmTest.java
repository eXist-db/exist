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

import static org.junit.Assert.*;

import java.io.InputStream;

import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.security.AuthenticationException;
import org.exist.security.Account;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class LDAPRealmTest {

	private static String config = 
		"<realm id=\"LDAP\">" +
		"	<context>" +
		"		<principalPattern>cn={0},dc=local</principalPattern>" +
		"		<url>ldap://localhost:389</url>" +
        "	</context>" +
		"</realm>";

	private static LDAPRealm realm;
	
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		try (final InputStream is = UnsynchronizedByteArrayInputStream.builder().setByteArray(config.getBytes(UTF_8)).get()) {
			Configuration config = Configurator.parse(is);
			realm = new LDAPRealm(null, config);
		}
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() {
	}

	/**
	 * Test method for {@link org.exist.security.realm.ldap.LDAPRealm#authenticate(java.lang.String, java.lang.Object)}.
	 */
	@Ignore
	@Test
	public void testAuthenticate() {
		Account account = null;
		try {
			account = realm.authenticate("admin", "passwd");
		} catch (AuthenticationException e) {
			fail(e.getMessage());
		}
		
		assertNotNull(account);
	}

}
