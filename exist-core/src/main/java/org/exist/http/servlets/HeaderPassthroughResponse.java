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
package org.exist.http.servlets;

/**
 * Capability of a response wrapper that buffers its output (and so cannot let a header take
 * effect on the real, underlying response immediately) to still accept a header that must
 * bypass that buffering and reach the real response right away, regardless of whether this
 * particular wrapper instance ever gets flushed.
 * <p>
 * {@code org.exist.http.urlrewrite.XQueryURLRewrite$CachingResponseWrapper} is the sole
 * implementer: it buffers everything a dispatched pipeline step writes -- including headers a
 * step sets itself, whether via the underlying servlet's own resource handling or the XQuery
 * {@code response:set-header()} function -- because that step's output may be discarded in favor
 * of a later {@code <exist:view>} step's, in which case its buffered writes are correctly never
 * replayed. A {@code controller.xql} {@code <exist:set-header>} directive is different: it is
 * pipeline-level configuration, not step output, and must survive regardless of which step's
 * output eventually wins -- so it needs a way to skip buffering entirely, tunnelling through
 * however many wrapper layers are stacked (a forward step may itself trigger a nested dispatch,
 * wrapping one {@code CachingResponseWrapper} around another) until it reaches a response that
 * isn't one of these wrappers at all, and applying itself there, immediately.
 *
 * @see org.exist.http.urlrewrite.URLRewrite#setHeaders
 */
public interface HeaderPassthroughResponse {

    /**
     * Set a header on the real, underlying response immediately, bypassing any output buffering
     * this response wrapper (or any wrapper it in turn wraps) would otherwise apply.
     *
     * @param name the header name.
     * @param value the header value.
     */
    void setPassthroughHeader(String name, String value);
}
