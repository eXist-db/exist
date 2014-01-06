/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2013 The eXist Project
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
        final List<Configuration> list = getConfigurations(name);
        if (list == null)
            {return null;}
        if (list.size() > 0)
            {return list.get(0);}
        return null;
    }

    @Override
    public List<Configuration> getConfigurations(String name) {

        final List<Configuration> list = new ArrayList<Configuration>();
        
        Node child = getFirstChild();
        while (child != null) {
            
            if (child.getNodeType() == Node.ELEMENT_NODE) {

                final ElementAtExist el = (ElementAtExist) child;
                
                if (name.equals( el.getLocalName() ) && NS.equals( el.getNamespaceURI() )) {
                    
                    final Configuration config = new ConfigurationImpl(el);
                    list.add(config);
                
                }
            }
            
            child = child.getNextSibling();
        }

        return list;
    }
    
    private Map<String, String> props = null;
    
    private void cache() {
        
        if (props != null)
            return;
        
        props = new HashMap<String, String>();
        Set<String> names = new HashSet<String>();
        
        Node child = getFirstChild();
        while (child != null) {
            
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                
                if (NS.equals(child.getNamespaceURI())) {
                    
                    String name = child.getLocalName();
                    
                    if (names.contains(name)) {
                        
                        if (props.containsKey(name)) {
                            props.remove(name);
                        }
                    } else {
                        props.put(name, child.getNodeValue());
                    }
                }
            }
            
            child = child.getNextSibling();
        }
        
        //load attributes values
        NamedNodeMap attrs = getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            
            Node attr = attrs.item(i);
            
            if ( !"xmlns".equals( attr.getPrefix() ) ) {
                
                props.put(attr.getLocalName(), attr.getNodeValue());
            }
        }
    }
    
    public void clearCache() {
        props = null;
    }

    @Override
    public String getProperty(String name) {
        
        cache();
        
        return props.get(name);

//        if (hasAttribute(name))
//            {return getAttribute(name);}
//        final NodeList nodes = getElementsByTagNameNS(NS, name);
//        if (nodes.getLength() == 1) {
//            return nodes.item(0).getNodeValue();
//        }
//        return null;
    }

    public String getProperty(String name, String default_property) {
        final String property = getProperty(name);
        
        if (property == null) {
            return default_property;
        }
        
        return property;
    }

    @Override
    public Map<String, String> getPropertyMap(String name) {
        if (props.containsKey(name))
            return null;

        final Map<String, String> map = new HashMap<String, String>();
        
        Node child = getFirstChild();
        while (child != null) {
            
            if (child.getNodeType() == Node.ELEMENT_NODE) {

                final ElementAtExist el = (ElementAtExist) child;
                
                if (name.equals( el.getLocalName() ) && NS.equals( el.getNamespaceURI() )) {
                    
                    if(!el.hasAttributes()){
                        continue;
                    }
                    
                    final NamedNodeMap attrs = el.getAttributes();
 
                    if (attrs.getLength() != 1) {
                        continue;
                    }
                    
                    Node attr = attrs.getNamedItem("key");
                    
                    if (attr == null)
                        continue;
                    
                    final String key = attr.getNodeValue();
                    final String value = el.getNodeValue();
                    
                    if(key == null || key.isEmpty() || value == null || value.isEmpty()){
                        ; //skip
                    } else {
                        map.put(key, value);
                    }
                
                }
            }
            
            child = child.getNextSibling();
        }

        return map;
        
//        if(hasAttribute(name)) {
//            return null;
//        }
//        final Map<String, String> map = new HashMap<String, String>();
//        final NodeList nodes = getElementsByTagNameNS(NS, name);
//        for(int i = 0; i < nodes.getLength(); i++) {
//            final Node item = nodes.item(i);
//            if(!item.hasAttributes()){
//                return null;
//            }
//            final NamedNodeMap attrs = item.getAttributes();
//            if(attrs.getLength() != 1){
//                return null;
//            }
//            final String key = attrs.getNamedItem("key").getNodeValue();
//            final String value = item.getNodeValue();
//            if(value == null || value.isEmpty()){
//                return null;
//            }
//            map.put(key, value);
//        }
//        return map;
    }

    @Override
    public boolean hasProperty(String name) {
        cache();
        
        return props.containsKey(name);
        
//        if (hasAttribute(name))
//            {return true;}
//        return (getElementsByTagName(name).getLength() == 1);
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
        final String value = getProperty(name);
        if(value == null)
            {return null;}
        if ("yes".equalsIgnoreCase(value))
            {return true;}
        else if ("no".equalsIgnoreCase(value))
            {return false;}
        else if ("true".equalsIgnoreCase(value))
            {return true;}
        else if ("false".equalsIgnoreCase(value))
            {return false;}
        //???
        return null;
    }

    public Boolean getPropertyBoolean(String name, boolean defaultValue) {
        final String value = getProperty(name);
        if(value == null)
            {return Boolean.valueOf(defaultValue);}
        return Boolean.valueOf("yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value));
    }

    @Override
    public Integer getPropertyInteger(String name) {
        final String value = getProperty(name);
        if (value == null)
            {return null;}
        return Integer.valueOf(value);
    }

    public Integer getPropertyInteger(String name, Integer defaultValue, boolean positive) {
        final String value = getProperty(name);
        if (value == null)
            {return defaultValue;}
        final Integer result = Integer.valueOf(value);
        if ((positive) && (result < 0))
            {return defaultValue.intValue();}
        return result;
    }

    @Override
    public Long getPropertyLong(String name) {
        final String value = getProperty(name);
        if (value == null)
            {return null;}
        return Long.valueOf(value);
    }

    public Long getPropertyLong(String name, Long defaultValue, boolean positive) {
        final String value = getProperty(name);
        if (value == null)
            {return defaultValue;}
        final long result = Long.valueOf(value);
        if ((positive) && (result < 0))
            {return defaultValue.longValue();}
        return result;
    }

    public Integer getPropertyMegabytes(String name, Integer defaultValue) {
        String cacheMem = getAttribute(name);
        if (cacheMem != null) {
            if (cacheMem.endsWith("M") || cacheMem.endsWith("m"))
                {cacheMem = cacheMem.substring(0, cacheMem.length() - 1);}
            final Integer result = new Integer(cacheMem);
            if (result < 0)
                {return defaultValue;}
            return result;
        }
        return defaultValue;
    }

    public String getConfigFilePath() {
        return "";//XXX: put config url
    }

    @Override
    public Set<String> getProperties() {
        final Set<String> properties = new HashSet<String>();
        final NamedNodeMap attrs = getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            //ignore namespace declarations
            if ( !"xmlns".equals( attrs.item(i).getPrefix() ) )
                {properties.add(attrs.item(i).getNodeName());}
        }
        final NodeList children = getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                properties.add(child.getNodeName());
            }
        }
        return properties;
    }

    //related objects
    Map<String, Object> objects = null;

    @Override
    public synchronized Object putObject(String name, Object object) {
        if (objects == null)
            objects = new HashMap<String, Object>();
        
        return objects.put(name, object);
    }

    @Override
    public synchronized Object getObject(String name) {
        if (objects == null)
            return null;
        
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
            {return;} 
        synchronized (this) {
            try {
                saving = true;
                if (configuredObjectReference != null && configuredObjectReference.get() != null)
                    {Configurator.save(
                        configuredObjectReference.get(), 
                        getProxyObject().getDocumentAtExist().getURI()
                    );}
            } catch (final Exception e) {
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
            {return;} 
        synchronized(this) {
            try {
                saving = true;
                if (configuredObjectReference != null && configuredObjectReference.get() != null)
                    {Configurator.save(broker,
                        configuredObjectReference.get(), 
                        getProxyObject().getDocumentAtExist().getURI()
                    );}
            } catch (final Exception e) {
               throw new ConfigurationException(e.getMessage(), e);
            } finally {
                saving = false;
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConfigurationImpl) {
            final ConfigurationImpl conf = (ConfigurationImpl)obj;
            if (!(getName().equals(conf.getName())))
                {return false;}
            final String id = getProperty(Configuration.ID);
            if (id == null) {
                return false;
            }
            if (id.equals(conf.getProperty(Configuration.ID)))
                {return true;}
        }
        return false;
    }

    public boolean equals(Object obj, String uniqField) {
        if (obj instanceof ConfigurationImpl) {
            final ConfigurationImpl conf = (ConfigurationImpl)obj;
            if (!(getName().equals(conf.getName())))
                {return false;}
            final String uniq = getProperty( uniqField);
            if (uniq == null) {
                return false;
            }
            if (uniq.equals(conf.getProperty(uniqField)))
                {return true;}
        }
        return false;
    }
}
