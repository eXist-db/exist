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
package org.exist.xquery.functions;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import io.lacuna.bifurcan.IMap;
import io.lacuna.bifurcan.ISet;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccessUtilTest {

    private static final Pattern PTN_TEST_ACCESS = Pattern.compile("testAccess\\.([^=\\00]+)\\.requires((?:Group)|(?:User))");

    /**
     * Regression test for a capturing-group placement bug: {@code ([^=\x00])+} repeats the
     * single-character group, so {@code Matcher#group(1)} only ever yields the last character
     * matched rather than the whole name. The pattern must be {@code ([^=\x00]+)}, with the
     * quantifier inside the group, so that multi-character access rule names (e.g. environment
     * variable or system property names) are captured in full.
     */
    @Test
    public void parseAccessParametersRetainsFullMultiCharacterName() {
        final Map<String, List<?>> parameters = Map.of(
                "testAccess.MY_SECRET_VAR.requiresGroup", List.of("admins")
        );

        final Tuple2<IMap<String, ISet<String>>, IMap<String, ISet<String>>> accessRules =
                AccessUtil.parseAccessParameters(PTN_TEST_ACCESS, parameters);
        final IMap<String, ISet<String>> accessGroupRules = accessRules._1;

        assertTrue("access rule should be keyed by the full parameter name",
                accessGroupRules.contains("MY_SECRET_VAR"));
        assertFalse("access rule must not be keyed by just the last character of the parameter name",
                accessGroupRules.contains("R"));
    }

    @Test
    public void isAllowedAccessHonoursMultiCharacterNamedGroupRule() {
        final Map<String, List<?>> parameters = Map.of(
                "testAccess.MY_SECRET_VAR.requiresGroup", List.of("admins")
        );

        final Tuple2<IMap<String, ISet<String>>, IMap<String, ISet<String>>> accessRules =
                AccessUtil.parseAccessParameters(PTN_TEST_ACCESS, parameters);
        final IMap<String, ISet<String>> accessGroupRules = accessRules._1;
        final IMap<String, ISet<String>> accessUserRules = accessRules._2;

        assertTrue("a member of the 'admins' group should be allowed access to 'MY_SECRET_VAR'",
                AccessUtil.isAllowedAccess(mockUser("bob", "admins"), accessGroupRules, accessUserRules, "MY_SECRET_VAR"));
        assertFalse("a user without membership of any granted group should be denied access to 'MY_SECRET_VAR'",
                AccessUtil.isAllowedAccess(mockUser("alice", "users"), accessGroupRules, accessUserRules, "MY_SECRET_VAR"));
    }

    private static org.exist.security.Subject mockUser(final String username, final String... groups) {
        final org.exist.security.Subject user = org.easymock.EasyMock.createMock(org.exist.security.Subject.class);
        org.easymock.EasyMock.expect(user.getUsername()).andStubReturn(username);
        org.easymock.EasyMock.expect(user.getGroups()).andStubReturn(groups);
        org.easymock.EasyMock.replay(user);
        return user;
    }
}
