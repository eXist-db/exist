/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.config;

import java.lang.ref.WeakReference;
import java.util.*;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;

/**
 * configuration -&gt; element
 * property -&gt; attribute
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ConfigurationImpl implements Configuration {

    private Map<String, Object> runtimeProperties = new HashMap<>();

    protected WeakReference<Configurable> configuredObjectReference = null;

    private Element element;

    private ConfigurationImpl() {
        //Nothing to do
    }

    protected ConfigurationImpl(final Element element) {
        this.element = element;
    }

    @Override
    public Element getElement() {
        return element;
    }

    @Override
    public String getName() {
        return element.getLocalName();
    }

    @Override
    public String getValue() {
        return element.getTextContent();
    }

    @Override
    public Configuration getConfiguration(String name) {
        if (element.getLocalName().equals(name)) {
            return this;
        }
        final List<Configuration> list = getConfigurations(name);
        if (list == null)
            {return null;}
        if (!list.isEmpty())
            {return list.getFirst();}
        return null;
    }

    @Override
    public List<Configuration> getConfigurations(String name) {

        final List<Configuration> list = new ArrayList<>();
        
        Node child = element.getFirstChild();
        while (child != null) {
            
            if (child.getNodeType() == Node.ELEMENT_NODE) {

                final Element el = (Element)child;

                final String ns = el.getNamespaceURI();
                if (name.equals( el.getLocalName() ) && ns != null && NS.equals( ns )) {
                    
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
        
        props = new HashMap<>();
        Set<String> names = new HashSet<>();
        
        Node child = element.getFirstChild();
        while (child != null) {
            
            if (child.getNodeType() == Node.ELEMENT_NODE) {

                final String ns = child.getNamespaceURI();
                if (ns != null && NS.equals(ns)) {
                    
                    String name = child.getLocalName();
                    
                    if (names.contains(name)) {
                        
                        if (props.containsKey(name)) {
                            props.remove(name);
                        }
                    } else {
                        props.put(name, child.getTextContent());
                        names.add(name);
                    }
                }
            }
            child = child.getNextSibling();
        }
        
        //load attributes values
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            
            Node attr = attrs.item(i);
            
            if ( !XMLConstants.XMLNS_ATTRIBUTE.equals( attr.getPrefix() ) ) {
                
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
//            return nodes.item(0).getTextContent();
//        }
//        return null;
    }

    public String getProperty(String name, String default_property) {
        final String property = getProperty(name);
        
        if (property == null) return default_property;
        
        return property;
    }

    @Override
    public Map<String, String> getPropertyMap(String name) {
        final Map<String, String> map = new HashMap<>();

        if (hasProperty(name)) {
            map.put(name, getProperty(name));
            return map;
        }
        
        Node child = element.getFirstChild();
        while (child != null) {
            
            if (child.getNodeType() == Node.ELEMENT_NODE) {

                final Element el = (Element) child;

                final String ns = el.getNamespaceURI();
                if (name.equals( el.getLocalName() ) && ns != null && NS.equals( ns )) {
                    
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
                    final String value = el.getTextContent();

                    if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
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
//            final String value = item.getTextContent();
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
    public Boolean getPropertyBoolean(final String name) {
        final String value = getProperty(name);
        if(value == null) {
            return null;
        }
        return switch (value.toLowerCase()) {
            case "yes", "true" -> true;
            case "no", "false" -> false;
            default -> null;
        };
    }

    public Boolean getPropertyBoolean(String name, boolean defaultValue) {
        Boolean value = getPropertyBoolean(name);
        if(value == null) return defaultValue;

        return value;
    }

    @Override
    public Integer getPropertyInteger(final String name) {
        final String value = getProperty(name);
        if (value == null) {
            return null;
        }
        return Integer.valueOf(value);
    }

    public Integer getPropertyInteger(final String name, final Integer defaultValue, final boolean positive) {
        final String value = getProperty(name);
        if (value == null) {
            return defaultValue;
        }
        final int result = Integer.parseInt(value);
        if ((positive) && (result < 0)) {
            return defaultValue;
        }
        return result;
    }

    @Override
    public Long getPropertyLong(final String name) {
        final String value = getProperty(name);
        if (value == null) {
            return null;
        }
        return Long.valueOf(value);
    }

    public Long getPropertyLong(final String name, final Long defaultValue, final boolean positive) {
        final String value = getProperty(name);
        if (value == null) {
            return defaultValue;
        }
        final long result = Long.parseLong(value);
        if ((positive) && (result < 0)) {
            return defaultValue;
        }
        return result;
    }

    public Integer getPropertyMegabytes(String name, Integer defaultValue) {
        String cacheMem = element.getAttribute(name);
        if (cacheMem != null) {
            if (cacheMem.endsWith("M") || cacheMem.endsWith("m")) {
                cacheMem = cacheMem.substring(0, cacheMem.length() - 1);
            }
            final Integer result = Integer.valueOf(cacheMem);
            if (result < 0) {
                return defaultValue;
            }
            return result;
        }
        return defaultValue;
    }

    public String getConfigFilePath() {
        return "";//XXX: put config url
    }

    @Override
    public Set<String> getProperties() {
        final Set<String> properties = new HashSet<>();
        final NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            //ignore namespace declarations
            if ( !XMLConstants.XMLNS_ATTRIBUTE.equals( attrs.item(i).getPrefix() ) )
                {properties.add(attrs.item(i).getNodeName());}
        }
        final NodeList children = element.getChildNodes();
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
            objects = new HashMap<>();
        
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
    public void checkForUpdates(Element element) {
        synchronized (this) {
            if (!saving && configuredObjectReference != null && configuredObjectReference.get() != null) {
                clearCache();
                this.element = element;
                Configurator.configure(configuredObjectReference.get(), this);
            }
        }
    }

    @Override
    public void save() throws PermissionDeniedException, ConfigurationException {
        //ignore in-memory nodes
        if (element instanceof org.exist.dom.memtree.ElementImpl) {
            return;
        }

        synchronized (this) {
            try {
                saving = true;
                if (configuredObjectReference != null && configuredObjectReference.get() != null)
                    {Configurator.save(
                        configuredObjectReference.get(),
                        ((DocumentImpl)element.getOwnerDocument()).getURI()
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
        if (element instanceof org.exist.dom.memtree.ElementImpl) {
            return;
        }

        synchronized(this) {
            try {
                saving = true;
                if (configuredObjectReference != null && configuredObjectReference.get() != null)
                    {Configurator.save(broker,
                        configuredObjectReference.get(),
                        ((DocumentImpl) element.getOwnerDocument()).getURI()
                    );}
            } catch (final Exception e) {
               throw new ConfigurationException(e.getMessage(), e);
            } finally {
                saving = false;
            }
        }
    }

    @Override
    public boolean equals(final Object obj) {
        return equals(obj, Optional.empty());
    }

    @Override
    public boolean equals(final Object obj, final Optional<String> property) {
        if (obj instanceof ConfigurationImpl conf) {
            if (!(getName().equals(conf.getName()))) {
                return false;
            }

            final String name = property.orElse(Configuration.ID);
            final Optional<String> value = Optional.ofNullable(getProperty(name));

            return value.map(v -> v.equals(conf.getProperty(name))).orElse(false);
        }

        return false;
    }
}
