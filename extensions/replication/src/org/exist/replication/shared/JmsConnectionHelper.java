/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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
 */
package org.exist.replication.shared;

import java.lang.reflect.InvocationTargetException;
import javax.jms.ConnectionFactory;
import org.apache.commons.lang3.reflect.MethodUtils;

/**
 * Helper methods for setting up connections to a broker.
 * 
 * @author Dannes Wessels <dannes@exist-db.org>
 */


public class JmsConnectionHelper {

    /**
     * Set properties on the ConnectionFactory.
     *
     * @param cf The connection factory
     * @throws NoSuchMethodException if there is no such accessible method
     * @throws IllegalAccessException  wraps an exception thrown by the method invoked
     * @throws InvocationTargetException if the requested method is not accessible via reflection
     */
    public static void configureConnectionFactory(ConnectionFactory cf, ClientParameters params) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        String group = "connectionfactory";
        String[] allMethods = {"setUserName", "setPassword"};
        for (String method : allMethods) {
            String value = params.getParameterValue(group, method);
            if (value != null) {
                MethodUtils.invokeMethod(cf, method, value);
            }
        }
    }
    
}
