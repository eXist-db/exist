/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2013 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.exist.dom.ElementAtExist;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;

/**
 * Configuration interface provide methods to read settings.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Configuration {

    public String NS = "http://exist-db.org/Configuration";

    public String ID = "id";

    /**
     * Return sub configuration by name.
     */
    public Configuration getConfiguration(String name);

    /**
     * Return list of sub configurations by name.
     * 
     * @param name
     */
    public List<Configuration> getConfigurations(String name);

    /**
     * Return set of properties configuration have.
     * 
     */
    public Set<String> getProperties();

    /**
     * Check presents of setting by name.
     * 
     * @param name
     */
    public boolean hasProperty(String name);

    /**
     * Return property string value.
     * 
     * @param property
     */
    public String getProperty(String property);

    /**
     * Return property map value.
     * 
     * @param property
     */
    public Map<String, String> getPropertyMap(String property);

    /**
     * Return property integer value.
     * 
     * @param property
     *
     */
    public Integer getPropertyInteger(String property);

    /**
     * Return property long value.
     * 
     * @param property
     * 
     */
    public Long getPropertyLong(String property);

    /**
     * Return property boolean value.
     * 
     * @param property
     * 
     */
    public Boolean getPropertyBoolean(String property);

    /**
     * Keep at internal map object associated with key.
     * 
     * @param name
     * @param object
     *
     */
    public Object putObject(String name, Object object);

    /**
     * Get object associated by key from internal map.
     * 
     * @param name
     * 
     */
    public Object getObject(String name);

    /**
     * Configuration name.
     * 
     * 
     */
    public String getName();

    /**
     * Return configuration's String value.
     *  
     * 
     */
    public String getValue();

    /**
     * Return element associated with configuration.
     *  
     * 
     */
    public ElementAtExist getElement();

    /**
     * Perform check for changers.
     * 
     * @param document
     */
    public void checkForUpdates(ElementAtExist document);

    /**
     * Save configuration.
     * 
     * @throws PermissionDeniedException
     * @throws ConfigurationException
     */
    public void save() throws PermissionDeniedException, ConfigurationException;

    /**
     * Save configuration.
     * 
     * @param broker
     * @throws PermissionDeniedException
     * @throws ConfigurationException
     */
    public void save(DBBroker broker) throws PermissionDeniedException, ConfigurationException;

    public boolean equals(Object obj, String uniqField);
    
    /**
     * Free up memory allocated for cache.
     */
    public void clearCache();
}