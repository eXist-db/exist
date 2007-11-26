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

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;

import javax.management.openmbean.*;
import java.util.Iterator;
import java.util.Map;

public class Database implements DatabaseMBean {

    private static String[] itemNames = { "owner", "referenceCount" };
    private static String[] itemDescriptions = {
            "Name of the thread owning the broker",
            "Number of references held by the thread"
    };
    private static String[] indexNames = { "owner" };

    private BrokerPool pool;

    public Database(BrokerPool pool) {
        this.pool = pool;
    }

    public String getInstanceId() {
        return pool.getId();
    }

    public int getMaxBrokers() {
        return pool.getMax();
    }

    public int getAvailableBrokers() {
        return pool.available();
    }

    public int getActiveBrokers() {
        return pool.active();
    }

    public TabularData getActiveBrokersMap() {
        OpenType[] itemTypes = { SimpleType.STRING, SimpleType.INTEGER };
        try {
            CompositeType infoType = new CompositeType("brokerInfo", "Provides information on a broker instance.",
                    itemNames, itemDescriptions, itemTypes);
            TabularType tabularType = new TabularType("activeBrokers", "Lists all threads currently using a broker instance", infoType, indexNames);
            TabularDataSupport data = new TabularDataSupport(tabularType);
            for (Iterator i = pool.getActiveBrokers().entrySet().iterator(); i.hasNext(); ) {
                Map.Entry entry = (Map.Entry) i.next();
                Thread thread = (Thread) entry.getKey();
                DBBroker broker = (DBBroker) entry.getValue();
                Object[] itemValues = { thread.getName(), broker.getReferenceCount() };
                data.put(new CompositeDataSupport(infoType, itemNames, itemValues));
            }
            return data;
        } catch (OpenDataException e) {
            e.printStackTrace();
            return null;
        }
    }

    public long getReservedMem() {
        return pool.getReservedMem();
    }

    public long getCacheMem() {
        return pool.getCacheManager().getTotalMem();
    }

    public long getCollectionCacheMem() {
        return pool.getCollectionCacheMgr().getMaxTotal();
    }
}
