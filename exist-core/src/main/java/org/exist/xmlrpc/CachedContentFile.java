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
package org.exist.xmlrpc;

import org.exist.util.io.ContentFile;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public final class CachedContentFile extends AbstractCachedResult {
    private final ContentFile result;

    public CachedContentFile(final ContentFile result) {
        super(0);
        this.result = result;
    }

    @Override
    public ContentFile getResult() {
        return result;
    }

    @Override
    protected void doClose() {
        if (result != null) {
            result.close();
        }
    }
}
