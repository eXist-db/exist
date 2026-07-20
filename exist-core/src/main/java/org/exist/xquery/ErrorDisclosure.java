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
package org.exist.xquery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.Permission;
import org.exist.security.Subject;
import org.exist.source.DBSource;
import org.exist.source.Source;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * How much of a failed query execution may be disclosed to the caller.
 *
 * A caller which may EXECUTE a stored query but not READ it must not learn anything about the
 * source: not the query text, nor the message, error code, line/column or cause of a failure —
 * exactly as {@code execve} of a {@code --x} binary reports a generic errno rather than the
 * contents of the file. The full error is logged server-side with a correlation id so that the
 * owner or a DBA can still diagnose it.
 *
 * The disclosure level is recomputed from the current subject on every execution
 * (see {@link XQuery#execute}) and is never cached with the compiled query, so the same pooled
 * query yields full errors to a read-capable caller and generic errors to a read-blind one.
 *
 * @author <a href="mailto:info@exist-db.org">The eXist-db Authors</a>
 */
public enum ErrorDisclosure {

    /**
     * The caller may read the source, so it may see the error in full.
     */
    FULL,

    /**
     * The caller may execute but not read the source, so it learns only that the execution failed.
     */
    GENERIC;

    private static final Logger LOG = LogManager.getLogger(ErrorDisclosure.class);

    /**
     * The level which applies to a subject executing a query from the given source.
     *
     * A stored query which the subject cannot read is {@link #GENERIC}; everything else — a query
     * typed in by the caller, a query from the file system or the classpath, a stored query the
     * subject can read — is {@link #FULL}, as its source is not confidential from that caller.
     *
     * Entry points must apply this to the context BEFORE compiling: a compile error never reaches
     * {@link XQuery#execute}, which can only recompute the level for a runtime failure.
     *
     * @param source the source of the query, or null if it is unknown
     * @param subject the subject executing the query, or null if it is unknown
     *
     * @return the level which may be disclosed to that subject
     */
    public static ErrorDisclosure of(@Nullable final Source source, @Nullable final Subject subject) {
        if (!(source instanceof DBSource dbSource)) {
            // not a stored query, so its source is not confidential from the caller
            return FULL;
        }
        // fail closed: a stored query whose reader we cannot determine is treated as unreadable
        return subject != null && dbSource.getPermissions().validate(subject, Permission.READ) ? FULL : GENERIC;
    }

    /**
     * Filter an error according to the disclosure level of the context it was raised in.
     *
     * For {@link #FULL} the original error is returned unchanged. For {@link #GENERIC} the original
     * is logged at WARN with a correlation id, the resource, and the real and effective subjects,
     * and a sanitized error is returned which carries nothing but {@link ErrorCodes#EXXQDY0010}
     * and that same correlation id.
     *
     * @param context the context the query executed in, or null if it is unknown
     * @param original the error raised by the query
     *
     * @return the error which may be disclosed to the caller
     */
    public static XPathException disclose(@Nullable final XQueryContext context, final XPathException original) {
        if (context == null || context.getErrorDisclosure() == FULL) {
            return original;
        }
        return sanitize(context, original);
    }

    /**
     * Filter an arbitrary failure of an authorized query, for the transports which catch more than
     * {@link XPathException}.
     *
     * A query which was allowed to run can fail in ways that are not an {@link XPathException} — a
     * serialization {@code BadRequestException} or {@code SAXException}, a runtime
     * {@link org.exist.security.PermissionDeniedException}, a {@link RuntimeException} — and every one
     * of those carries source-derived detail that must not reach a read-blind caller. The transport
     * must branch on the disclosure level (this method), never on the Java type of the failure.
     *
     * @param context the context the query executed in, or null if it is unknown
     * @param original the failure raised by the query
     *
     * @return the sanitized generic error to throw instead when {@link #GENERIC}, or {@code null} when
     *     {@link #FULL}, which signals the caller to rethrow {@code original} unchanged so its own type
     *     and HTTP status are preserved
     */
    public static @Nullable XPathException discloseGeneric(@Nullable final XQueryContext context, final Throwable original) {
        if (context == null || context.getErrorDisclosure() == FULL) {
            return null;
        }
        return sanitize(context, original);
    }

    private static XPathException sanitize(final XQueryContext context, final Throwable original) {
        final String correlationId = UUID.randomUUID().toString();
        LOG.warn("Read-blind query execution failed [{}] resource={} realUser={} effectiveUser={}",
                correlationId, sourceOf(context), realUserOf(context), effectiveUserOf(context), original);

        return new XPathException((Expression) null, ErrorCodes.EXXQDY0010,
                "Query execution failed (ref " + correlationId + ").");
    }

    private static String sourceOf(final XQueryContext context) {
        final Source source = context.getSource();
        return source == null ? "unknown" : source.path();
    }

    private static String realUserOf(final XQueryContext context) {
        return nameOf(context.getRealUser());
    }

    private static String effectiveUserOf(final XQueryContext context) {
        // the effective user is read through the broker, which may already be gone when the error is filtered
        return context.getBroker() == null ? "unknown" : nameOf(context.getEffectiveUser());
    }

    private static String nameOf(@Nullable final Subject subject) {
        return subject == null ? "unknown" : subject.getName();
    }
}
