/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
package org.exist.storage;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.exist.EXistException;
import org.exist.util.Configuration;

public class BrokerFactory {

    public static final String PROPERTY_DATABASE = "database";

    private static Class<?> constructorArgs[] = { BrokerPool.class, Configuration.class };

    private static Map<String, Class<? extends DBBroker>> objClasses =  new HashMap<String, Class<? extends DBBroker>>();

    public static void plug(String id, Class<? extends DBBroker> clazz) {
        objClasses.put(id.toUpperCase(Locale.ENGLISH), clazz);
    }

    static {
        plug("NATIVE", NativeBroker.class);
    }

    public static DBBroker getInstance(BrokerPool database, Configuration conf) throws EXistException {
        String brokerID = (String) conf.getProperty(PROPERTY_DATABASE);
        if (brokerID == null) {
            throw new RuntimeException("no database defined");
        }
        
        // Repair name ; https://sourceforge.net/p/exist/bugs/810/
        brokerID = brokerID.toUpperCase(Locale.ENGLISH);
        if (!objClasses.containsKey(brokerID)) {
            throw new RuntimeException("no database backend found for " + brokerID);
        }
        
        try {
            final Class<? extends DBBroker> clazz = objClasses.get(brokerID);
            final Constructor<? extends DBBroker> constructor = clazz.getConstructor(constructorArgs);
            return constructor.newInstance(database, conf);
            
        } catch (final Exception e) {
            throw new RuntimeException("can't get database backend " + brokerID, e);
        }
    }
}
