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
package org.exist.xquery.modules.persistentlogin;

import org.exist.xquery.XPathException;
import org.exist.xquery.value.DayTimeDurationValue;
import org.exist.xquery.value.DurationValue;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PersistentLoginTest {

    private static DurationValue oneDay;

    @BeforeClass
    public static void initDuration() throws XPathException {
        oneDay = new DayTimeDurationValue("P1D");
    }

    @Test
    public void newTokensUsePipeSeparator() throws XPathException {
        final PersistentLogin login = new PersistentLogin();
        final PersistentLogin.LoginDetails details = login.register("admin", "admin", oneDay);
        assertTrue(details.toString().contains("|"));
        assertNotNull(login.lookup(details.toString()));
    }

    @Test
    public void lookupAcceptsLegacyColonSeparator() throws XPathException {
        final PersistentLogin login = new PersistentLogin();
        final PersistentLogin.LoginDetails details = login.register("admin", "admin", oneDay);
        final String legacyToken = details.getSeries() + ":" + details.getToken();
        assertNotNull(login.lookup(legacyToken));
    }

    @Test
    public void invalidateAcceptsLegacyColonSeparator() throws XPathException {
        final PersistentLogin login = new PersistentLogin();
        final PersistentLogin.LoginDetails details = login.register("admin", "admin", oneDay);
        final String legacyToken = details.getSeries() + ":" + details.getToken();
        login.invalidate(legacyToken);
        assertNull(login.lookup(legacyToken));
    }
}
