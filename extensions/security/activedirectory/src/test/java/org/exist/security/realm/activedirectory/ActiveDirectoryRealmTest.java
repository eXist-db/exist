/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.security.realm.activedirectory;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.security.AuthenticationException;
import org.exist.security.Subject;
import org.exist.util.io.FastByteArrayInputStream;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ActiveDirectoryRealmTest {

	private static String config = 
		"<ActiveDirectory xmlns='http://exist-db.org/Configuration'>" +
		"	<context>" +
//		"		principalPattern='CN={0},OU=users,DC=bnb,DC=bulungur,dc=nb' " +
//		"		searchBase='ou=users,dc=bnb,dc=bulungur,dc=nb' " +
		"		<url>ldap://fake.com:389</url>" +
//		"		<authentication>strong</authentication>" +
		"	</context>" +
		"</ActiveDirectory>";

	private static ActiveDirectoryRealm realm;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		InputStream is = new FastByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8));
		
		Configuration config = Configurator.parse(is);

		realm = new ActiveDirectoryRealm(null, config);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * Test method for {@link org.exist.security.realm.activedirectory.ActiveDirectoryRealm#authenticate(java.lang.String, java.lang.Object)}.
	 */
	@Ignore
	@Test
	public void testAuthenticate() {
		Subject currentUser = null;
		try {
			currentUser = realm.authenticate("accounter@fake.com", "password");
		} catch (AuthenticationException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
		assertNotNull(currentUser);
	}

}
