/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2017 The eXist Project
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
 */
package org.exist.management.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.lock.LockTable.LockCountTraces;
import org.exist.storage.lock.LockTable.LockModeOwner;
import org.exist.storage.lock.LockTableUtils;
import org.exist.util.io.FastByteArrayOutputStream;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * JMX MXBean for examining the LockTable
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class LockTable implements LockTableMXBean {

    private final BrokerPool pool;

    public LockTable(final BrokerPool brokerPool) {
        this.pool = brokerPool;
    }

    public static String getAllInstancesQuery() {
        return getName("*");
    }

    private static String getName(final String instanceId) {
        return "org.exist.management." + instanceId + ":type=LockTable";
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
    public Map<String, Map<LockType, Map<Lock.LockMode, Map<String, LockCountTraces>>>> getAcquired() {
        return pool.getLockManager().getLockTable().getAcquired();
    }

    @Override
    public Map<String, Map<LockType, List<LockModeOwner>>> getAttempting() {
        return pool.getLockManager().getLockTable().getAttempting();
    }

    @Override
    public void dumpToConsole() {
        System.out.println(LockTableUtils.stateToString(pool.getLockManager().getLockTable(), false));
    }

    @Override
    public void fullDumpToConsole() {
        System.out.println(LockTableUtils.stateToString(pool.getLockManager().getLockTable(), true));
    }

    @Override
    public void xmlDumpToConsole() {
        try {
            LockTableUtils.stateToXml(pool.getLockManager().getLockTable(), false, new OutputStreamWriter(System.out));
        } catch (final XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void xmlFullDumpToConsole() {
        try {
            LockTableUtils.stateToXml(pool.getLockManager().getLockTable(), true, new OutputStreamWriter(System.out));
        } catch (final XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private final static Logger LOCK_LOG = LogManager.getLogger(org.exist.storage.lock.LockTable.class);

    @Override
    public void dumpToLog() {
        LOCK_LOG.info(LockTableUtils.stateToString(pool.getLockManager().getLockTable(), false));
    }

    @Override
    public void fullDumpToLog() {
        LOCK_LOG.info(LockTableUtils.stateToString(pool.getLockManager().getLockTable(), true));
    }

    @Override
    public void xmlDumpToLog() {
        try (final FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
                final Writer writer = new OutputStreamWriter(bos)) {
            LockTableUtils.stateToXml(pool.getLockManager().getLockTable(), false, writer);
            LOCK_LOG.info(new String(bos.toByteArray(), UTF_8));
        } catch (final IOException | XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void xmlFullDumpToLog() {
        try (final FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
             final Writer writer = new OutputStreamWriter(bos)) {
            LockTableUtils.stateToXml(pool.getLockManager().getLockTable(), true, writer);
            LOCK_LOG.info(new String(bos.toByteArray(), UTF_8));
        } catch (final IOException | XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }
}
