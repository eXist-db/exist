/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
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
 *  $Id$
 */
package org.exist.management.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.management.Agent;
import org.exist.management.TaskStatus;
import org.exist.storage.BrokerPool;
import org.exist.util.DatabaseConfigurationException;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import java.util.*;

/**
 * Real implementation of interface {@link org.exist.management.Agent}
 * which registers MBeans with the MBeanServer.
 */
public class JMXAgent implements Agent {

    private final static Logger LOG = LogManager.getLogger(JMXAgent.class);

    private static volatile Agent agent = null;

    private final MBeanServer server;
    private final Map<String, Deque<ObjectName>> registeredMBeans = new HashMap<>();
    private final Map<ObjectName, Object> beanInstances = new HashMap<>();

    public static Agent getInstance() {
        if (agent == null) {
            agent = new JMXAgent();
        }
        return agent;
    }

    public JMXAgent() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating the JMX MBeanServer.");
        }

        final ArrayList<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
        if (servers.size() > 0)
            {server = servers.get(0);}
        else
            {server = MBeanServerFactory.createMBeanServer();}

//        try {
//            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://127.0.0.1:9999/server");
//            JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, server);
//            cs.start();
//        } catch (IOException e) {
//            LOG.warn("ERROR: failed to initialize JMX connector: " + e.getMessage(), e);
//        }
        registerSystemMBeans();
    }

    public synchronized void registerSystemMBeans() {
        try {
            ObjectName name = new ObjectName("org.exist.management:type=LockManager");
            addMBean(name, new org.exist.management.impl.LockManager());

            name = new ObjectName("org.exist.management:type=SystemInfo");
            addMBean(name, new org.exist.management.impl.SystemInfo());

        } catch (final MalformedObjectNameException | DatabaseConfigurationException e) {
            LOG.warn("Exception while registering cache mbean.", e);
        }
    }

    @Override
    public void initDBInstance(BrokerPool instance) {
        try {
            addMBean(instance.getId(), "org.exist.management." + instance.getId() + ":type=Database",
                    new org.exist.management.impl.Database(instance));
            
            addMBean(instance.getId(), "org.exist.management." + instance.getId() + ".tasks:type=SanityReport",
                    new SanityReport(instance));
            
            addMBean(instance.getId(), "org.exist.management." + instance.getId() + ":type=DiskUsage",
                    new DiskUsage(instance));

            addMBean(instance.getId(), "org.exist.management." + instance.getId() + ":type=ProcessReport",
                    new ProcessReport(instance));

            addMBean(instance.getId(), "org.exist.management." + instance.getId() + ":type=BinaryValues",
                    new BinaryValues());
                        
        } catch (final DatabaseConfigurationException e) {
            LOG.warn("Exception while registering database mbean.", e);
        }
    }

    @Override
    public synchronized void closeDBInstance(BrokerPool instance) {
        try {
            final Deque<ObjectName> stack = registeredMBeans.get(instance.getId());
            while (!stack.isEmpty()) {
                final ObjectName on = stack.pop();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("deregistering JMX MBean: " + on);
                }
                if (server.isRegistered(on)) {
                    server.unregisterMBean(on);
                }
            }
        } catch (final InstanceNotFoundException | MBeanRegistrationException e) {
            LOG.warn("Problem found while unregistering JMX", e);
        }
    }

    @Override
    public synchronized void addMBean(String dbInstance, String name, Object mbean) throws DatabaseConfigurationException {
        try {
            final ObjectName on = new ObjectName(name);
            addMBean(on, mbean);
            if (dbInstance != null) {
                Deque<ObjectName> stack = registeredMBeans.get(dbInstance);
                if (stack == null) {
                    stack = new ArrayDeque<>();
                    registeredMBeans.put(dbInstance, stack);
                }
                stack.push(on);
            }
            beanInstances.put(on, mbean);
        } catch (final MalformedObjectNameException e) {
            LOG.warn("Problem registering mbean: " + e.getMessage(), e);
            throw new DatabaseConfigurationException("Exception while registering JMX mbean: " + e.getMessage());
        }
    }

    private void addMBean(ObjectName name, Object mbean) throws DatabaseConfigurationException {
        try {
            if (!server.isRegistered(name)) {
                server.registerMBean(mbean, name);
            }

        } catch (final InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException e) {
            LOG.warn("Problem registering mbean: " + e.getMessage(), e);
            throw new DatabaseConfigurationException("Exception while registering JMX mbean: " + e.getMessage());
        }
    }

    @Override
    public synchronized void changeStatus(BrokerPool instance, TaskStatus actualStatus) {
        try {
            final ObjectName name = new ObjectName("org.exist.management." + instance.getId() + ".tasks:type=SanityReport");
            final SanityReport report = (SanityReport) beanInstances.get(name);
            if (report != null) {
                report.changeStatus(actualStatus);
            }
        } catch (final MalformedObjectNameException e) {
            LOG.warn("Problem calling mbean: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void updateStatus(BrokerPool instance, int percentage) {
        try {
            final ObjectName name = new ObjectName("org.exist.management." + instance.getId() + ".tasks:type=SanityReport");
            final SanityReport report = (SanityReport) beanInstances.get(name);
            if (report != null) {
                report.updateStatus(percentage);
            }
        } catch (final MalformedObjectNameException e) {
            LOG.warn("Problem calling mbean: " + e.getMessage(), e);
        }
    }
}
