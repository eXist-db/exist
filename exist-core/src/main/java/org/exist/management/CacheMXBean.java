/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2016 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.management;

import org.exist.management.impl.PerInstanceMBean;
import org.exist.storage.cache.Cache;

/**
 * Provides access to some properties of the internal page caches
 * ({@link org.exist.storage.cache.Cache}).
 */
public interface CacheMXBean extends PerInstanceMBean {

    Cache.CacheType getType();

    int getSize();

    int getUsed();

    int getHits();

    int getFails();

    String getCacheName();
}