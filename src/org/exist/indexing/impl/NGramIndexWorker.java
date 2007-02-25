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
package org.exist.indexing.impl;

import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.AttrImpl;
import org.exist.dom.TextImpl;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.StreamListener;
import org.exist.indexing.AbstractStreamListener;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLString;
import org.exist.storage.index.BFile;
import org.exist.storage.txn.Txn;
import org.exist.storage.NodePath;
import org.exist.storage.IndexSpec;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

import java.util.*;

/**
 */
public class NGramIndexWorker implements IndexWorker {

    private final static String ID = NGramIndex.class.getName();

    private final static String INDEX_ELEMENT = "ngram";
    private static final String QNAME_ATTR = "qname";

    private NGramIndex index;

    private char[] buf = new char[1024];
    private int currentChar = 0;

    private Map ngrams = new TreeMap();
    
    public NGramIndexWorker(NGramIndex index) {
        this.index = index;
    }

    public String getIndexId() {
        return ID;
    }

    public Object configure(NodeList configNodes, Map namespaces) throws DatabaseConfigurationException {
        // We use a map to store the QNames to be indexed
        Map map = new TreeMap();
        Node node;
        for(int i = 0; i < configNodes.getLength(); i++) {
            node = configNodes.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE &&
                    INDEX_ELEMENT.equals(node.getLocalName())) {
                String qname = ((Element)node).getAttribute(QNAME_ATTR);
                if (qname == null || qname.length() == 0)
                    throw new DatabaseConfigurationException("Configuration error: element " + node.getNodeName() +
	                		" must have an attribute " + QNAME_ATTR);
                NGramIndexConfig config = new NGramIndexConfig(namespaces, qname);
                map.put(config.getQName(), config);
            }
        }
        return map;
    }

    public void flush() {
        for (Iterator iterator = ngrams.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
//            System.out.println("NGram: " + entry.getKey().toString());
        }
        ngrams.clear();
    }

    public StreamListener getListener(DocumentImpl document) {
        return new NGramStreamListener(document);
    }

    private void indexText(XMLString text) {
        System.out.println(text);
        int len = text.length();
        int gramSize = index.getN();
        XMLString ngram;
        for (int i = 0; i < len; i++) {
            checkBuffer();
            for (int j = 0; j < gramSize && i + j < len; j++) {
                buf[currentChar + j] = text.charAt(i + j);
            }
            ngram = new XMLString(buf, currentChar, gramSize);
            System.out.println(ngram);
            if (!ngrams.containsKey(ngram))
                ngrams.put(ngram, null);
            currentChar += gramSize;
        }
    }

    private void checkBuffer() {
        if (currentChar + index.getN() > buf.length) {
            buf = new char[1024];
            Arrays.fill(buf, ' ');
            currentChar = 0;
        }
    }

    private class NGramStreamListener extends AbstractStreamListener {

        private Map config;
        private Stack contentStack = null;
        
        public NGramStreamListener(DocumentImpl document) {
            setDocument(document);
        }

        private void setDocument(DocumentImpl document) {
            IndexSpec indexConf = document.getCollection().getIndexConfiguration(document.getBroker());
            if (indexConf != null)
                config = (Map) indexConf.getCustomIndexSpec(ID);
        }

        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null && config.get(element.getQName()) != null) {
                if (contentStack == null) contentStack = new Stack();
                XMLString contentBuf = new XMLString();
                contentStack.push(contentBuf);
            }
            super.startElement(transaction, element, path);
        }

        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
            super.attribute(transaction, attrib, path);
        }

        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null && config.get(element.getQName()) != null) {
                XMLString content = (XMLString) contentStack.pop();
                indexText(content);
            }
            super.endElement(transaction, element, path);
        }

        public void characters(Txn transaction, TextImpl text, NodePath path) {
            if (contentStack != null && !contentStack.isEmpty()) {
                for (int i = 0; i < contentStack.size(); i++) {
                    XMLString next = (XMLString) contentStack.get(i);
                    next.append(text.getXMLString());
                }
            }
            super.characters(transaction, text, path);
        }
    }
}