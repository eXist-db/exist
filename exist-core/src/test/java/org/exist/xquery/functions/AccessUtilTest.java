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
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
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

        final Tuple2<Map<String, Set<String>>, Map<String, Set<String>>> accessRules =
                AccessUtil.parseAccessParameters(PTN_TEST_ACCESS, parameters);
        final Map<String, Set<String>> accessGroupRules = accessRules._1;

        assertTrue("access rule should be keyed by the full parameter name",
                accessGroupRules.containsKey("MY_SECRET_VAR"));
        assertFalse("access rule must not be keyed by just the last character of the parameter name",
                accessGroupRules.containsKey("R"));
    }

    @Test
    public void isAllowedAccessHonoursMultiCharacterNamedGroupRule() {
        final Map<String, List<?>> parameters = Map.of(
                "testAccess.MY_SECRET_VAR.requiresGroup", List.of("admins")
        );

        final Tuple2<Map<String, Set<String>>, Map<String, Set<String>>> accessRules =
                AccessUtil.parseAccessParameters(PTN_TEST_ACCESS, parameters);
        final Map<String, Set<String>> accessGroupRules = accessRules._1;
        final Map<String, Set<String>> accessUserRules = accessRules._2;

        assertTrue("a member of the 'admins' group should be allowed access to 'MY_SECRET_VAR'",
                AccessUtil.isAllowedAccess(mockUser("bob", "admins"), accessGroupRules, accessUserRules, "MY_SECRET_VAR"));
        assertFalse("a user without membership of any granted group should be denied access to 'MY_SECRET_VAR'",
                AccessUtil.isAllowedAccess(mockUser("alice", "users"), accessGroupRules, accessUserRules, "MY_SECRET_VAR"));
    }

    /**
     * Regression test for a default-fallback bug: the "otherwise" (DBA-group) default was only
     * ever applied when literally no group rule at all had been configured. As soon as an admin
     * configured even one specific named group rule without also adding an explicit "*" rule,
     * every other, unlisted name became inaccessible to everyone, including DBAs - contradicting
     * the documented behaviour ("if '*' is not set, it defaults to the 'DBA' group").
     */
    @Test
    public void parseAccessParametersDefaultsUnlistedNamesToDbaGroupWhenWildcardAbsent() {
        final Map<String, List<?>> parameters = Map.of(
                "testAccess.MY_SECRET_VAR.requiresGroup", List.of("admins")
        );

        final Tuple2<Map<String, Set<String>>, Map<String, Set<String>>> accessRules =
                AccessUtil.parseAccessParameters(PTN_TEST_ACCESS, parameters);
        final Map<String, Set<String>> accessGroupRules = accessRules._1;
        final Map<String, Set<String>> accessUserRules = accessRules._2;

        assertTrue("an unlisted name should still fall back to the '*' (otherwise) rule",
                accessGroupRules.containsKey(AccessUtil.OTHERWISE));
        assertTrue("a DBA should be allowed access to an unlisted name by default",
                AccessUtil.isAllowedAccess(mockUser("dba-user", org.exist.security.SecurityManager.DBA_GROUP), accessGroupRules, accessUserRules, "SOME_OTHER_VAR"));
        assertFalse("a non-DBA should be denied access to an unlisted name by default",
                AccessUtil.isAllowedAccess(mockUser("alice", "users"), accessGroupRules, accessUserRules, "SOME_OTHER_VAR"));
    }

    private static org.exist.security.Subject mockUser(final String username, final String... groups) {
        final org.exist.security.Subject user = org.easymock.EasyMock.createMock(org.exist.security.Subject.class);
        org.easymock.EasyMock.expect(user.getUsername()).andStubReturn(username);
        org.easymock.EasyMock.expect(user.getGroups()).andStubReturn(groups);
        org.easymock.EasyMock.replay(user);
        return user;
    }
}
