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
package org.exist.vector;

import javax.annotation.Nullable;

/**
 * Diagnostic row for a configured or built-in vector embedding model.
 */
public class VectorModelInfo {

    private final String id;
    private final String source;
    private final String path;
    private final int dimension;
    private final String status;
    @Nullable
    private final String message;
    private final String provider;

    public VectorModelInfo(final String id, final String source, final String path, final int dimension,
                           final String status, @Nullable final String message, final String provider) {
        this.id = id;
        this.source = source;
        this.path = path;
        this.dimension = dimension;
        this.status = status;
        this.message = message;
        this.provider = provider;
    }

    public String getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getPath() {
        return path;
    }

    public int getDimension() {
        return dimension;
    }

    public String getStatus() {
        return status;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public String getProvider() {
        return provider;
    }
}
