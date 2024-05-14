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
package org.exist.collections.triggers;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import java.lang.management.ManagementFactory;
import java.util.StringJoiner;

final class XQueryTriggerMBeanImpl extends StandardMBean implements XQueryTriggerMBean {

    private XQueryTriggerMBeanImpl() throws NotCompliantMBeanException {
        super(XQueryTriggerMBean.class);
    }

    static void init() {
        try {
            final ObjectName name = ObjectName.getInstance("org.exist.management.exist", "type", "TriggerStates");
            final MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            if (!platformMBeanServer.isRegistered(name)) {
                platformMBeanServer.registerMBean(new XQueryTriggerMBeanImpl(), name);
            }
        } catch (final MalformedObjectNameException | InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public int getKeys() {
        return TriggerStatePerThread.keys();
    }

    @Override
    public void clear() {
        TriggerStatePerThread.clearAll();
    }

    @Override
    public String dumpTriggerStates() {
        StringJoiner joiner = new StringJoiner("\n");
        TriggerStatePerThread.forEach((k, v) -> joiner.add("%s: %s".formatted(k, v.size())));
        return joiner.toString();
    }

    @Override
    public String listKeys() {
        StringJoiner joiner = new StringJoiner("\n");
        TriggerStatePerThread.forEach((k, v) -> joiner.add("%s".formatted(k)));
        return joiner.toString();
    }
}
