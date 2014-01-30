/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
package org.exist.collections.triggers;

import java.util.List;
import java.util.Map;

import org.exist.collections.Collection;
import org.exist.storage.DBBroker;

/**
 *
 * @author aretter
 */
public abstract class AbstractTriggerProxy<T extends Trigger> implements TriggerProxy<T> {

    private final Class<? extends T> clazz;
    private Map<String, List<? extends Object>> parameters;
    
//    /**
//     * The database Collection URI of where the configuration for this Trigger came from
//     * typically somewhere under /db/system/config/db/
//     */
//    private final XmldbURI collectionConfigurationURI;

    public AbstractTriggerProxy(Class<? extends T> clazz) {
        this.clazz = clazz;
//        this.collectionConfigurationURI = collectionConfigurationURI;
    }
    
    public AbstractTriggerProxy(Class<? extends T> clazz, Map<String, List<? extends Object>> parameters) {
        this.clazz = clazz;
//        this.collectionConfigurationURI = collectionConfigurationURI;
        this.parameters = parameters;
    }

    protected Class<? extends T> getClazz() {
        return clazz;
    }
    
//    protected XmldbURI getCollectionConfigurationURI() {
//        return collectionConfigurationURI;
//    }
    
    @Override
    public void setParameters(Map<String, List<? extends Object>> parameters) {
        this.parameters = parameters;
    }
    
    protected Map<String, List<? extends Object>> getParameters() {
        return parameters;
    }
    
    public T newInstance(DBBroker broker, Collection collection) throws TriggerException {
        try {
            final T trigger = getClazz().newInstance();

            trigger.configure(broker, collection, getParameters());

            return trigger;
        } catch (final InstantiationException ie) {
            throw new TriggerException("Unable to instantiate Trigger '"  + getClazz().getName() + "': " + ie.getMessage(), ie);
        } catch (final IllegalAccessException iae) {
            throw new TriggerException("Unable to instantiate Trigger '"  + getClazz().getName() + "': " + iae.getMessage(), iae);
        }
    }
}