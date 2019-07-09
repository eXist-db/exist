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
package org.exist.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.w3c.dom.Element;

/**
 * Configuration interface provide methods to read settings.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Configuration {

    String NS = "http://exist-db.org/Configuration";

    String ID = "id";

    /**
     * Return sub configuration by name.
     * @param name of the configuration
     * @return Configuration
     */
    Configuration getConfiguration(String name);

    /**
     * Return list of sub configurations by name.
     * 
     * @param name of the sub configuration
     * @return the selected sub configuration
     */
    List<Configuration> getConfigurations(String name);

    /**
     * Set of properties configuration have
     * @return set of properties of the configuration
     * 
     */
    Set<String> getProperties();

    /**
     * Check presents of setting by name
     * @param name of the property
     * @return true if the property is in the configuration  otherwise false
     */
    boolean hasProperty(String name);

    /**
     * Return property string value.
     * 
     * @param property to get the value for
     * @return String value of the requested property
     */
    String getProperty(String property);

    /**
     * Return property map value.
     * 
     * @param property name of the property map
     * @return property map
     */
    Map<String, String> getPropertyMap(String property);

    /**
     * Return property integer value.
     * 
     * @param property name
     * @return property integer value
     *
     */
    Integer getPropertyInteger(String property);

    /**
     * Return property long value.
     *
     * @param property name
     * @return property long value
     *
     */
    Long getPropertyLong(String property);

    /**
     * Return property boolean value.
     *
     * @param property name
     * @return property boolean value
     * 
     */
    Boolean getPropertyBoolean(String property);

    /**
     * Keep at internal map object associated with key.
     * 
     * @param name of the object
     * @param object to add
     * @return the created object
     */
    Object putObject(String name, Object object);

    /**
     * Get object associated by key from internal map.
     * 
     * @param name of the object
     * @return the according object
     */
    Object getObject(String name);

    /**
     * Configuration name.
     * @return name of the Configuration
     */
    String getName();

    /**
     * Return configuration's String value.
     * @return configuration's string value
     */
    String getValue();

    /**
     * Return element associated with configuration.
     * @return element associated with configuration.
     */
    Element getElement();

    /**
     * Perform check for changers.
     * 
     * @param document to check for changes
     */
    void checkForUpdates(Element document);

    /**
     * Save configuration.
     * 
     * @throws PermissionDeniedException if permission to save the configuration is denied
     * @throws ConfigurationException if there is an error in the configuration
     */
    void save() throws PermissionDeniedException, ConfigurationException;

    /**
     * Save configuration.
     * 
     * @param broker eXist-db DBBroker
     * @throws PermissionDeniedException if permission to save the configuration is denied
     * @throws ConfigurationException if there is an error in the configuration
     */
    void save(DBBroker broker) throws PermissionDeniedException, ConfigurationException;

    /**
     * Determines equality based on a property value of the configuration
     *
     * @param obj The Configured instance
     * @param property The name of the property to use for comparison, or
     *                 if empty, the {@link ConfigurationImpl#ID} is used.
     * @return true if obj equals property otherwise false
     */
    boolean equals(Object obj, Optional<String> property);
    
    /**
     * Free up memory allocated for cache.
     */
    void clearCache();
}