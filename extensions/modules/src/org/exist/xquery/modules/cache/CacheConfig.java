/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.modules.cache;

import java.util.Optional;

/**
 * Holds the configuration for a Cache
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CacheConfig {

    private final Optional<Permissions> permissions;

    public CacheConfig(final Optional<Permissions> permissions) {
        this.permissions = permissions;
    }

    public Optional<Permissions> getPermissions() {
        return permissions;
    }

    public static class Permissions {
        private final Optional<String> putGroup;
        private final Optional<String> getGroup;
        private final Optional<String> removeGroup;
        private final Optional<String> clearGroup;


        public Permissions(final Optional<String> putGroup, final Optional<String> getGroup,
                final Optional<String> removeGroup, final Optional<String> clearGroup) {
            this.putGroup = putGroup;
            this.getGroup = getGroup;
            this.removeGroup = removeGroup;
            this.clearGroup = clearGroup;
        }

        public Optional<String> getPutGroup() {
            return putGroup;
        }

        public Optional<String> getGetGroup() {
            return getGroup;
        }

        public Optional<String> getRemoveGroup() {
            return removeGroup;
        }

        public Optional<String> getClearGroup() {
            return clearGroup;
        }
    }
}
