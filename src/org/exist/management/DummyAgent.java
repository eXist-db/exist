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
 * A dummy agent which will be used if JMX is disabled. It just acts as an empty
 * placeholder.
 */
public class DummyAgent implements Agent {

    @Override
    public void initDBInstance(final BrokerPool instance) {
        // do nothing
    }

    @Override
    public void closeDBInstance(final BrokerPool instance) {
        // nothing to do
    }

    @Override
    public void addMBean(final PerInstanceMBean mbean) throws DatabaseConfigurationException {
        // just do nothing
    }

    @Override
    public void changeStatus(final BrokerPool instance, final TaskStatus actualStatus) {
        // nothing to do
    }

    @Override
    public void updateStatus(final BrokerPool instance, final int percentage) {
        // nothing to do
    }
}
