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

package org.exist.management.impl;

import org.exist.storage.BrokerPool;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * JMX MXBean for examining the CollectionCache
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CollectionCache implements CollectionCacheMXBean {

    private final BrokerPool instance;

    public CollectionCache(final BrokerPool instance) {
        this.instance = instance;
    }

    public static String getAllInstancesQuery() {
        return getName("*");
    }

    private static String getName(final String instanceId) {
        return "org.exist.management." + instanceId + ":type=CollectionCache";
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return new ObjectName(getName(instance.getId()));
    }

    @Override
    public String getInstanceId() {
        return instance.getId();
    }

    @Override
    public org.exist.collections.CollectionCache.Statistics getStatistics() {
        return instance.getCollectionsCache().getStatistics();
    }
}
