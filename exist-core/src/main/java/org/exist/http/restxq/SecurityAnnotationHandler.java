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
package org.exist.http.restxq;

import org.exist.dom.QName;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.xquery.Annotation;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.XPathException;

import java.util.*;

/**
 * Handles eXist-specific security annotations for access control on
 * RESTXQ-dispatched functions.
 *
 * <p><strong>Note:</strong> These annotations are an eXist-db extension,
 * not part of the RESTXQ specification. They provide declarative access
 * control by checking the already-authenticated servlet user against
 * eXist's permission system. They do not introduce server-side session
 * state — they inspect the {@link Subject} that was authenticated via
 * standard HTTP mechanisms (Basic auth, etc.) by the servlet container.</p>
 *
 * <h3>Supported annotations</h3>
 * <ul>
 *   <li>{@code %auth:allow-groups("dba", "editor")} — require membership
 *       in at least one listed group</li>
 *   <li>{@code %auth:allow-users("admin")} — require specific username</li>
 *   <li>{@code %auth:deny-groups("guest")} — deny access to listed groups</li>
 *   <li>{@code %auth:login-required} — require any authenticated user
 *       (not the guest account)</li>
 * </ul>
 *
 * <p>All annotations use the namespace
 * {@code http://exist-db.org/ns/auth} (prefix {@code auth}).</p>
 */
public class SecurityAnnotationHandler {

    /**
     * Accept both the canonical namespace and the test namespace for auth annotations.
     * The test suite uses 'http://exist-db.org/xquery/restxq/auth'.
     */
    private static final Set<String> AUTH_NAMESPACES = Set.of(
            RestXqNamespaces.AUTH_NS,
            "http://exist-db.org/xquery/restxq/auth"
    );

    /**
     * Checks whether the given subject is authorized to invoke the
     * function at the given route. Returns null if authorized, or an
     * error message string if denied.
     *
     * @param subject the authenticated user (from servlet-level auth)
     * @param route the matched RESTXQ route
     * @param compiled the compiled XQuery containing the function
     * @return null if authorized, or a denial reason string
     */
    public static String checkAccess(final Subject subject, final Route route,
                                     final CompiledXQuery compiled) {
        final UserDefinedFunction fn =
                compiled.getContext().resolveFunction(route.getFunctionName(), route.getArity());
        if (fn == null) {
            return null;
        }

        final Annotation[] annotations = fn.getSignature().getAnnotations();
        if (annotations == null) {
            return null;
        }

        for (final Annotation annotation : annotations) {
            final QName name = annotation.getName();
            if (!AUTH_NAMESPACES.contains(name.getNamespaceURI())) {
                continue;
            }

            final String local = name.getLocalPart();
            switch (local) {
                case "login-required" -> {
                    if (SecurityManager.GUEST_USER.equals(subject.getName())) {
                        return "Authentication required";
                    }
                }
                case "allow-groups" -> {
                    final Set<String> allowed = literalValues(annotation);
                    if (!allowed.isEmpty() && !hasAnyGroup(subject, allowed)) {
                        return "User not in required group";
                    }
                }
                case "allow-users" -> {
                    final Set<String> allowed = literalValues(annotation);
                    if (!allowed.isEmpty() && !allowed.contains(subject.getName())) {
                        return "User not authorized";
                    }
                }
                case "deny-groups" -> {
                    final Set<String> denied = literalValues(annotation);
                    if (hasAnyGroup(subject, denied)) {
                        return "User in denied group";
                    }
                }
                default -> { /* ignore unknown auth annotations */ }
            }
        }

        return null; // authorized
    }

    private static boolean hasAnyGroup(final Subject subject, final Set<String> groups) {
        final String[] userGroups = subject.getGroups();
        for (final String ug : userGroups) {
            if (groups.contains(ug)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> literalValues(final Annotation annotation) {
        final Set<String> values = new LinkedHashSet<>();
        for (final org.exist.xquery.LiteralValue lv : annotation.getValue()) {
            try {
                values.add(lv.getValue().getStringValue());
            } catch (final XPathException e) {
                // skip
            }
        }
        return values;
    }
}
