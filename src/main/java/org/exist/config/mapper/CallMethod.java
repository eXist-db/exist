/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.config.mapper;

import java.lang.reflect.Method;

import org.exist.config.Configuration;
import org.exist.config.Configurator;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class CallMethod {

    private Object obj = null;
    private Configuration conf = null;
    private Object value = null;

    private String name = null;
    private String attribute = null;
    private String element = null;

    public CallMethod(Object obj, Configuration conf) {
        this.obj = obj;
        this.conf = conf;
    }

    public void set(String name, String value) {
        if ("name".equals(name)) {
            this.name = value;
        } else if ("attribute".equals(name)) {
            this.attribute = value;
        } else if ("element".equals(name)) {
            this.element = value;
        }
    }

    public boolean eval() throws Exception {
        if (name == null) {
            Configurator.LOG.error("'callMethod' element must have 'name' attribute, skip instance creation.");
            return false;
        } else if (attribute == null && element == null) {
            Configurator.LOG.error("'callMethod' element must have 'attribute' or 'element' attribute, skip instance creation.");
            return false;
        }

        final Method[] methods = obj.getClass().getMethods();
        for (Method method : methods) {
            if (method.getName().equals(name)) {
                final Class<?>[] types = method.getParameterTypes();
                if (types.length == 1) {
                    final String typeName = types[0].getName();
                    if (element != null) {
                        if (typeName.equals(value.getClass().getName())) {
                            method.invoke(obj, value);
                            return true;
                        }
                    } else {
                        if ("java.lang.String".equals(typeName)) {
                            method.invoke(obj, conf.getElement().getAttribute(attribute));
                            return true;

                        } else if ("int".equals(typeName) || "java.lang.Integer".equals(typeName)) {
                            method.invoke(obj, Integer.valueOf(conf.getElement().getAttribute(attribute)));
                            return true;
                        }
                    }
                }
            }
        }
        Configurator.LOG.error("'callMethod' element '" + name + "' method can not be found, skip instance creation.");

        return false;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Configuration getConfiguration() {
        if (element != null) return conf.getConfiguration(element);
        return conf;
    }
}