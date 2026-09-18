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
import io.lacuna.bifurcan.IMap;
import io.lacuna.bifurcan.ISet;
import io.lacuna.bifurcan.LinearSet;
import io.lacuna.bifurcan.LinearMap;
import io.lacuna.bifurcan.Map;
import io.lacuna.bifurcan.Set;
import org.exist.security.Subject;
import org.exist.security.internal.SecurityManagerImpl;

import java.util.List;
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
    public static Tuple2<IMap<String, ISet<String>>, IMap<String, ISet<String>>> parseAccessParameters(
            final Pattern accessRulePattern, final java.util.Map<String, List<?>> parameters) {
        final Tuple2<IMap<String, ISet<String>>, IMap<String, ISet<String>>> rawRules =
                collectAccessRules(accessRulePattern, parameters);
        final IMap<String, ISet<String>> accessUserRules = rawRules._2;
        final IMap<String, ISet<String>> accessGroupRules = applyOtherwiseDefault(rawRules._1, accessUserRules);

        return Tuple(
                accessGroupRules == null ? Map.empty() : accessGroupRules.forked(),
                accessUserRules == null ? Map.empty() : accessUserRules.forked());
    }

    /**
     * Scans the module parameters for entries matching {@code accessRulePattern}, splitting
     * them into raw (unforked, possibly {@code null}) Group and User access rule maps.
     *
     * @param accessRulePattern A pattern whose first group matches a "name",
     *                          and whose second pattern matches the String "Group" or "User".
     * @param parameters the module parameters.
     *
     * @return a Tuple where the first entry is the raw Group Access Rules, and the second entry is the raw User Access Rules.
     */
    private static Tuple2<IMap<String, ISet<String>>, IMap<String, ISet<String>>> collectAccessRules(
            final Pattern accessRulePattern, final java.util.Map<String, List<?>> parameters) {
        IMap<String, ISet<String>> accessGroupRules = null;
        IMap<String, ISet<String>> accessUserRules = null;

        if (parameters == null) {
            return Tuple(null, null);
        }

        Matcher matcher = null;
        for (final java.util.Map.Entry<String, List<?>> parameter : parameters.entrySet()) {
            final String parameterName = parameter.getKey();
            if (matcher == null) {
                matcher = accessRulePattern.matcher(parameterName);
            } else {
                matcher.reset(parameterName);
            }

            if (!matcher.matches()) {
                continue;
            }

            final String principalType = matcher.group(2);
            final String name = matcher.group(1);
            if ("Group".equals(principalType)) {
                if (accessGroupRules == null) {
                    accessGroupRules = new LinearMap<>();
                }
                accessGroupRules.put(name, toSet(parameter.getValue()));
            } else if ("User".equals(principalType)) {
                if (accessUserRules == null) {
                    accessUserRules = new LinearMap<>();
                }
                accessUserRules.put(name, toSet(parameter.getValue()));
            }
        }

        return Tuple(accessGroupRules, accessUserRules);
    }

    /**
     * If neither the group nor the user rules define an explicit "otherwise" ({@code "*"})
     * rule, injects a default "otherwise" -&gt; DBA-group rule, so that any name not otherwise
     * configured remains accessible to DBAs, per the documented behaviour in conf.xml.
     *
     * @param accessGroupRules the raw (unforked, possibly {@code null}) Group Access Rules.
     * @param accessUserRules the raw (unforked, possibly {@code null}) User Access Rules.
     *
     * @return the Group Access Rules, with the default "otherwise" rule added if needed.
     */
    private static IMap<String, ISet<String>> applyOtherwiseDefault(
            final IMap<String, ISet<String>> accessGroupRules, final IMap<String, ISet<String>> accessUserRules) {
        final boolean groupOtherwiseSet = accessGroupRules != null && accessGroupRules.contains(OTHERWISE);
        final boolean userOtherwiseSet = accessUserRules != null && accessUserRules.contains(OTHERWISE);

        if (groupOtherwiseSet || userOtherwiseSet) {
            return accessGroupRules;
        }

        final IMap<String, ISet<String>> groupRulesWithDefault = accessGroupRules == null ? new LinearMap<>(1) : accessGroupRules;
        ISet<String> otherwiseDba = new LinearSet<>(1);
        otherwiseDba.add(SecurityManagerImpl.DBA_GROUP);
        otherwiseDba = otherwiseDba.forked();
        groupRulesWithDefault.put(OTHERWISE, otherwiseDba);

        return groupRulesWithDefault;
    }

    private static ISet<String> toSet(final List<?> values) {
        ISet<String> set;
        if (values.size() > 0) {
            set = new LinearSet<>();
            for (final Object value : values) {
                set.add(value.toString());
            }
            set = set.forked();
        } else {
            set = Set.empty();
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
    public static boolean isAllowedAccess(final Subject user, final IMap<String, ISet<String>> accessGroupRules,
                                          final IMap<String, ISet<String>> accessUserRules, final String name) {
        return hasGroupAccess(user, accessGroupRules, name)
                || hasUserAccess(user, accessUserRules, name);
    }

    private static boolean hasGroupAccess(final Subject user, final IMap<String, ISet<String>> accessGroupRules,
            final String name) {
        ISet<String> accessGroups = accessGroupRules.get(name, null);

        // fallback to "otherwise"
        if (accessGroups == null) {
            accessGroups = accessGroupRules.get(OTHERWISE, null);
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

    private static boolean hasUserAccess(final Subject user, final IMap<String, ISet<String>> accessUserRules,
            final String name) {
        ISet<String> accessUsers = accessUserRules.get(name, null);

        // fallback to "otherwise"
        if (accessUsers == null) {
            accessUsers = accessUserRules.get(OTHERWISE, null);
        }

        if (accessUsers != null) {
            return accessUsers.contains(user.getUsername());
        }

        return false;
    }
}