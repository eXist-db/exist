/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011-2012 The eXist Project
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
package org.exist.collections.triggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.exist.collections.Collection;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;

/**
 *
 * @author aretter
 */
public abstract class AbstractTriggerProxy<T extends Trigger> implements TriggerProxy<T> {

    private final Class<T> clazz;
    private Map<String, List<? extends Object>> parameters;
    
    /**
     * The database Collection URI of where the configuration for this Trigger came from
     * typically somewhere under /db/system/config/db/
     */
    private final XmldbURI collectionConfigurationURI;

    public AbstractTriggerProxy(Class<? extends T> clazz, XmldbURI collectionConfigurationURI) {
        this.clazz = (Class<T>)clazz;
        this.collectionConfigurationURI = collectionConfigurationURI;
    }
    
    public AbstractTriggerProxy(Class<? extends T> clazz, XmldbURI collectionConfigurationURI, Map<String, List<? extends Object>> parameters) {
        this.clazz = (Class<T>)clazz;
        this.collectionConfigurationURI = collectionConfigurationURI;
        this.parameters = parameters;
    }

    protected Class<T> getClazz() {
        return clazz;
    }
    
    protected XmldbURI getCollectionConfigurationURI() {
        return collectionConfigurationURI;
    }
    
    @Override
    public void setParameters(Map<String, List<? extends Object>> parameters) {
        this.parameters = parameters;
    }
    
    protected Map<String, List<? extends Object>> getParameters() {
        return parameters;
    }
    
    protected T newInstance(DBBroker broker) throws TriggerException {
        try {
            final T trigger = getClazz().newInstance();

            XmldbURI collectionForTrigger = getCollectionConfigurationURI();
            if(collectionForTrigger.startsWith(XmldbURI.CONFIG_COLLECTION_URI)) {
                collectionForTrigger = collectionForTrigger.trimFromBeginning(XmldbURI.CONFIG_COLLECTION_URI);
            }

            final Collection collection = broker.getCollection(collectionForTrigger);
            trigger.configure(broker, collection, getParameters());

            return trigger;
        } catch (final InstantiationException ie) {
            throw new TriggerException("Unable to instantiate Trigger '"  + getClazz().getName() + "': " + ie.getMessage(), ie);
        } catch (final IllegalAccessException iae) {
            throw new TriggerException("Unable to instantiate Trigger '"  + getClazz().getName() + "': " + iae.getMessage(), iae);
        } catch (final PermissionDeniedException pde) {
            throw new TriggerException("Unable to instantiate Trigger '"  + getClazz().getName() + "': " + pde.getMessage(), pde);
        }
    }
    
    public static List<TriggerProxy> newInstance(Class c, XmldbURI collectionConfigurationURI, Map<String, List<? extends Object>> parameters) throws TriggerException {
        
        final List<TriggerProxy> proxies = new ArrayList<TriggerProxy>();
        
        if(DocumentTrigger.class.isAssignableFrom(c)) {
            proxies.add(new DocumentTriggerProxy((Class<DocumentTrigger>)c, collectionConfigurationURI, parameters));
        }
        
        
        if(CollectionTrigger.class.isAssignableFrom(c)) {
            proxies.add(new CollectionTriggerProxy((Class<CollectionTrigger>)c, collectionConfigurationURI, parameters));
        } 
        
        
        if(proxies.isEmpty()) {
            throw new TriggerException("Unknown Trigger class type: " + c.getName());
        }
        
        return proxies;
    }
}