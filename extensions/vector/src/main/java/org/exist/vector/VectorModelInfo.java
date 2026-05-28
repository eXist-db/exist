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

    /**
     * Creates a model diagnostic row.
     *
     * @param id        model identifier
     * @param source    source label (registry, builtin, or registry+builtin)
     * @param path      resolved model path
     * @param dimension embedding dimension
     * @param status    availability status
     * @param message   status detail message, may be null
     * @param provider  provider type (ONNX or HTTP)
     */
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

    /**
     * Returns the model identifier.
     *
     * @return model id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the source label.
     *
     * @return source label
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the resolved model path.
     *
     * @return model path
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the embedding dimension.
     *
     * @return dimension
     */
    public int getDimension() {
        return dimension;
    }

    /**
     * Returns the availability status.
     *
     * @return status string
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returns the status detail message, if any.
     *
     * @return message or null
     */
    @Nullable
    public String getMessage() {
        return message;
    }

    /**
     * Returns the provider type.
     *
     * @return provider type (ONNX or HTTP)
     */
    public String getProvider() {
        return provider;
    }
}
