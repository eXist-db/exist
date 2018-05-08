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
 * $Id$
 */
package org.exist.management;

import org.exist.management.impl.PerInstanceMBean;
import org.exist.storage.BrokerPool;
import org.exist.util.DatabaseConfigurationException;

/**
 * Interface to allow the JMX classes to be plugged in on systems which support
 * it. A dummy implementation will be used if JMX is not available.
 */
public interface Agent {

    void initDBInstance(BrokerPool instance);

    void closeDBInstance(BrokerPool instance);

    void addMBean(PerInstanceMBean mbean) throws DatabaseConfigurationException;

    void changeStatus(BrokerPool instance, TaskStatus actualStatus);

    void updateStatus(BrokerPool instance, int percentage);
}
