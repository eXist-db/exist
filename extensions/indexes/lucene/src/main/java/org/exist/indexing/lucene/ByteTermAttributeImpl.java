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
package org.exist.indexing.lucene;

import org.apache.lucene.util.AttributeImpl;

public abstract class ByteTermAttributeImpl extends AttributeImpl {
    /**
     * @deprecated fillBytesRef() is no longer part of the Lucene 10 API.
     * It was previously part of the {@link org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute}
     * but has been removed.
     *
     * TODO: This method is currently kept as a dummy to avoid AbstractMethodError
     * during transition but should be removed once all callers are updated to the Lucene 10 API.
     */
    @Deprecated
    public void fillBytesRef() {
        // No-op compatibility shim retained during Lucene 10 migration.
    }
}
