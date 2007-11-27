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

import org.apache.log4j.Logger;

public class AgentFactory {

    private final static Logger LOG = Logger.getLogger(AgentFactory.class);

    private static Agent instance = null;

    public static Agent getInstance() {
        if (instance == null) {
            String className = System.getProperty("exist.jmxagent", "org.exist.management.impl.JMXAgent");
            try {
                Class clazz = Class.forName(className);
                if (!Agent.class.isAssignableFrom(clazz)) {
                    LOG.warn("Class " + className + " does not implement interface Agent. Using fallback.");
                } else {
                    instance = (Agent) clazz.newInstance();
                }
            } catch (ClassNotFoundException e) {
                LOG.warn("Class not found for JMX agent: " + className);
            } catch (IllegalAccessException e) {
                LOG.warn("Failed to instantiate class for JMX agent: " + className);
            } catch (InstantiationException e) {
                LOG.warn("Failed to instantiate class for JMX agent: " + className);
            }
            if (instance == null)
                instance = new DummyAgent();
        }
        return instance;
    }
}
