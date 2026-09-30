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
package org.exist.xmldb;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * Shared helper for code that walks or creates a {@link Collection} path (e.g. XQuery's
 * xmldb: module, the Ant xmldb tasks, the interactive client): closing every intermediate
 * collection it creates or looks up along the way, without ever closing a collection it
 * doesn't own.
 */
public final class CollectionCloseUtil {

    private CollectionCloseUtil() {
    }

    /**
     * Closes {@code candidate} unless it is {@code borrowed} -- a collection passed in by,
     * and owned by, the caller -- or {@code null}. A close failure is reported to
     * {@code onError} rather than thrown, since this is cleanup of an already-superseded or
     * already-returned collection, not a reason to fail whatever operation produced it.
     *
     * @param borrowed  the collection the caller owns and must not be closed
     * @param candidate the collection to close if it isn't {@code borrowed}, may be {@code null}
     * @param onError   invoked with the exception if {@code candidate.close()} fails
     */
    public static void closeIfOwn(final Collection borrowed, @Nullable final Collection candidate,
            final Consumer<XMLDBException> onError) {
        if (candidate != null && candidate != borrowed) {
            try {
                candidate.close();
            } catch (final XMLDBException e) {
                onError.accept(e);
            }
        }
    }
}
