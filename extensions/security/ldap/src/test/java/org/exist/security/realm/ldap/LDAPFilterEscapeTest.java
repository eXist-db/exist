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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Unit tests for the RFC 4515 search-filter escaping performed by
 * {@link LDAPRealm#escapeSearchAttribute(String)}. All filter-building paths in
 * {@link LDAPRealm} funnel user-supplied names through this single method, so it
 * must escape every metacharacter an attacker could use to break out of a filter
 * value: <code>*</code>, <code>(</code>, <code>)</code>, <code>\</code> and NUL.
 */
public class LDAPFilterEscapeTest {

    @Test
    public void plainValueIsLeftUnchanged() {
        assertEquals("alice", LDAPRealm.escapeSearchAttribute("alice"));
    }

    @Test
    public void wildcardsAreEscaped() {
        assertEquals("a\\2aa", LDAPRealm.escapeSearchAttribute("a*a"));
    }

    @Test
    public void parenthesesAreEscaped() {
        assertEquals("\\28x\\29", LDAPRealm.escapeSearchAttribute("(x)"));
    }

    @Test
    public void backslashIsEscapedFirstToAvoidDoubleEscaping() {
        assertEquals("\\5c\\28", LDAPRealm.escapeSearchAttribute("\\" + "("));
    }

    @Test
    public void nulCharacterIsEscaped() {
        assertEquals("a\\00b", LDAPRealm.escapeSearchAttribute("a\u0000b"));
    }

     @Test
    public void combinedMetacharactersAreEscapedAndOrderStable() {
        assertEquals("\\5c\\2a\\28\\29",
                LDAPRealm.escapeSearchAttribute("\\*()"));
     }
}
