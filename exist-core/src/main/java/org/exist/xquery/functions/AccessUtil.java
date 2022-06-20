/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
import org.exist.security.Subject;
import org.exist.security.internal.SecurityManagerImpl;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class AccessUtil {

    static final String OTHERWISE = "*";

    /**
     * Parse access parameters into Group Access Rules and User Access Rules.
     *
     * @param accessRulePattern A pattern whose first group matches a "name",
     *                          and whose second pattern matches the String "Group" or "User".
     * @param parameters the module parameters.
     *
     * @return a Tuple where the first entry is the Group Access Rules, and the second entry is the User Access Rules.
     */
    public static Tuple2<Map<String, Set<String>>, Map<String, Set<String>>> parseAccessParameters(
            final Pattern accessRulePattern, final Map<String, List<? extends Object>> parameters) {
        Map<String, Set<String>> accessGroupRules = null;
        Map<String, Set<String>> accessUserRules = null;

        Matcher matcher = null;
        for (final Map.Entry<String, List<? extends Object>> parameter : parameters.entrySet()) {
            final String parameterName = parameter.getKey();
            if (matcher == null) {
                matcher = accessRulePattern.matcher(parameterName);
            } else {
                matcher.reset(parameterName);
            }

            if (matcher.matches()) {
                final String principalType = matcher.group(2);
                if ("Group".equals(principalType)) {
                    if (accessGroupRules == null) {
                        accessGroupRules = new HashMap<>();
                    }
                    final String name = matcher.group(1);
                    accessGroupRules.put(name, toSet(parameter.getValue()));
                } else if ("User".equals(principalType)) {
                    if (accessUserRules == null) {
                        accessUserRules = new HashMap<>();
                    }
                    final String name = matcher.group(1);
                    accessUserRules.put(name, toSet(parameter.getValue()));
                }
            }
        }

        if ((accessGroupRules == null || !accessGroupRules.containsKey(OTHERWISE))
                && ((accessUserRules == null) || !accessUserRules.containsKey(OTHERWISE))) {
            if (accessGroupRules == null) {
                accessGroupRules = new HashMap<>(1);
                final Set<String> otherwiseDba = new HashSet<>(1);
                otherwiseDba.add(SecurityManagerImpl.DBA_GROUP);
                accessGroupRules.put(OTHERWISE, otherwiseDba);
            }
        }

        if (accessGroupRules == null) {
            accessGroupRules = Collections.emptyMap();
        } else {
            accessGroupRules = Collections.unmodifiableMap(accessGroupRules);
        }

        if (accessUserRules == null) {
            accessUserRules = Collections.emptyMap();
        } else {
            accessUserRules = Collections.unmodifiableMap(accessUserRules);
        }

        return Tuple(accessGroupRules, accessUserRules);
    }

    private static Set<String> toSet(final List<? extends Object> values) {
        final Set<String> set;
        if (values.size() > 0) {
            set = new HashSet<>(values.size());
            for (final Object value : values) {
                set.add(value.toString());
            }
        } else {
            set = Collections.emptySet();
        }
        return set;
    }

    /**
     * Returns true of the user is allowed access to the name.
     *
     * @param user the user to test the access rules against.
     * @param accessGroupRules the group access rules.
     * @param accessUserRules the user access rules.
     * @param name the name of the resource that the user wishes to access.
     */
    public static boolean isAllowedAccess(final Subject user, final Map<String, Set<String>> accessGroupRules,
            final Map<String, Set<String>> accessUserRules, final String name) {
        return hasGroupAccess(user, accessGroupRules, name)
                || hasUserAccess(user, accessUserRules, name);
    }

    private static boolean hasGroupAccess(final Subject user, final Map<String, Set<String>> accessGroupRules,
            final String name) {
        Set<String> accessGroups = accessGroupRules.get(name);

        // fallback to "otherwise"
        if (accessGroups == null) {
            accessGroups = accessGroupRules.get(OTHERWISE);
        }

        if (accessGroups != null) {
            final String[] userGroups = user.getGroups();
            for (final String userGroup : userGroups) {
                if (accessGroups.contains(userGroup)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasUserAccess(final Subject user, final Map<String, Set<String>> accessUserRules, final String name) {
        Set<String> accessUsers = accessUserRules.get(name);

        // fallback to "otherwise"
        if (accessUsers == null) {
            accessUsers = accessUserRules.get(OTHERWISE);
        }

        if (accessUsers != null) {
            if (accessUsers.contains(user.getUsername())) {
                return true;
            }
        }
        return false;
    }
}
