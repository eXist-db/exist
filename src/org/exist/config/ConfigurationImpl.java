/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.exist.dom.ElementAtExist;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

/**
 * configuration -> element
 * property -> attribute
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ConfigurationImpl extends ProxyElement<ElementAtExist> implements Configuration {
	
	private Map<String, Object> runtimeProperties = new HashMap<String, Object>();
	
	private Map<String, List<Configuration>> configs = new HashMap<String, List<Configuration>>();
	
	private ConfigurationImpl() {
	}

	protected ConfigurationImpl(ElementAtExist element) {
		this();
		setProxyObject(element);
	}

	@Override
	public String getName() {
		return getLocalName();
	}

	public Configuration getConfiguration(String name) {
		if (getLocalName().equals(name)) {
			return this;
		}
		
		List<Configuration> list = getConfigurations(name);
		
		if (list == null)
			return null;
		
		if (list.size() > 0)
			return list.get(0);
		
		return null;
	}

	public List<Configuration> getConfigurations(String name) {
		if (configs.containsKey(name))
			return configs.get(name);
		
		NodeList nodes = getElementsByTagName(name);

		if (nodes.getLength() > 0) { 
			List<Configuration> list = new ArrayList<Configuration>();
		
			for (int i = 0; i < nodes.getLength(); i++) {
				Configuration config = new ConfigurationImpl((ElementAtExist) nodes.item(i));
				list.add(config);
			}
			
			configs.put(name, list);
			return list;
		}
		
		return null;
	}

	public String getProperty(String name) {
        return getAttribute(name);
    }
    
	public String getProperty(String name, String default_property) {
		String property = getAttribute(name);
		if (property == null)
			return default_property;
		
        return property;
    }

	public boolean hasProperty(String name) {
        return hasAttribute(name);
    }
    
    public void setProperty(String name, String value) {
        setAttribute(name, value);
    }

	@Override
	public void setProperty(String property, Integer value) {
		setAttribute(property, String.valueOf(value));
	}
    
    public Object getRuntimeProperty(String name) {
        return runtimeProperties.get(name);
    }

	public boolean hasRuntimeProperty(String name) {
        return runtimeProperties.containsKey(name);
    }

	public void setRuntimeProperty(String name, Object obj) {
        runtimeProperties.put(name, obj);
    }
    
    public Boolean getPropertyBoolean(String name) {
    	String value = getProperty(name);
        
    	if(value == null)
            return null;
        
    	if ("yes".equalsIgnoreCase(value))
    		return true;
    	else if ("no".equalsIgnoreCase(value))
    		return false;
    	else if ("true".equalsIgnoreCase(value))
    		return true;
    	else if ("false".equalsIgnoreCase(value))
    		return false;
    	
    	//???
    	return null;
    }

	public Boolean getPropertyBoolean(String name, boolean defaultValue) {
    	String value = getProperty(name);
        if(value == null)
            return Boolean.valueOf(defaultValue);     
        return Boolean.valueOf("yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value));
    }
    
    public Integer getPropertyInteger(String name) {
        String value = getProperty(name);
        
        if (value == null)
            return null;
        
        return Integer.valueOf(value);
    }

    public Integer getPropertyInteger(String name, Integer defaultValue, boolean positive) {
        String value = getProperty(name);
        
        if (value == null)
            return defaultValue;
        
        Integer result = Integer.valueOf(value);
        if ((positive) && (result < 0))
            return defaultValue.intValue();
        
        return result;
    }

    public Long getPropertyLong(String name) {
        String value = getProperty(name);
        
        if (value == null)
            return null;
        
        return Long.valueOf(value);
    }
    
    public Long getPropertyLong(String name, Long defaultValue, boolean positive) {
        String value = getProperty(name);
        
        if (value == null)
            return defaultValue;
        
        long result = Long.valueOf(value);
        if ((positive) && (result < 0))
            return defaultValue.longValue();
        
        return result;
    }

    public Integer getPropertyMegabytes(String name, Integer defaultValue) {
    	String cacheMem = getAttribute(name);
    	if (cacheMem != null) {
    		if (cacheMem.endsWith("M") || cacheMem.endsWith("m"))
    			cacheMem = cacheMem.substring(0, cacheMem.length() - 1);
    		
    		Integer result = new Integer(cacheMem);
    		if (result < 0)
    			return defaultValue;
   			return result;
    	}
    	return defaultValue;
    }
    
    public String getConfigFilePath() {
    	return "";//XXX: put config url
    }

	
    public Set<String> getProperties() {
    	NamedNodeMap attrs = getAttributes();
    	
    	Set<String> properties = new HashSet<String>();
    	for (int index = 0; index < attrs.getLength(); index++) {
    		properties.add(attrs.item(index).getLocalName());
    	}
    	
    	return properties;
	}

	@Override
	public Class<?> getPropertyClass(String propertySecurityClass) {
		// TODO Auto-generated method stub
		return null;
	}

	//related objects
	Map<String, Object> objects = new HashMap<String, Object>();
	
	@Override
	public Object putObject(String name, Object object) {
		return objects.put(name, object);
	}

	@Override
	public Object getObject(String name) {
		return objects.get(name);
	}
}