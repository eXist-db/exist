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
import org.exist.dom.DocumentImpl;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Map;
import java.util.HashMap;

/**
 * Internally used by the {@link DBBroker} to dispatch an operation to each of the
 * registered indexes.
 * 
 */
public class IndexController {

    protected IndexWorker indexWorkers[];

    public IndexController(DBBroker broker) {
        indexWorkers = broker.getBrokerPool().getIndexManager().getWorkers();
    }

    public StreamListener getStreamListener(DocumentImpl document) {
        StreamListener first = null;
        StreamListener listener, previous = null;
        IndexWorker worker;
        for (int i = 0; i < indexWorkers.length; i++) {
            worker = indexWorkers[i];
            listener = worker.getListener(document);
            if (first == null) {
                first = listener;
            } else {
                previous.setNextInChain(listener);
            }
            previous = listener;
        }
        return first;
    }

    public Map configure(NodeList configNodes, Map namespaces) throws DatabaseConfigurationException {
        Map map = new HashMap();
        IndexWorker indexWorker;
        Object conf;
        for (int i = 0; i < indexWorkers.length; i++) {
            indexWorker = indexWorkers[i];
            conf = indexWorker.configure(configNodes, namespaces);
            if (conf != null)
                map.put(indexWorker.getIndexId(), conf);
        }
        return map;
    }

    public void flush() {
        IndexWorker indexWorker;
        for (int i = 0; i < indexWorkers.length; i++) {
            indexWorker = indexWorkers[i];
            indexWorker.flush();
        }
    }
}