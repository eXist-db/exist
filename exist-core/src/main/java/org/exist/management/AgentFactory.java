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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static java.lang.invoke.MethodType.methodType;

public class AgentFactory {

    private final static Logger LOG = LogManager.getLogger(AgentFactory.class);

    private static Agent instance = null;

    public static Agent getInstance() {
        if (instance == null) {
            final String className = System.getProperty("exist.jmxagent", "org.exist.management.impl.JMXAgent");
            try {
                final Class<?> clazz = Class.forName(className);
                if (!Agent.class.isAssignableFrom(clazz)) {
                    LOG.warn("Class " + className + " does not implement interface Agent. Using fallback.");
                } else {
                    final MethodHandles.Lookup lookup = MethodHandles.publicLookup();

                    // 1. try for default constructor
                    try {
                        final MethodHandle mhConstructor = lookup.findConstructor(clazz, methodType(void.class));
                        instance = (Agent) mhConstructor.invokeExact();
                    } catch (final NoSuchMethodException | IllegalAccessException e) {
                        LOG.warn("No default constructor found for Agent: " + className + ". Will try singleton pattern...");

                        // 2. try for singleton with static getInstance()
                        try {
                            final MethodHandle methodHandle = lookup.findStatic(clazz, "getInstance", methodType(Agent.class));
                            instance = (Agent) methodHandle.invokeExact();
                        } catch (final NoSuchMethodException | IllegalAccessException e2) {
                            LOG.warn("No singleton pattern found for Agent: " + className);
                        }
                    }
                }
            } catch (final Throwable e) {
                LOG.error("Unable to instantiate JMX agent: " + className + ". JMX will be unavailable!", e);
            }

            if (instance == null) {
                instance = new DummyAgent();
            }
        }

        return instance;
    }
}
