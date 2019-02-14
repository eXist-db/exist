/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id: Database.java 6177 2007-07-08 14:42:37Z wolfgang_m $
 */
package org.exist.management.impl;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;

public class Database implements DatabaseMXBean {

    private final BrokerPool pool;

    public Database(final BrokerPool pool) {
        this.pool = pool;
    }

    public static String getAllInstancesQuery() {
        return getName("*");
    }

    private static String getName(final String instanceId) {
        return "org.exist.management." + instanceId + ":type=Database";
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return new ObjectName(getName(pool.getId()));
    }

    @Override
    public String getInstanceId() {
        return pool.getId();
    }

    @Override
    public String getStatus() {
        return pool.getStatus();
    }

    @Override
    public void shutdown() {
        pool.shutdown();
    }

    @Override
    public int getMaxBrokers() {
        return pool.getMax();
    }

    @Override
    public int getAvailableBrokers() {
        return pool.available();
    }

    @Override
    public int getActiveBrokers() {
        return pool.countActiveBrokers();
    }

    @Override
    public int getTotalBrokers() {
        return pool.total();
    }

    @Override
    public List<ActiveBroker> getActiveBrokersMap() {
        final List<ActiveBroker> brokersList = new ArrayList<>();

        for (final Map.Entry<Thread, DBBroker> entry : pool.getActiveBrokers().entrySet()) {
            final Thread thread = entry.getKey();
            final DBBroker broker = entry.getValue();
            final String trace = printStackTrace(thread);
            final String watchdogTrace = pool.getWatchdog().map(wd -> wd.get(broker)).orElse(null);
            brokersList.add(new ActiveBroker(thread.getName(), broker.getReferenceCount(), trace, watchdogTrace));
        }
        return brokersList;
    }

    @Override
    public long getReservedMem() {
        return pool.getReservedMem();
    }

    @Override
    public long getCacheMem() {
        return pool.getCacheManager().getTotalMem();
    }

    @Override
    public long getCollectionCacheMem() {
        return pool.getCollectionsCache().getMaxCacheSize();
    }

    @Override
    public long getUptime() {
        return System.currentTimeMillis() - pool.getStartupTime().getTimeInMillis();
    }

    @Override
    public String getExistHome() {
        return pool.getConfiguration().getExistHome().map(p -> p.toAbsolutePath().toString()).orElse(null);
    }

    public String printStackTrace(final Thread thread) {
        final StackTraceElement[] stackElements = thread.getStackTrace();
        final StringWriter writer = new StringWriter();
        final int showItems = stackElements.length > 20 ? 20 : stackElements.length;
        for (int i = 0; i < showItems; i++) {
            writer.append(stackElements[i].toString()).append('\n');
        }
        return writer.toString();
    }
}
