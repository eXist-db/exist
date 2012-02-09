/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2011 The eXist Project
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.exist.dom.ElementAtExist;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
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

    protected WeakReference<Configurable> configuredObjectReference = null;
    
    private ConfigurationImpl() {
        //Nothing to do
    }

    protected ConfigurationImpl(ElementAtExist element) {
        this();
        setProxyObject(element);
    }

    @Override
    public ElementAtExist getElement() {
        return getProxyObject();
    }

    @Override
    public String getName() {
        return getLocalName();
    }

    @Override
    public String getValue() {
        return getElement().getNodeValue();
    }

    @Override
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

    @Override
    public List<Configuration> getConfigurations(String name) {
        NodeList nodes = getElementsByTagNameNS(Configuration.NS, name);
        List<Configuration> list = new ArrayList<Configuration>();
        if (nodes.getLength() > 0) {
            for (int i = 0; i < nodes.getLength(); i++) {
                Configuration config = new ConfigurationImpl((ElementAtExist) nodes.item(i));
                list.add(config);
            }
        }
        return list;
    }

    @Override
    public String getProperty(String name) {
        if (hasAttribute(name))
            return getAttribute(name);
        NodeList nodes = getElementsByTagNameNS(NS, name);
        if (nodes.getLength() == 1) {
            return nodes.item(0).getNodeValue();
        }
        return null;
    }

    public String getProperty(String name, String default_property) {
        String property = getProperty(name);
        if (property == null)
            return default_property;
        return property;
    }

    @Override
    public Map<String, String> getPropertyMap(String name) {
        if(hasAttribute(name)) {
            return null;
        }
        Map<String, String> map = new HashMap<String, String>();
        NodeList nodes = getElementsByTagNameNS(NS, name);
        for(int i = 0; i < nodes.getLength(); i++) {
            Node item = nodes.item(i);
            if(!item.hasAttributes()){
                return null;
            }
            NamedNodeMap attrs = item.getAttributes();
            if(attrs.getLength() != 1){
                return null;
            }
            String key = attrs.getNamedItem("key").getNodeValue();
            String value = item.getNodeValue();
            if(value == null || value.isEmpty()){
                return null;
            }
            map.put(key, value);
        }
        return map;
    }

    @Override
    public boolean hasProperty(String name) {
        if (hasAttribute(name))
            return true;
        return (getElementsByTagName(name).getLength() == 1);
    }

    @Override
    public void setProperty(String name, String value) {
        //detect save place: attribute or element's text
        setAttribute(name, value);
    }

    @Override
    public void setProperty(String property, Integer value) {
        setProperty(property, String.valueOf(value));
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    public Set<String> getProperties() {
        Set<String> properties = new HashSet<String>();
        NamedNodeMap attrs = getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            //ignore namespace declarations
            if ( !"xmlns".equals( attrs.item(i).getPrefix() ) )
                properties.add(attrs.item(i).getNodeName());
        }
        NodeList children = getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                properties.add(child.getNodeName());
            }
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

    private boolean saving = false;

    @Override
    public void checkForUpdates(ElementAtExist element) {
        if (!saving && configuredObjectReference != null && configuredObjectReference.get() != null) {
            setProxyObject(element);
            Configurator.configure(configuredObjectReference.get(), this);
        }
    }

    @Override
    public void save() throws PermissionDeniedException, ConfigurationException {
        //ignore in-memory nodes
        if (getProxyObject().getClass().getPackage().getName().startsWith("org.exist.memtree"))
            return; 
        synchronized (this) {
            try {
                saving = true;
                if (configuredObjectReference != null && configuredObjectReference.get() != null)
                    Configurator.save(
                        configuredObjectReference.get(), 
                        getProxyObject().getDocumentAtExist().getURI()
                    );
            } catch (Exception e) {
               throw new ConfigurationException(e.getMessage(), e);
            } finally {
                saving = false;
            }
        }
    }

    @Override
    public void save(final DBBroker broker) throws PermissionDeniedException, ConfigurationException {
        //ignore in-memory nodes
        if (getProxyObject().getClass().getPackage().getName().startsWith("org.exist.memtree"))
            return; 
        synchronized(this) {
            try {
                saving = true;
                if (configuredObjectReference != null && configuredObjectReference.get() != null)
                    Configurator.save(broker,
                        configuredObjectReference.get(), 
                        getProxyObject().getDocumentAtExist().getURI()
                    );
            } catch (Exception e) {
               throw new ConfigurationException(e.getMessage(), e);
            } finally {
                saving = false;
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConfigurationImpl) {
            ConfigurationImpl conf = (ConfigurationImpl)obj;
            if (!(getName().equals(conf.getName())))
                return false;
            String id = getProperty(Configuration.ID);
            if (id == null) {
                return false;
            }
            if (id.equals(conf.getProperty(Configuration.ID)))
                return true;
        }
        return false;
    }

    public boolean equals(Object obj, String uniqField) {
        if (obj instanceof ConfigurationImpl) {
            ConfigurationImpl conf = (ConfigurationImpl)obj;
            if (!(getName().equals(conf.getName())))
                return false;
            String uniq = getProperty( uniqField);
            if (uniq == null) {
                return false;
            }
            if (uniq.equals(conf.getProperty(uniqField)))
                return true;
        }
        return false;
    }
}
