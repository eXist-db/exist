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
import org.exist.storage.DBBroker;

/**
 *
 * @author aretter
 */
public abstract class AbstractTriggerProxies<T extends Trigger, P extends AbstractTriggerProxy<T>, D extends TriggersVisitor> implements TriggerProxies<P>{

    //extract signatures to interface
    
    private List<P> proxies = new ArrayList<P>();
    
    @Override
    public void add(P proxy) {
        proxies.add(proxy);
    }
    
    protected List<T> instantiateTriggers(DBBroker broker) throws TriggerException {
        
        final List<T> triggers = new ArrayList<T>(proxies.size());
        
        for(final P proxy : proxies) {
            triggers.add(proxy.newInstance(broker));
        }
        
        return triggers;
    }
    
    @Override
    public abstract D instantiateVisitor(DBBroker broker);
}
