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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.fulltext;

import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.indexing.QNamedKeysIndex;
import org.exist.indexing.StreamListener;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.FulltextIndexSpec;
import org.exist.storage.IndexSpec;
import org.exist.storage.NativeTextEngine;
import org.exist.storage.NodePath;
import org.exist.storage.TextSearchEngine;
import org.exist.storage.btree.DBException;
import org.exist.storage.txn.Txn;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Map;
import java.util.Stack;

/**
 * A legacy IndexWorker which wraps around {@link org.exist.storage.NativeTextEngine}. Right
 * now, the fulltext index has only partly been moved into the new modularized indexing architecture
 * and we thus need some glue classes to keep the old and new parts together. This class will become
 * part of the new fulltext indexing module.
 */
public class FTIndexWorker implements OrderedValuesIndex, QNamedKeysIndex {

    private NativeTextEngine engine;
    private FTIndex index;
    private DBBroker broker;
    private DocumentImpl document;
    private FulltextIndexSpec config;
    private int mode = StreamListener.UNKNOWN;

    private FTStreamListener listener = new FTStreamListener();
    private FTMatchListener matchListener = null;
    
    public FTIndexWorker(FTIndex index, DBBroker broker) throws DatabaseConfigurationException {
        this.index = index;
        this.broker = broker;
        try {
            this.engine = new NativeTextEngine(broker, index.getBFile(), broker.getConfiguration());
        } catch (final DBException e) {
            throw new DatabaseConfigurationException(e.getMessage(), e);
        }
    }

    public String getIndexId() {
        return FTIndex.ID;
    }

    public String getIndexName() {
        return "ft-index-old";
    }

    public TextSearchEngine getEngine() {
        return engine;
    }

    @Override
    public QueryRewriter getQueryRewriter(XQueryContext context) {
        return null;
    }

    public Object configure(IndexController controller, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        // Not implemented
        return null;
    }

    public void setDocument(DocumentImpl doc) {
        setDocument(doc, StreamListener.UNKNOWN);
    }

    public void setDocument(DocumentImpl doc, int newMode) {
        document = doc;
        mode = newMode;
        final IndexSpec indexConf = document.getCollection().getIndexConfiguration(broker);
        if (indexConf != null)
            {config = indexConf.getFulltextIndexSpec();}
        engine.setDocument(document);
    }

    public void setMode(int newMode) {
        mode = newMode;
        // wolf: unnecessary call to setDocument?
        // setDocument(document, newMode);
    }

    public DocumentImpl getDocument() {
        return document;
    }

    public int getMode() {
        return mode;
    }

    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean insert, boolean includeSelf) {
        if (node.getNodeType() == Node.ATTRIBUTE_NODE)
            {return null;}
        final IndexSpec indexConf = node.getDocument().getCollection().getIndexConfiguration(broker);
        if (indexConf != null) {
            final FulltextIndexSpec config = indexConf.getFulltextIndexSpec();
            if (config == null)
                {return null;}
            boolean reindexRequired = false;
            final int len = node.getNodeType() == Node.ELEMENT_NODE && !includeSelf ? path.length() - 1 : path.length();
            for (int i = 0; i < len; i++) {
                final QName qn = path.getComponent(i);
                if (config.hasQNameIndex(qn)) {
                    reindexRequired = true;
                    break;
                }
            }
            if (reindexRequired) {
                StoredNode topMost = null;
                StoredNode currentNode = node;
                while (currentNode != null) {
                    if (config.hasQNameIndex(currentNode.getQName()))
                        {topMost = currentNode;}
                    currentNode = currentNode.getParentStoredNode();
                }
                return topMost;
            }
        }
        return null;
    }

    public StreamListener getListener() {
        return listener;
    }

    public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
        boolean needToFilter = false;
        Match nextMatch = proxy.getMatches();
        while (nextMatch != null) {
            if (nextMatch.getIndexId() == FTIndex.ID) {
                needToFilter = true;
                break;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        if (!needToFilter)
            {return null;}
        if (matchListener == null)
            {matchListener = new FTMatchListener(broker, proxy);}
        else
            {matchListener.reset(broker, proxy);}
        return matchListener;
    }

    public void flush() {
        switch (mode) {
            case StreamListener.STORE :
                engine.flush();
                break;
            case StreamListener.REMOVE_ALL_NODES :
                engine.dropIndex(document);
                break;
            case StreamListener.REMOVE_SOME_NODES :
                engine.remove();
                break;
        }
    }

    public void removeCollection(Collection collection, DBBroker broker, boolean reindex) {
        engine.dropIndex(collection);
    }

    public boolean checkIndex(DBBroker broker) {
        // Not implemented
        return false;
    }

    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map hints) {
        // Not implemented
        return new Occurrences[0];
    }

    private class FTStreamListener extends AbstractStreamListener {

        private Stack<ElementContent> contentStack = new Stack<ElementContent>();

        public FTStreamListener() {
            //Nothing to do
        }

        @Override
        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null) {
                final boolean mixedContent = config.matchMixedElement(path);
                if (mixedContent || config.hasQNameIndex(element.getQName())) {
                    final ElementContent contentBuf =
                            new ElementContent(element.getQName(), mixedContent || config.preserveMixedContent(element.getQName()));
                    contentStack.push(contentBuf);
                }
            }
            super.startElement(transaction, element, path);
        }

        @Override
        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null) {
                final boolean mixedContent = config.matchMixedElement(path);
                if (mixedContent || config.hasQNameIndex(element.getQName())) {
                    final ElementContent contentBuf = contentStack.pop();
                    element.getQName().setNameType(ElementValue.ELEMENT);
                    engine.storeText(element, contentBuf,
                            mixedContent ? NativeTextEngine.FOURTH_OPTION : NativeTextEngine.TEXT_BY_QNAME,
                            null, mode == REMOVE_ALL_NODES);
                }
            }
            super.endElement(transaction, element, path);
        }

        /**
         *
         * @param transaction
         * @param text
         * @param path
         */
        @Override
        public void characters(Txn transaction, CharacterDataImpl text, NodePath path) {
            if (config == null) {
                engine.storeText(text, NativeTextEngine.TOKENIZE, config, mode == REMOVE_ALL_NODES);
            } else if (config.match(path)) {
                final int tokenize = config.preserveContent(path) ? NativeTextEngine.DO_NOT_TOKENIZE : NativeTextEngine.TOKENIZE;
                engine.storeText(text, tokenize, config, mode == REMOVE_ALL_NODES);
            }
            if (!contentStack.isEmpty()) {
                for (int i = 0; i < contentStack.size(); i++) {
                    final ElementContent next = contentStack.get(i);
                    next.append(text.getXMLString());
                }
            }
            super.characters(transaction, text, path);
        }

        @Override
        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
            path.addComponent(attrib.getQName());
            if (config == null || config.matchAttribute(path)) {
                engine.storeAttribute(attrib, null, NativeTextEngine.ATTRIBUTE_NOT_BY_QNAME, config, mode == REMOVE_ALL_NODES);
            }
            if (config != null && config.hasQNameIndex(attrib.getQName())){
                engine.storeAttribute(attrib, null, NativeTextEngine.ATTRIBUTE_BY_QNAME, config, mode == REMOVE_ALL_NODES);
            }
            path.removeLastComponent();
            super.attribute(transaction, attrib, path);
        }

        @Override
        public IndexWorker getWorker() {
            return FTIndexWorker.this;
        }
    }
}