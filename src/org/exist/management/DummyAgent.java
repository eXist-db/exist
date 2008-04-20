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

import org.exist.storage.BrokerPool;
import org.exist.util.DatabaseConfigurationException;

import java.util.List;

/**
 * A dummy agent which will be used if JMX is disabled. It just acts as an empty
 * placeholder.
 */
public class DummyAgent implements Agent {

    public void initDBInstance(BrokerPool instance) {
        // do nothing
    }

    public void closeDBInstance(BrokerPool instance) {
        // nothing to do
    }
    
    public void addMBean(String dbInstance, String name, Object mbean) throws DatabaseConfigurationException {
        // just do nothing
    }

    public void updateErrors(BrokerPool pool, List errorList, long startTime) {
        // nothing to do
    }
}
