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
import org.exist.storage.txn.Txn;

/**
 *
 * @author aretter
 */
public abstract class AbstractTriggerProxy<T extends Trigger> implements TriggerProxy<T> {

    private final Class<? extends T> clazz;
    private Map<String, List<? extends Object>> parameters;

    public AbstractTriggerProxy(final Class<? extends T> clazz) {
        this.clazz = clazz;
    }
    
    public AbstractTriggerProxy(final Class<? extends T> clazz, final Map<String, List<? extends Object>> parameters) {
        this.clazz = clazz;
        this.parameters = parameters;
    }

    @Override
    public Class<? extends T> getClazz() {
        return clazz;
    }
    
    @Override
    public void setParameters(final Map<String, List<? extends Object>> parameters) {
        this.parameters = parameters;
    }
    
    protected Map<String, List<? extends Object>> getParameters() {
        return parameters;
    }

    @Override
    public T newInstance(final DBBroker broker, final Txn transaction, final Collection collection) throws TriggerException {
        try {
            final T trigger = getClazz().newInstance();

            trigger.configure(broker, transaction, collection, getParameters());

            return trigger;
        } catch (final InstantiationException | IllegalAccessException ie) {
            throw new TriggerException("Unable to instantiate Trigger '"  + getClazz().getName() + "': " + ie.getMessage(), ie);
        }
    }
}
