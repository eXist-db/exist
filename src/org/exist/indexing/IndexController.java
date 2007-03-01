/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.indexing;

import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.dom.*;
import org.exist.util.DatabaseConfigurationException;
import org.exist.collections.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Internally used by the {@link DBBroker} to dispatch an operation to each of the
 * registered indexes.
 * 
 */
public class IndexController {

    protected Map indexWorkers = new HashMap();

    protected StreamListener listener = null;

    public IndexController(DBBroker broker) {
        IndexWorker[] workers = broker.getBrokerPool().getIndexManager().getWorkers();
        for (int i = 0; i < workers.length; i++) {
            indexWorkers.put(workers[i].getIndexId(), workers[i]);
        }
    }

    public IndexWorker getIndexWorker(String indexId) {
        return (IndexWorker) indexWorkers.get(indexId);
    }
    
    public StreamListener getStreamListener(DocumentImpl document, int mode) {
        if (listener != null)
            return listener;
        StreamListener first = null;
        StreamListener listener, previous = null;
        IndexWorker worker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            worker = (IndexWorker) i.next();
            listener = worker.getListener(mode, document);
            if (first == null) {
                first = listener;
            } else {
                previous.setNextInChain(listener);
            }
            previous = listener;
        }
        listener = first;
        return listener;
    }
    
    public Map configure(NodeList configNodes, Map namespaces) throws DatabaseConfigurationException {
        Map map = new HashMap();
        IndexWorker indexWorker;
        Object conf;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            conf = indexWorker.configure(configNodes, namespaces);
            if (conf != null)
                map.put(indexWorker.getIndexId(), conf);
        }
        return map;
    }

    public void flush() {
        IndexWorker indexWorker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            indexWorker.flush();
        }
    }

    public void removeCollection(Collection collection) {
        IndexWorker indexWorker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            indexWorker.removeCollection(collection);
        }
    }
}