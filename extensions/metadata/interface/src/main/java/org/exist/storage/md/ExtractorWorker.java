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
package org.exist.storage.md;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import org.exist.collections.Collection;
import org.exist.dom.AttrImpl;
import org.exist.dom.BinaryDocument;
import org.exist.dom.CharacterDataImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.NodeList;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class ExtractorWorker implements IndexWorker {

	private ExtractorIndex index;

	private ExtractorConfig config = null;

	private int cachedNodesSize = 0;
	private int maxCachedNodesSize = 4096 * 1024;

	private DocumentImpl doc = null;
	private int mode = -1;

	private List<PendingDoc> nodesToWrite = null;

	private Stack<TextExtractor> contentStack = null;
	private Set<NodeId> nodesToRemove = null;

	public ExtractorWorker(ExtractorIndex index, DBBroker broker) {
		this.index = index;
	}

	@Override
	public String getIndexId() {
		return index.getIndexId();
	}

	@Override
	public String getIndexName() {
		return index.getIndexName();
	}

	@Override
	public Object configure(IndexController controller, NodeList configNodes,
			Map<String, String> namespaces)
			throws DatabaseConfigurationException {

		// LOG.debug("Configuring meta data extractor ...");
		config = new ExtractorConfig(configNodes, namespaces);
		return config;
	}

	@Override
	public void setDocument(DocumentImpl doc) {
		this.doc = doc;
	}

	@Override
	public void setDocument(DocumentImpl doc, int mode) {
		setDocument(doc);
		setMode(mode);
	}

	@Override
	public void setMode(int mode) {
		this.mode = mode;
        switch (mode) {
        case StreamListener.STORE:
            if (nodesToWrite == null)
                nodesToWrite = new ArrayList<PendingDoc>();
            else
                nodesToWrite.clear();
            cachedNodesSize = 0;
            break;
        case StreamListener.REMOVE_SOME_NODES:
            nodesToRemove = new TreeSet<NodeId>();
            break;
        }
	}

	@Override
	public DocumentImpl getDocument() {
		return doc;
	}

	@Override
	public int getMode() {
		return mode;
	}

	@Override
	public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean insert, boolean includeSelf) {
		return null;
	}

	@Override
	public StreamListener getListener() {
		return new StreamListener();
	}

	@Override
	public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
		return null;
	}

	@Override
	public void flush() {
        switch (mode) {
        case StreamListener.STORE:
            write();
            break;
        case StreamListener.REMOVE_ALL_NODES:
//            removeDocument(currentDoc.getDocId());
            break;
        case StreamListener.REMOVE_SOME_NODES:
//            removeNodes();
            break;
        case StreamListener.REMOVE_BINARY:
//        	removePlainTextIndexes();
        	break;
        }
	}

	@Override
	public void removeCollection(Collection collection, DBBroker broker, boolean reindex) throws PermissionDeniedException {
	}

	@Override
	public boolean checkIndex(DBBroker broker) {
		return true;
	}

	@Override
	public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map<?, ?> hints) {
		return null;
	}

	@Override
	public QueryRewriter getQueryRewriter(XQueryContext context) {
		return null;
	}

	protected void indexText(NodeId nodeId, QName qname, NodePath path, ExtractConfig config, CharSequence content) {
		
		PendingDoc pending = new PendingDoc(nodeId, qname, path, content, config);
		
		nodesToWrite.add(pending);
		
		cachedNodesSize += content.length();
		if (cachedNodesSize > maxCachedNodesSize)
			write();
	}
	
	private void write() {
        if (nodesToWrite == null || nodesToWrite.size() == 0)
            return;
        
        final Metas metas = MetaData.get().getMetas(doc);
        
        for (PendingDoc pending : nodesToWrite) {
        	
        	final String key = pending.idxConf.getKey();
        	final String value = pending.text.toString();
        	
        	metas.put(key, value);
        }
	}

	private class StreamListener extends AbstractStreamListener {

		@Override
		public void startElement(Txn transaction, ElementImpl element, NodePath path) {
			
			if (mode == STORE && config != null) {
				if (contentStack != null && !contentStack.isEmpty()) {
					for (TextExtractor extractor : contentStack) {
						extractor.startElement(element.getQName());
					}
				}
				Iterator<ExtractConfig> configIter = config.getConfig(path);
				if (configIter != null) {
					if (contentStack == null)
						contentStack = new Stack<TextExtractor>();
					while (configIter.hasNext()) {
						ExtractConfig configuration = configIter.next();
						if (configuration.match(path)) {
							TextExtractor extractor = new DefaultTextExtractor();
							extractor.configure(config, configuration);
							contentStack.push(extractor);
						}
					}
				}
			}
			super.startElement(transaction, element, path);
		}

		@Override
		public void endElement(Txn transaction, ElementImpl element, NodePath path) {
			if (config != null) {
				if (mode == STORE && contentStack != null
						&& !contentStack.isEmpty()) {
					for (TextExtractor extractor : contentStack) {
						extractor.endElement(element.getQName());
					}
				}
				Iterator<ExtractConfig> configIter = config.getConfig(path);
				if (mode != REMOVE_ALL_NODES && configIter != null) {
					if (mode == REMOVE_SOME_NODES) {
						nodesToRemove.add(element.getNodeId());
					} else {
						while (configIter.hasNext()) {
							ExtractConfig configuration = configIter.next();
							if (configuration.match(path)) {
								TextExtractor extractor = contentStack.pop();
								indexText(element.getNodeId(),
										element.getQName(), path,
										extractor.getIndexConfig(),
										extractor.getText());
							}
						}
					}
				}
			}
			super.endElement(transaction, element, path);
		}

		@Override
		public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
			path.addComponent(attrib.getQName());
			Iterator<ExtractConfig> configIter = null;
			if (config != null)
				configIter = config.getConfig(path);
			
			if (mode != REMOVE_ALL_NODES && configIter != null) {
				if (mode == REMOVE_SOME_NODES) {
					nodesToRemove.add(attrib.getNodeId());
				} else {
					while (configIter.hasNext()) {
						ExtractConfig configuration = configIter.next();
						if (configuration.match(path)) {
							indexText(attrib.getNodeId(), attrib.getQName(),
									path, configuration, attrib.getValue());
						}
					}
				}
			}
			path.removeLastComponent();
			super.attribute(transaction, attrib, path);
		}

		@Override
		public void characters(Txn transaction, CharacterDataImpl text, NodePath path) {
			if (contentStack != null && !contentStack.isEmpty()) {
				for (TextExtractor extractor : contentStack) {
					extractor.beforeCharacters();
					extractor.characters(text.getXMLString());
				}
			}
			super.characters(transaction, text, path);
		}

		@Override
		public IndexWorker getWorker() {
			return ExtractorWorker.this;
		}
	}

	private class PendingDoc {
//		NodeId nodeId;
		CharSequence text;
//		QName qname;
		ExtractConfig idxConf;

		private PendingDoc(NodeId nodeId, QName qname, NodePath path,
				CharSequence text, ExtractConfig idxConf) {
//			this.nodeId = nodeId;
//			this.qname = qname;
			this.text = text;
			this.idxConf = idxConf;
		}
	}

	@Override
	public void indexCollection(Collection col) {
	}

    @Override
    public void indexBinary(BinaryDocument doc) {
    }

    @Override
    public void removeIndex(XmldbURI url) {
    }
}
