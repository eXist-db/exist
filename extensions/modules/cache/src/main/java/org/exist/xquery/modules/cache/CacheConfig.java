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
package org.exist.xquery.modules.cache;

import java.util.Optional;

/**
 * Holds the configuration for a Cache
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public record CacheConfig(Optional<Permissions> permissions, Optional<Long> maximumSize,
                          Optional<Long> expireAfterAccess, Optional<Long> expireAfterWrite) {

    /**
     * @param permissions       Any restrictions on cache operations
     * @param maximumSize       The maximimum number of entries in the cache
     * @param expireAfterAccess The time in milliseconds after the entry is last accessed, that it should expire
     * @param expireAfterWrite  The time in milliseconds after the entry is last modified, that it should expire
     */
    public CacheConfig {
    }

    public record Permissions(Optional<String> putGroup, Optional<String> getGroup, Optional<String> removeGroup,
                              Optional<String> clearGroup) {
    }
}
