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

import org.exist.storage.BrokerPool;
import org.exist.storage.NodePath;
import org.exist.storage.index.BFile;
import org.exist.storage.txn.Txn;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.exist.dom.ElementImpl;
import org.exist.dom.AttrImpl;
import org.exist.dom.TextImpl;
import org.exist.dom.DocumentImpl;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Map;

/**
 *
 */
public class ExampleIndexer implements Index {

    private final static Logger LOG = Logger.getLogger(ExampleWorker.class);

    private final static String INDEX_ID = ExampleIndexer.class.getName();

    private final static String INDEX_ELEMENT = "dummy";

    private BFile db;

    public void open(BrokerPool pool, String dataDir, Element config) throws DatabaseConfigurationException {
        String fileName = config.getAttribute("file");
        File file = new File(dataDir, fileName);
        try {
            db = new BFile(pool, (byte) 0, false, file, pool.getCacheManager(), 0.1, 0.1, 0.1);
        } catch (DBException e) {
            throw new DatabaseConfigurationException("Failed to create index file: " + file.getAbsolutePath() + ": " +
                e.getMessage());
        }
    }

    public void close() throws DBException {
        db.close();
    }

    public void sync() {
    }

    public IndexWorker getWorker() {
        return new ExampleWorker();
    }

    class DummyConfig {

        private String qname;

        public DummyConfig(String qn) { qname = qn; }

    }

    class ExampleWorker implements IndexWorker {

        public String getIndexId() {
            return INDEX_ID;
        }

        public Object configure(NodeList configNodes, Map namespaces) {
            for (int i = 0; i < configNodes.getLength(); i++) {
                Node node = configNodes.item(i);
                if(node.getNodeType() == Node.ELEMENT_NODE &&
                        INDEX_ELEMENT.equals(node.getLocalName())) {
                    String qn = ((Element)node).getAttribute("qname");
                    LOG.debug("Creating dummy index on " + qn);
                    return new DummyConfig(qn);
                }
            }
            return null;
        }

        public void flush() {
        }

        public StreamListener getListener(DocumentImpl document) {
            return new ExampleListener();
        }
    }

    class ExampleListener extends AbstractStreamListener {

        public ExampleListener() {
            super();
        }

        public void setNextInChain(StreamListener listener) {
            LOG.debug("Setting next listener: " + listener.getClass().getName());
            super.setNextInChain(listener);
        }

        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            LOG.debug("START ELEMENT: " + element.getQName());
            super.startElement(transaction, element, path);
        }

        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
            LOG.debug("ATTRIBUTE: " + attrib.getQName());
            super.attribute(transaction, attrib, path);
        }

        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            LOG.debug("END ELEMENT: " + element.getQName());
            super.endElement(transaction, element, path);
        }

        public void characters(Txn transaction, TextImpl text, NodePath path) {
            LOG.debug("TEXT: " + text.getData());
            super.characters(transaction, text, path);
        }
    }
}