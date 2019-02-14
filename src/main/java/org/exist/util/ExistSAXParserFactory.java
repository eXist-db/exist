/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
package org.exist.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.xml.parsers.SAXParserFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class for creating an instance of javax.xml.parsers.SAXParserFactory
 *
 * @author dizzzz@exist-db.org
 */
public class ExistSAXParserFactory {

    private final static Logger LOG = LogManager.getLogger(ExistSAXParserFactory.class);

    public final static String ORG_EXIST_SAXPARSERFACTORY = "org.exist.SAXParserFactory";

    /**
     * Get SAXParserFactory instance specified by factory class name.
     *
     * @param className Full class name of factory
     * @return A Sax parser factory or NULL when not available.
     */
    public static SAXParserFactory getSAXParserFactory(final String className) {

        Class<?> clazz = null;
        try {
            clazz = Class.forName(className);

        } catch (final ClassNotFoundException ex) {
            // quick escape
            if (LOG.isDebugEnabled()) {
                LOG.debug(className + ": " + ex.getMessage(), ex);
            }
            return null;
        }

        // Get specific method
        Method method = null;
        try {
            method = clazz.getMethod("newInstance", (Class[]) null);
        } catch (final SecurityException | NoSuchMethodException ex) {
            // quick escape
            if (LOG.isDebugEnabled()) {
                LOG.debug("Method " + className + ".newInstance not found.", ex);
            }
            return null;
        }

        // Invoke method
        Object result = null;
        try {
            result = method.invoke(null, (Object[]) null);

        } catch (final IllegalAccessException | InvocationTargetException ex) {
            // quick escape
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not invoke method " + className + ".newInstance.", ex);
            }
            return null;
        }

        if (!(result instanceof SAXParserFactory)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not create instance of SAXParserFactory: " + result.toString());
            }
            return null;
        }

        return (SAXParserFactory) result;
    }

    /**
     * Get instance of a SAXParserFactory. Return factory specified by
     * system property org.exist.SAXParserFactory (if available) otherwise
     * return system default.
     *
     * @return A sax parser factory.
     */
    public static SAXParserFactory getSAXParserFactory() {

        SAXParserFactory factory = null;

        // Get SAXParser configuratin from system
        final String config = System.getProperty(ORG_EXIST_SAXPARSERFACTORY);

        // Get SAXparser factory specified by system property
        if (config != null) {
            factory = getSAXParserFactory(config);
        }

        // If no factory could be retrieved, create system default property.
        if (factory == null) {
            factory = SAXParserFactory.newInstance();
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Using default SAXParserFactory '%s'", factory.getClass().getCanonicalName()));
            }
        }

        return factory;
    }
}
