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

import java.util.*;

/**
 * Represents a RESTXQ error handler function annotated with {@code %rest:error}.
 *
 * <p>Error handlers match XQuery errors by QName with four precedence levels:</p>
 * <ol>
 *   <li>Exact QName: {@code %rest:error('err:FORG0001')}</li>
 *   <li>Namespace wildcard: {@code %rest:error('err:*')}</li>
 *   <li>Local-name wildcard: {@code %rest:error('*:FORG0001')}</li>
 *   <li>Catch-all: {@code %rest:error('*')}</li>
 * </ol>
 */
public class ErrorRoute {

    /** Precedence: exact > namespace > local > catch-all */
    public enum MatchType {
        EXACT(0),
        NAMESPACE_WILD(1),
        LOCAL_WILD(2),
        CATCH_ALL(3);

        final int priority;
        MatchType(int priority) { this.priority = priority; }
    }

    /** An error code pattern from a %rest:error annotation. */
    public static class ErrorCode {
        private final MatchType matchType;
        private final String namespaceURI;  // null for catch-all and local-wild
        private final String localName;     // null for catch-all and namespace-wild

        public ErrorCode(MatchType matchType, String namespaceURI, String localName) {
            this.matchType = matchType;
            this.namespaceURI = namespaceURI;
            this.localName = localName;
        }

        public MatchType getMatchType() { return matchType; }
        public String getNamespaceURI() { return namespaceURI; }
        public String getLocalName() { return localName; }

        public boolean matches(final QName errorQName) {
            return switch (matchType) {
                case CATCH_ALL -> true;
                case EXACT -> localName.equals(errorQName.getLocalPart())
                        && Objects.equals(namespaceURI, errorQName.getNamespaceURI());
                case NAMESPACE_WILD -> Objects.equals(namespaceURI, errorQName.getNamespaceURI());
                case LOCAL_WILD -> localName.equals(errorQName.getLocalPart());
            };
        }

        @Override
        public String toString() {
            return switch (matchType) {
                case CATCH_ALL -> "*";
                case EXACT -> (namespaceURI != null ? "Q{" + namespaceURI + "}" : "") + localName;
                case NAMESPACE_WILD -> "Q{" + namespaceURI + "}*";
                case LOCAL_WILD -> "*:" + localName;
            };
        }
    }

    private final String moduleUri;
    private final QName functionName;
    private final int arity;
    private final List<ErrorCode> errorCodes;
    private final Map<String, Route.ParamBinding> errorParams;

    public ErrorRoute(final String moduleUri, final QName functionName, final int arity,
                      final List<ErrorCode> errorCodes,
                      final Map<String, Route.ParamBinding> errorParams) {
        this.moduleUri = moduleUri;
        this.functionName = functionName;
        this.arity = arity;
        this.errorCodes = errorCodes;
        this.errorParams = errorParams;
    }

    public String getModuleUri() { return moduleUri; }
    public QName getFunctionName() { return functionName; }
    public int getArity() { return arity; }
    public List<ErrorCode> getErrorCodes() { return errorCodes; }
    public Map<String, Route.ParamBinding> getErrorParams() { return errorParams; }

    /**
     * Returns the best matching error code for the given QName, or null.
     */
    public ErrorCode bestMatch(final QName errorQName) {
        ErrorCode best = null;
        for (final ErrorCode code : errorCodes) {
            if (code.matches(errorQName)) {
                if (best == null || code.matchType.priority < best.matchType.priority) {
                    best = code;
                }
            }
        }
        return best;
    }
}
