/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.storage;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.dbxml.core.DBException;
import org.dbxml.core.data.Value;
import org.dbxml.core.filer.BTreeException;
import org.dbxml.core.filer.Paged;
import org.dbxml.core.indexer.IndexQuery;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.ArraySet;
import org.exist.dom.AttrImpl;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.NodeListImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.TextImpl;
import org.exist.dom.XMLUtil;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.serializers.NativeSerializer;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.store.BFile;
import org.exist.storage.store.CollectionStore;
import org.exist.storage.store.DOMFile;
import org.exist.storage.store.DOMFileIterator;
import org.exist.storage.store.DOMTransaction;
import org.exist.storage.store.NodeIterator;
import org.exist.storage.store.StorageAddress;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.Occurrences;
import org.exist.util.ReadOnlyException;
import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;
import org.exist.xquery.Constants;
import org.exist.xquery.NodeSelector;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  Main class for the native XML storage backend.
 * 
 * Provides access to all low-level operations required by
 * the database. Extends {@link DBBroker}.
 *
 *@author     Wolfgang Meier
 */
public class NativeBroker extends DBBroker {

	private static final String DATABASE_IS_READ_ONLY = "database is read-only";
	private static final String ROOT_COLLECTION = "/db";
	private static final String EXCEPTION_DURING_REINDEX = "exception during reindex";
	/** default buffer size setting */
	protected final static int BUFFERS = 256;

	/** check available memory after storing MEM_LIMIT_CHECK nodes */
	protected static int MEM_LIMIT_CHECK = 10000;

	protected CollectionStore collectionsDb = null;
	protected DOMFile domDb = null;
	protected NativeElementIndex elementIndex;
	protected ElementPool elementPool = new ElementPool(50);
	protected BFile elementsDb = null;
	protected NativeTextEngine textEngine;
	protected Serializer xmlSerializer;
	protected PatternCompiler compiler = new Perl5Compiler();
	protected PatternMatcher matcher = new Perl5Matcher();
	protected int defaultIndexDepth = 1;
	protected Map idxPathMap;
	protected boolean readOnly = false;
	protected int memMinFree;
	protected int nodesCount = 0;
	private final Runtime run = Runtime.getRuntime();

	public NativeBroker(BrokerPool pool, Configuration config) throws EXistException {
		super(pool, config);
		String dataDir;
		int buffers, pageSize, cacheSize;
		String temp;
		boolean compress = false;
		if ((dataDir = (String) config.getProperty("db-connection.data-dir")) == null)
			dataDir = "data";

		if ((pageSize = config.getInteger("db-connection.page-size")) < 0)
			pageSize = 4096;
		if ((buffers = config.getInteger("db-connection.buffers")) < 0)
			buffers = BUFFERS;
		if ((cacheSize = config.getInteger("db-connection.cache-size")) > 0) {
			long totalMem = cacheSize * 1024 * 1024;
			buffers = (int) (totalMem / pageSize / 64);
		}

		if ((defaultIndexDepth = config.getInteger("indexer.index-depth")) < 0)
			defaultIndexDepth = 1;
		if ((memMinFree = config.getInteger("db-connection.min_free_memory")) < 0)
			memMinFree = 5000000;
		Paged.setPageSize(pageSize);
		String pathSep = System.getProperty("file.separator", "/");
		int indexBuffers, dataBuffers;
		try {
			if ((elementsDb = (BFile) config.getProperty("db-connection.elements"))
				== null) {
				if ((indexBuffers = config.getInteger("db-connection.elements.buffers"))
					< 0) {
					indexBuffers = buffers * 4;
					dataBuffers = buffers * 11;
				} else
					dataBuffers = indexBuffers >> 2;

				LOG.debug(
					"elements index buffer size: " + indexBuffers + "; " + dataBuffers);
				elementsDb =
					new BFile(
						new File(dataDir + pathSep + "elements.dbx"),
						indexBuffers,
						dataBuffers);
				if (!elementsDb.exists()) {
					LOG.info("creating elements.dbx");
					elementsDb.create();
				} else
					elementsDb.open();

				config.setProperty("db-connection.elements", elementsDb);
				readOnly = elementsDb.isReadOnly();
			}

			if ((domDb = (DOMFile) config.getProperty("db-connection.dom")) == null) {
				if (config.hasProperty("db-connection.buffers")) {
					indexBuffers = buffers;
					dataBuffers = 512;
				} else {
					indexBuffers = buffers * 4;
					dataBuffers = buffers;
				}
				LOG.debug("page buffer size = " + indexBuffers + "; " + dataBuffers);
				domDb =
					new DOMFile(
						new File(dataDir + pathSep + "dom.dbx"),
						indexBuffers,
						dataBuffers);
				if (!domDb.exists()) {
					LOG.info("creating dom.dbx");
					domDb.create();
				} else
					domDb.open();

				config.setProperty("db-connection.dom", domDb);
				if (!readOnly)
					readOnly = domDb.isReadOnly();
			}

			if ((collectionsDb =
				(CollectionStore) config.getProperty("db-connection.collections"))
				== null) {
				if ((indexBuffers =
					config.getInteger("db-connection.collections.buffers"))
					< 0) {
					indexBuffers = buffers * 8;
					dataBuffers = buffers * 8;
				} else
					dataBuffers = indexBuffers;
				LOG.debug(
					"collections index buffer size: "
						+ indexBuffers
						+ "; "
						+ dataBuffers);
				collectionsDb =
					new CollectionStore(
						new File(dataDir + pathSep + "collections.dbx"),
						indexBuffers,
						dataBuffers);
				if (!collectionsDb.exists()) {
					LOG.info("creating collections.dbx");
					collectionsDb.create();
				} else
					collectionsDb.open();

				config.setProperty("db-connection.collections", collectionsDb);
				if (!readOnly)
					readOnly = collectionsDb.isReadOnly();
			}

			if (readOnly)
				LOG.info("database runs in read-only mode");
			idxPathMap = (Map) config.getProperty("indexer.map");
			textEngine = new NativeTextEngine(this, config, buffers);
			xmlSerializer = new NativeSerializer(this, config);
			elementIndex = new NativeElementIndex(this, config, elementsDb);
			user = new User("admin", null, "dba");
			getOrCreateCollection(ROOT_COLLECTION);
		} catch (Exception e) {
			LOG.debug(e);
			e.printStackTrace();
		}
	}

	protected final static String normalizeCollectionName(String name) {
		StringBuffer out = new StringBuffer();
		for (int i = 0; i < name.length(); i++)
			if (name.charAt(i) == '/'
				&& name.length() > i + 1
				&& name.charAt(i + 1) == '/')
				i++;
			else
				out.append(name.charAt(i));

		return out.toString();
	}

	public void addObserver(Observer o) {
		super.addObserver(o);
		textEngine.addObserver(o);
		elementIndex.addObserver(o);
	}

	private final boolean compare(String o1, String o2, int relation) {
		int cmp;
		if (!isCaseSensitive())
			cmp = o1.compareToIgnoreCase(o2);
		else
			cmp = o1.compareTo(o2);
		switch (relation) {
			case Constants.LT :
				return (cmp < 0);
			case Constants.LTEQ :
				return (cmp <= 0);
			case Constants.GT :
				return (cmp > 0);
			case Constants.GTEQ :
				return (cmp >= 0);
			case Constants.EQ :
				return (cmp == 0);
			case Constants.NEQ :
				return (cmp != 0);
		}
		return false;
		// never reached
	}

	public Occurrences[] scanIndexedElements(Collection collection, boolean inclusive)
		throws PermissionDeniedException {
		if (!collection.getPermissions().validate(user, Permission.READ))
			throw new PermissionDeniedException(
				"you don't have the permission"
					+ " to read collection "
					+ collection.getName());
		List collections =
			inclusive ? collection.getDescendants(this, user) : new ArrayList();
		collections.add(collection);
		TreeMap map = new TreeMap();
		VariableByteInputStream is;
		int docId;
		int len;
		// required for namespace lookups
		XQueryContext context = new XQueryContext(this);
		final Lock lock = elementsDb.getLock();
		for (Iterator i = collections.iterator(); i.hasNext();) {
			Collection current = (Collection) i.next();
			short collectionId = current.getId();

			ElementValue ref = new ElementValue(ElementValue.ELEMENT, collectionId);
			IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
			try {
				lock.acquire();
				ArrayList values = elementsDb.findEntries(query);
				for (Iterator j = values.iterator(); j.hasNext();) {
					Value val[] = (Value[]) j.next();
					short elementId = ByteConversion.byteToShort(val[0].getData(), 3);
					short nsSymbol = ByteConversion.byteToShort(val[0].getData(), 5);

					String name = getSymbols().getName(elementId);
					String namespace = nsSymbol == 0 ? "" : getSymbols().getNamespace(nsSymbol);
					QName qname = new QName(name, namespace);
					Occurrences oc = (Occurrences) map.get(qname);
					if (oc == null) {
						qname.setPrefix(context.getPrefixForURI(namespace));
						oc = new Occurrences(qname);
						map.put(qname, oc);
					}

					is =
						new VariableByteInputStream(
							val[1].data(),
							val[1].start(),
							val[1].length());
					try {
						while (is.available() > 0) {
							docId = is.readInt();
							len = is.readInt();
							oc.addOccurrences(len);
							is.skip(len * 4);
						}
					} catch (EOFException e) {
					} catch (IOException e) {
						LOG.warn("unexpected exception", e);
					}
				}
			} catch (BTreeException e) {
				LOG.warn("exception while reading element index", e);
			} catch (IOException e) {
				LOG.warn("exception while reading element index", e);
			} catch (LockException e) {
				LOG.warn("failed to acquire lock", e);
			} finally {
				lock.release();
			}
		}
		Occurrences[] result = new Occurrences[map.size()];
		return (Occurrences[]) map.values().toArray(result);
	}

	/**
	 *  Find elements by their tag name. This method is comparable to the DOM's
	 *  method call getElementsByTagName. All elements matching tagName and
	 *  belonging to one of the documents in the DocumentSet docs are returned.
	 *
	 *@param  docs     Description of the Parameter
	 *@param  tagName  Description of the Parameter
	 *@return
	 */
	public NodeSet findElementsByTagName(byte type, DocumentSet docs, QName qname,
		NodeSelector selector) {
//		final long start = System.currentTimeMillis();
		final ExtArrayNodeSet result = new ExtArrayNodeSet(docs.getLength(), 256);
		DocumentImpl doc;
		int docId;
		int len;
		short collectionId;
		long gid;
		VariableByteInputStream is;
		ElementValue ref;
		InputStream dis = null;
		short sym, nsSym;
		Collection collection;
		NodeProxy p;
		final short nodeType =
			(type == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE);
		final Lock lock = elementsDb.getLock();
		for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
			collection = (Collection) i.next();
			collectionId = collection.getId();
			if (type == ElementValue.ATTRIBUTE_ID) {
				ref = new ElementValue((byte) type, collectionId, qname.getLocalName());
			} else {
				sym = getSymbols().getSymbol(qname.getLocalName());
				nsSym = getSymbols().getNSSymbol(qname.getNamespaceURI());
				ref = new ElementValue((byte) type, collectionId, sym, nsSym);
			}
			boolean exceptionOcurred = false;
			try {
				lock.acquire(Lock.READ_LOCK);
				dis = elementsDb.getAsStream(ref);
			} catch (LockException e) {
				LOG.warn("failed to acquire lock", e);
				// jmv: dis = null;
				exceptionOcurred = true;
			} catch (IOException e) {
				LOG.warn("io exception while reading elements for " + qname, e);
				// jmv: dis = null;
				exceptionOcurred = true;
			} finally {
				lock.release();
			}
			// jmv: if (dis == null)
			// wolf: dis == null if no matching element has been found in the index
			if (dis == null || exceptionOcurred)
				continue;
			is = new VariableByteInputStream(dis);
			try {
				while (is.available() > 0) {
					docId = is.readInt();
					len = is.readInt();
					if ((doc = docs.getDoc(docId)) == null) {
						is.skip(len * 4);
						continue;
					}
					gid = 0;
					for (int k = 0; k < len; k++) {
						gid = gid + is.readLong();
						p = new NodeProxy(doc, gid, nodeType, StorageAddress.read(is));
						if(selector == null || selector.match(p))
							result.add(p, len);
					}
				}
			} catch (EOFException e) {
			} catch (IOException e) {
				LOG.warn("unexpected io error", e);
			}
		}
//		result.sort();
//				LOG.debug(
//					"found "
//						+ qname
//						+ ": "
//						+ result.getLength()
//						+ " in "
//						+ (System.currentTimeMillis() - start)
//						+ "ms.");
		return result;
	}

	public void flush() {
		textEngine.flush();
		elementIndex.flush();
		if (symbols != null && symbols.hasChanged())
			try {
				saveSymbols();
			} catch (EXistException e) {
				LOG.warn(e.getMessage(), e);
			}
		nodesCount = 0;
	}

	public void endRemove() {
		textEngine.remove();
		elementIndex.remove();
	}

	/**
	 *  get all the documents in this database repository. The documents are
	 *  returned as a DocumentSet.
	 *
	 *@param  user  Description of the Parameter
	 *@return       The allDocuments value
	 */
	public DocumentSet getAllDocuments(DocumentSet docs) {
		long start = System.currentTimeMillis();
		Collection root = getCollection(ROOT_COLLECTION);
		root.allDocs(this, docs, true);
		LOG.debug(
			"loading "
				+ docs.getLength()
				+ " documents from "
				+ docs.getCollectionCount()
				+ "collections took "
				+ (System.currentTimeMillis() - start)
				+ "ms.");
		return docs;
	}

	/**
	 *  Attributes are stored in the element-index (with a leading "@" in the
	 *  name). So simply call findElementsByTagName()
	 *
	 *@param  docs  Description of the Parameter
	 *@param  name  Description of the Parameter
	 *@return       The attributesByName value
	 */
	public NodeSet getAttributesByName(DocumentSet docs, QName qname) {
		qname.setLocalName(qname.getLocalName());
		NodeSet result = findElementsByTagName(ElementValue.ATTRIBUTE, docs, qname, null);
		return result;
	}

	public Collection getCollection(String name) {
		return getCollection(name, -1);
	}

	/**
	 *  get collection object. If the collection does not exist, null is
	 *  returned.
	 *
	 *@param  name  Description of the Parameter
	 *@return       The collection value
	 */
	public Collection getCollection(String name, long addr) {
		//	final long start = System.currentTimeMillis();
		name = normalizeCollectionName(name);
		if (name.length() > 0 && name.charAt(0) != '/')
			name = "/" + name;

		if (!name.startsWith(ROOT_COLLECTION))
			name = ROOT_COLLECTION + name;

		if (name.endsWith("/") && name.length() > 1)
			name = name.substring(0, name.length() - 1);
		Value key = null;
		if (addr == -1)
			try {
				key = new Value(name.getBytes("UTF-8"));
			} catch (UnsupportedEncodingException uee) {
				key = new Value(name.getBytes());
			}
		Collection collection = null;
		InputStream dis = null;
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.READ_LOCK);
			collection = collectionsDb.getCollectionCache().get(name);
			if (collection != null) {
				return collection;
			}
			collection = new Collection(collectionsDb, name);
			try {
				if (addr < 0) {
					dis = collectionsDb.getAsStream(key);
				} else {
					dis = collectionsDb.getAsStream(addr);
				}
			} catch (IOException ioe) {
				LOG.warn(ioe.getMessage(), ioe);
			}
			if (dis == null)
				return null;
			VariableByteInputStream istream = new VariableByteInputStream(dis);
			try {
				collection.read(this, istream);
			} catch (IOException ioe) {
				LOG.warn(ioe);
				return null;
			}
			collectionsDb.getCollectionCache().add(collection);
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections.dbx");
			return null;
		} finally {
			lock.release();
		}
		//			LOG.debug(
		//				"loading collection "
		//					+ name
		//					+ " took "
		//					+ (System.currentTimeMillis() - start)
		//					+ "ms.");
		return collection;
	}

	public Iterator getDOMIterator(NodeProxy proxy) {
		try {
			return new DOMFileIterator(this, domDb, proxy);
		} catch (BTreeException e) {
			LOG.debug("failed to create DOM iterator", e);
		} catch (IOException e) {
			LOG.debug("failed to create DOM iterator", e);
		}
		return null;
	}

	public Iterator getNodeIterator(NodeProxy proxy) {
		domDb.setOwnerObject(this);
		try {
			return new NodeIterator(this, domDb, proxy);
		} catch (BTreeException e) {
			LOG.debug("failed to create node iterator", e);
		} catch (IOException e) {
			LOG.debug("failed to create node iterator", e);
		}
		return null;
	}

	public int getDatabaseType() {
		return DBBroker.NATIVE;
	}

	/**
	 *  get a document by it's file name. The document's file name is used to
	 *  identify a document. File names are stored without the leading path.
	 *
	 *@param  fileName                       Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The document value
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public Document getDocument(String fileName) throws PermissionDeniedException {
		if (!fileName.startsWith("/"))
			fileName = '/' + fileName;

		int pos = fileName.lastIndexOf('/');
		String collName = (pos > 0) ? fileName.substring(0, pos) : "/";
		Collection collection = getCollection(collName);
		if (collection == null) {
			LOG.debug("collection " + collName + " not found!");
			return null;
		}
		if (!collection.getPermissions().validate(user, Permission.READ))
			throw new PermissionDeniedException("permission denied to read collection");
		DocumentImpl doc = collection.getDocument(fileName);
		if (doc == null) {
			LOG.debug("document " + fileName + " not found!");
			return null;
		}
		if (!doc.getPermissions().validate(user, Permission.READ))
			throw new PermissionDeniedException("not allowed to read document");
		return doc;
	}

	public DocumentSet getDocumentsByCollection(String collection, DocumentSet docs)
		throws PermissionDeniedException {
		return getDocumentsByCollection(collection, docs, true);
	}

	public DocumentSet getDocumentsByCollection(
		String collection,
		DocumentSet docs,
		boolean inclusive)
		throws PermissionDeniedException {
		long start = System.currentTimeMillis();
		if (collection == null || collection.length() == 0)
			return docs;
		if (collection.charAt(0) != '/')
			collection = "/" + collection;
		if (!collection.startsWith(ROOT_COLLECTION))
			collection = ROOT_COLLECTION + collection;
		Collection root = getCollection(collection);
		if (root == null) {
			LOG.debug("collection " + collection + " not found");
			return docs;
		}
		docs = root.allDocs(this, docs, inclusive);
		LOG.debug(
			"loading "
				+ docs.getLength()
				+ " documents from collection "
				+ collection
				+ " took "
				+ (System.currentTimeMillis() - start)
				+ "ms.");
		return docs;
	}

	/**
	 *  get all the documents in this database matching the given
	 *  document-type's name.
	 *
	 *@param  doctypeName  Description of the Parameter
	 *@param  user         Description of the Parameter
	 *@return              The documentsByDoctype value
	 */
	public DocumentSet getDocumentsByDoctype(String doctypeName, DocumentSet result) {
		DocumentSet docs = getAllDocuments(new DocumentSet());
		DocumentImpl doc;
		DocumentType doctype;
		for (Iterator i = docs.iterator(); i.hasNext();) {
			doc = (DocumentImpl) i.next();
			doctype = doc.getDoctype();
			if (doctype == null)
				continue;
			if (doctypeName.equals(doctype.getName())
				&& doc.getCollection().getPermissions().validate(user, Permission.READ)
				&& doc.getPermissions().validate(user, Permission.READ))
				result.add(doc);

		}
		return result;
	}

	protected void freeCollection(short id) throws ReadOnlyException {
//		LOG.debug("freeing collection " + id);
		Value key = new Value("__free_collection_id");
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			Value value = collectionsDb.get(key);
			if (value != null) {
				byte[] data = value.getData();
				byte[] ndata = new byte[data.length + 2];
				System.arraycopy(data, 0, ndata, 2, data.length);
				ByteConversion.shortToByte(id, ndata, 0);
				collectionsDb.put(key, ndata, true);
			} else {
				byte[] data = new byte[2];
				ByteConversion.shortToByte(id, data, 0);
				collectionsDb.put(key, data, true);
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
		} finally {
			lock.release();
		}
	}
	
	protected short getFreeCollectionId() throws ReadOnlyException {
		short freeCollectionId = -1;
		Value key = new Value("__free_collection_id");
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			Value value = collectionsDb.get(key);
			if (value != null) {
				byte[] data = value.getData();
				freeCollectionId = ByteConversion.byteToShort(data, data.length - 2);
//				LOG.debug("reusing collection id: " + freeCollectionId);
				if(data.length - 2 > 0) {
					byte[] ndata = new byte[data.length - 2];
					System.arraycopy(data, 0, ndata, 0, ndata.length);
					collectionsDb.put(key, ndata, true);
				} else
					collectionsDb.remove(key);
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
			return -1;
		} finally {
			lock.release();
		}
		return freeCollectionId;
	}
	
	protected short getNextCollectionId() throws ReadOnlyException {
		short nextCollectionId = getFreeCollectionId();
		if(nextCollectionId > -1)
			return nextCollectionId;
		Value key = new Value("__next_collection_id");
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			Value data = collectionsDb.get(key);
			if (data != null) {
				nextCollectionId = ByteConversion.byteToShort(data.getData(), 0);
				++nextCollectionId;
			}
			byte[] d = new byte[2];
			ByteConversion.shortToByte(nextCollectionId, d, 0);
			collectionsDb.put(key, d, true);
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
			return -1;
		} finally {
			lock.release();
		}
		return nextCollectionId;
	}

	protected void freeDocument(int id) throws ReadOnlyException {
//		LOG.debug("freeing document " + id);
		Value key = new Value("__free_doc_id");
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			Value value = collectionsDb.get(key);
			if (value != null) {
				byte[] data = value.getData();
				byte[] ndata = new byte[data.length + 4];
				System.arraycopy(data, 0, ndata, 4, data.length);
				ByteConversion.intToByte(id, ndata, 0);
				collectionsDb.put(key, ndata, true);
			} else {
				byte[] data = new byte[4];
				ByteConversion.intToByte(id, data, 0);
				collectionsDb.put(key, data, true);
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
		} finally {
			lock.release();
		}
	}
	
	protected int getFreeDocId() throws ReadOnlyException {
		int freeDocId = -1;
		Value key = new Value("__free_doc_id");
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			Value value = collectionsDb.get(key);
			if (value != null) {
				byte[] data = value.getData();
				freeDocId = ByteConversion.byteToInt(data, data.length - 4);
//				LOG.debug("reusing document id: " + freeDocId);
				if(data.length - 4 > 0) {
					byte[] ndata = new byte[data.length - 4];
					System.arraycopy(data, 0, ndata, 0, ndata.length);
					collectionsDb.put(key, ndata, true);
				} else
					collectionsDb.remove(key);
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
			return -1;
		} finally {
			lock.release();
		}
		return freeDocId;
	}
	
	public int getNextDocId(Collection collection) {
		int nextDocId;
		try {
			nextDocId = getFreeDocId();
		} catch (ReadOnlyException e1) {
			return 1;
		}
		if(nextDocId > -1)
			return nextDocId;
		else
			nextDocId = 1;
		
		Value key = new Value("__next_doc_id");
		Value data;
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			data = collectionsDb.get(key);
			if (data != null) {
				nextDocId = ByteConversion.byteToInt(data.getData(), 0);
				++nextDocId;
			}
			byte[] d = new byte[4];
			ByteConversion.intToByte(nextDocId, d, 0);
			try {
				collectionsDb.put(key, d, true);
			} catch (ReadOnlyException e) {
				LOG.debug("database read-only");
				return -1;
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
		} finally {
			lock.release();
		}
		return nextDocId;
	}

	public void index(final NodeImpl node) {
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final long gid = node.getGID();
		final short nodeType = node.getNodeType();
		final String nodeName = node.getNodeName();
		final long address = node.getInternalAddress();
		final IndexPaths idx =
			(IndexPaths) idxPathMap.get(node.getOwnerDocument().getDoctype().getName());
		if (address < 0)
			LOG.debug("node " + gid + ": internal address missing");
		final int depth = idx == null ? defaultIndexDepth : idx.getIndexDepth();
		final int level = doc.getTreeLevel(gid);
		NodeProxy tempProxy = new NodeProxy(doc, gid, address);
		QName qname;
		switch (nodeType) {
			case Node.ELEMENT_NODE :
				// save element by calling ElementIndex
				qname = node.getQName();
				qname.setNameType(ElementValue.ELEMENT);
				elementIndex.setDocument(doc);
				elementIndex.addRow(qname, tempProxy);
				break;
			case Node.ATTRIBUTE_NODE :
				elementIndex.setDocument(doc);
				qname =
					new QName(
						node.getLocalName(),
						node.getNamespaceURI(),
						node.getPrefix());
				qname.setNameType(ElementValue.ATTRIBUTE);
				elementIndex.addRow(qname, tempProxy);
				// check if attribute value should be fulltext-indexed
				// by calling IndexPaths.match(path) 
				if (idx == null || idx.getIncludeAttributes())
					textEngine.storeAttribute(idx, (AttrImpl) node);
				// if the attribute has type ID, store the ID-value
				// to the element index as well
				if (((AttrImpl) node).getType() == AttrImpl.ID) {
					qname = new QName(((AttrImpl) node).getValue(), "", null);
					qname.setNameType(ElementValue.ATTRIBUTE_ID);
					elementIndex.addRow(qname, tempProxy);
				}
				break;
			case Node.TEXT_NODE :
				// check if this textual content should be fulltext-indexed
				// by calling IndexPaths.match(path)
				// if (idx == null || idx.match(currentPath))
				textEngine.storeText(idx, (TextImpl) node);
				break;
		}
		if (nodeType == Node.ELEMENT_NODE && level <= depth) {
			new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
				public Object start() throws ReadOnlyException {
					try {
						domDb.addValue(
							new NodeRef(doc.getDocId(), gid),
							node.getInternalAddress());
					} catch (BTreeException e) {
						LOG.warn(EXCEPTION_DURING_REINDEX, e);
					} catch (IOException e) {
						LOG.warn(EXCEPTION_DURING_REINDEX, e);
					}
					return null;
				}
			}
			.run();
		}
	}

	public void reindex(DocumentImpl oldDoc, DocumentImpl doc, NodeImpl node) {
		int idxLevel = doc.reindexRequired();
		if (idxLevel < 0) {
			flush();
			return;
		}
		oldDoc.setReindexRequired(idxLevel);
		if (node == null)
			LOG.debug("reindexing level " + idxLevel + " of document " + doc.getDocId());
		final long start = System.currentTimeMillis();
		// remove old dom index
		Value ref = new NodeRef(doc.getDocId());
		final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
		final Lock lock = domDb.getLock();
		// try to acquire a lock on the file
		try {
			lock.acquire(Lock.WRITE_LOCK);
			domDb.setOwnerObject(this);
			final ArrayList nodes = domDb.findKeys(query);
			long gid;
			for (Iterator i = nodes.iterator(); i.hasNext();) {
				ref = (Value) i.next();
				gid = ByteConversion.byteToLong(ref.data(), 4);
				if (oldDoc.getTreeLevel(gid) >= doc.reindexRequired()) {
					if (node != null) {
						if (XMLUtil.isDescendant(oldDoc, node.getGID(), gid)) {
							domDb.removeValue(ref);
						}
					} else
						domDb.removeValue(ref);
				}
			}
		} catch (DBException e) {
			LOG.warn("db error during reindex", e);
		} catch (IOException e) {
			LOG.warn("io error during reindex", e);
		} catch (LockException e) {
			// timed out
			LOG.warn("lock timed out during reindex", e);
			return;
		} finally {
			lock.release();
		}
		// reindex the nodes
		Iterator iterator;
		if (node == null) {
			NodeList nodes = doc.getChildNodes();
			NodeImpl n;
			for (int i = 0; i < nodes.getLength(); i++) {
				n = (NodeImpl) nodes.item(i);
				iterator =
					getNodeIterator(
						new NodeProxy(doc, n.getGID(), n.getInternalAddress()));
				iterator.next();
				scanNodes(iterator, n, new StringBuffer());
			}
		} else {
			iterator =
				getNodeIterator(
					new NodeProxy(doc, node.getGID(), node.getInternalAddress()));
			iterator.next();
			scanNodes(iterator, node, node.getPath());
		}
		elementIndex.reindex(oldDoc, node);
		textEngine.reindex(oldDoc, node);
		doc.setReindexRequired(-1);
		LOG.debug("reindex took " + (System.currentTimeMillis() - start) + "ms.");
	}

	private void reindex(final NodeImpl node, StringBuffer currentPath) {
		if (node.getGID() < 0)
			LOG.debug("illegal node: " + node.getGID() + "; " + node.getNodeName());
		final IndexPaths idx =
			(IndexPaths) idxPathMap.get(node.getOwnerDocument().getDoctype().getName());
		final short nodeType = node.getNodeType();
		final long gid = node.getGID();
		final String nodeName = node.getNodeName();
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final byte data[] = node.serialize();
		final int depth = idx == null ? defaultIndexDepth : idx.getIndexDepth();
		final int level = doc.getTreeLevel(gid);
		if (level >= doc.reindexRequired()) {
			NodeIndexListener listener = doc.getIndexListener();
			// jmv if ((listener = doc.getIndexListener()) != null)
			if(listener != null)
				listener.nodeChanged(node);
			if (nodeType == Node.ELEMENT_NODE && level <= depth) {
				new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
					public Object start() throws ReadOnlyException {
						try {
							domDb.addValue(
								new NodeRef(doc.getDocId(), gid),
								node.getInternalAddress());
						} catch (BTreeException e) {
							LOG.warn(EXCEPTION_DURING_REINDEX, e);
						} catch (IOException e) {
							LOG.warn(EXCEPTION_DURING_REINDEX, e);
						}
						return null;
					}
				}
				.run();
			}
			final NodeProxy tempProxy =
				new NodeProxy(doc, gid, node.getInternalAddress());
			QName qname;
			switch (nodeType) {
				case Node.ELEMENT_NODE :
					// save element by calling ElementIndex
					qname = node.getQName();
					qname.setNameType(ElementValue.ELEMENT);
					tempProxy.setHasIndex(
						idx == null || idx.match(currentPath.toString()));
					elementIndex.setDocument(doc);
					elementIndex.addRow(qname, tempProxy);
					break;
				case Node.ATTRIBUTE_NODE :
					tempProxy.setHasIndex(
						idx == null || idx.match(currentPath.toString()));
					elementIndex.setDocument(doc);
					qname =
						new QName(
							node.getLocalName(),
							node.getNamespaceURI(),
							node.getPrefix());
					qname.setNameType(ElementValue.ATTRIBUTE);
					elementIndex.addRow(qname, tempProxy);
					// check if attribute value should be fulltext-indexed
					// by calling IndexPaths.match(path) 
					if (idx == null
						|| (idx.getIncludeAttributes()
							&& idx.match(currentPath + "/@" + nodeName)))
						textEngine.storeAttribute(idx, (AttrImpl) node);
					// if the attribute has type ID, store the ID-value
					// to the element index as well
					if (((AttrImpl) node).getType() == AttrImpl.ID) {
						qname = new QName("&" + ((AttrImpl) node).getValue(), "", null);
						qname.setNameType(ElementValue.ATTRIBUTE_ID);
						elementIndex.addRow(qname, tempProxy);
					}
					break;
				case Node.TEXT_NODE :
					// check if this textual content should be fulltext-indexed
					// by calling IndexPaths.match(path)
					if (idx == null || idx.match(currentPath.toString()))
						textEngine.storeText(idx, (TextImpl) node);
					break;
			}
		}
	}

	private void scanNodes(Iterator iterator, NodeImpl node, StringBuffer currentPath) {
		if (node.getNodeType() == Node.ELEMENT_NODE)
			currentPath.append('/').append(node.getNodeName());
		reindex(node, currentPath);
		if (node.hasChildNodes()) {
			final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
			final long firstChildId = XMLUtil.getFirstChildId(doc, node.getGID());
			if (firstChildId < 0) {
				LOG.fatal(
					"no child found: expected = "
						+ node.getChildCount()
						+ "; node = "
						+ node.getNodeName()
						+ "; gid = "
						+ node.getGID());
				throw new IllegalStateException("wrong node id");
			}
			final long lastChildId = firstChildId + node.getChildCount();
			// ong p;
			// Value value;
			NodeImpl child;
			for (long gid = firstChildId; gid < lastChildId; gid++) {
				child = (NodeImpl) iterator.next();
				child.setGID(gid);
				scanNodes(iterator, child, currentPath);
			}
		}
	}

	public String getNodeValue(final NodeProxy proxy) {
		return (String) new DOMTransaction(this, domDb, Lock.READ_LOCK) {
			public Object start() {
				return domDb.getNodeValue(proxy);
			}
		}
		.run();
	}

	/**
	 *  This method handles left or left-and-right truncated search terms. In
	 *  these cases it is not possible to use the cdata-index, since it contains
	 *  just the first 8 bytes of every cdata-string.
	 *
	 *@param  context   Description of the Parameter
	 *@param  docs      Description of the Parameter
	 *@param  relation  Description of the Parameter
	 *@param  expr      Description of the Parameter
	 *@return           The nodesEqualTo value
	 */
	public NodeSet getNodesEqualTo(
		NodeSet context,
		DocumentSet docs,
		int relation,
		String expr) {
		//		long start = System.currentTimeMillis();
		// NodeSet temp;
		int truncation = Constants.TRUNC_NONE;
		if (expr.length() > 0 && expr.charAt(0) == '%') {
			expr = expr.substring(1);
			truncation = Constants.TRUNC_LEFT;
		}
		if (expr.length() > 1 && expr.charAt(expr.length() - 1) == '%') {
			expr = expr.substring(0, expr.length() - 1);
			truncation =
				(truncation == Constants.TRUNC_LEFT)
					? Constants.TRUNC_BOTH
					: Constants.TRUNC_RIGHT;
		}
		if (!isCaseSensitive())
			expr = expr.toLowerCase();
		NodeSet result = scanSequential(context, docs, relation, truncation, expr);
		//				LOG.debug(
		//					"searching "
		//						+ result.getLength()
		//						+ " nodes took "
		//						+ (System.currentTimeMillis() - start)
		//						+ "ms.");
		return result;
	}

	/**
	 *  get collection object If the collection does not yet exists, it is
	 *  created automatically.
	 *
	 *@param  name                           the collection's name
	 *@param  user                           Description of the Parameter
	 *@return                                The orCreateCollection value
	 *@exception  PermissionDeniedException  Description of the Exception
	 *@author=@author
	 */
	public Collection getOrCreateCollection(String name)
		throws PermissionDeniedException {
		//		final long start = System.currentTimeMillis();
		name = normalizeCollectionName(name);
		if (name.length() > 0 && name.charAt(0) != '/')
			name = "/" + name;

		if (!name.startsWith(ROOT_COLLECTION))
			name = ROOT_COLLECTION + name;

		if (name.endsWith("/") && name.length() > 1)
			name = name.substring(0, name.length() - 1);

		Collection current = null;
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire();
			StringTokenizer tok = new StringTokenizer(name, "/");
			String temp = tok.nextToken();
			String path = ROOT_COLLECTION;
			Collection sub;
			current = getCollection(ROOT_COLLECTION);
			if (current == null) {
				LOG.debug("creating root collection /db");
				current = new Collection(collectionsDb, ROOT_COLLECTION);
				current.getPermissions().setPermissions(0777);
				current.getPermissions().setOwner(user);
				current.getPermissions().setGroup(user.getPrimaryGroup());
				current.setId(getNextCollectionId());
				current.setCreationTime(System.currentTimeMillis());
				saveCollection(current);
			}
			while (tok.hasMoreTokens()) {
				temp = tok.nextToken();
				path = path + "/" + temp;
				if (current.hasSubcollection(temp)) {
					current = getCollection(path);
				} else {
					if (!current.getPermissions().validate(user, Permission.WRITE)) {
						LOG.debug("permission denied to create collection " + path);
						throw new PermissionDeniedException("not allowed to write to collection");
					}
					LOG.debug("creating collection " + path);
					sub = new Collection(collectionsDb, path);
					sub.getPermissions().setOwner(user);
					sub.getPermissions().setGroup(user.getPrimaryGroup());
					sub.setId(getNextCollectionId());
					sub.setCreationTime(System.currentTimeMillis());
					current.addCollection(sub);
					saveCollection(current);
					current = sub;
				}
			}
		} catch (ReadOnlyException e) {
			LOG.debug("database read-only");
			return null;
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store");
		} finally {
			lock.release();
		}
		//			LOG.debug("getOrCreateCollection took " + 
		//				(System.currentTimeMillis() - start) + "ms.");
		return current;
	}

	/**
	 *  Gets a range of nodes, starting with first, ending with last
	 *
	 *@param  doc    the document
	 *@param  first  node-id of the first node
	 *@param  last   node-id of the last node
	 *@return        a list of nodes
	 */
	public NodeList getRange(final Document doc, final long first, final long last) {
		NodeListImpl result = new NodeListImpl((int) (last - first));
		for (long gid = first; gid <= last; gid++) {
			result.add(objectWith(doc, gid));
		}
		return result;
	}

	public Serializer getSerializer() {
		xmlSerializer.reset();
		return xmlSerializer;
	}

	public TextSearchEngine getTextEngine() {
		return textEngine;
	}

	public Serializer newSerializer() {
		return new NativeSerializer(this, null);
	}

	public Node objectWith(final Document doc, final long gid) {
		return (Node) new DOMTransaction(this, domDb) {
			public Object start() {
				Value val = domDb.get(new NodeProxy((DocumentImpl) doc, gid));
				if (val == null) {
					LOG.debug("node " + gid + " not found!");
					//throw new RuntimeException("node " + gid + " not found");
					return null;
				}
				NodeImpl node =
					NodeImpl.deserialize(
						val.getData(),
						0,
						val.getLength(),
						(DocumentImpl) doc);
				node.setGID(gid);
				node.setOwnerDocument(doc);
				node.setInternalAddress(val.getAddress());
				return node;
			}
		}
		.run();
	}

	public Node objectWith(final NodeProxy p) {
		if (p.getInternalAddress() < 0)
			return objectWith(p.doc, p.gid);
		return (Node) new DOMTransaction(this, domDb) {
			public Object start() {
				Value val = domDb.get(p.getInternalAddress());
				if (val == null) {
					LOG.debug("node " + p.gid + " not found!");
					Thread.dumpStack();
					return null;
				}
				NodeImpl node =
					NodeImpl.deserialize(
						val.getData(),
						0,
						val.getLength(),
						(DocumentImpl) p.doc);
				node.setGID(p.gid);
				node.setOwnerDocument(p.doc);
				node.setInternalAddress(p.getInternalAddress());
				return node;
			}
		}
		.run();
	}

	public boolean removeCollection(String name) throws PermissionDeniedException {
		if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		try {
			if (!name.startsWith(ROOT_COLLECTION))
				name = ROOT_COLLECTION + name;

			Collection collection = getCollection(name);
			if (collection == null) {
				LOG.debug("collection " + name + " not found!");
				return false;
			}
			if (!collection.getPermissions().validate(user, Permission.WRITE))
				throw new PermissionDeniedException("not allowed to remove collection");

			String childCollection;
			LOG.debug("removing sub-collections");
			for (Iterator i = collection.collectionIterator(); i.hasNext();) {
				childCollection = (String) i.next();
				removeCollection(
					(name.equals("/")
						? "/" + childCollection
						: name + "/" + childCollection));
			}
			Lock lock = collectionsDb.getLock();
			try {
				lock.acquire(Lock.WRITE_LOCK);
				
				// if this is not the root collection remove it completely
				if (name.equals(ROOT_COLLECTION))
					saveCollection(collection);
				else {
					Value key;
					try {
						key = new Value(name.getBytes("UTF-8"));
					} catch (UnsupportedEncodingException uee) {
						key = new Value(name.getBytes());
					}	
					collectionsDb.remove(key);
				}
				if (!name.equals(ROOT_COLLECTION))
					collectionsDb.getCollectionCache().remove(collection);
				Collection parent = collection.getParent(this);
				if (parent != null) {
					parent.removeCollection(name.substring(name.lastIndexOf("/") + 1));
					saveCollection(parent);
				}
			} catch (LockException e) {
				LOG.warn("Failed to acquire lock on collections.dbx");
			} finally {
				lock.release();
			}
			freeCollection(collection.getId());
			
			((NativeTextEngine) textEngine).removeCollection(collection);

			LOG.debug("removing elements ...");
			short collectionId = collection.getId();

			Value ref = new ElementValue(collectionId);
			IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
			lock = elementsDb.getLock();
			try {
				lock.acquire(Lock.WRITE_LOCK);
				elementsDb.removeAll(query);
			} catch (LockException e) {
				LOG.error("could not acquire lock on elements index", e);
			} finally {
				lock.release();
			}
			elementPool.clear();

			LOG.debug("removing dom nodes ...");
			for (Iterator i = collection.iterator(); i.hasNext();) {
				final DocumentImpl doc = (DocumentImpl) i.next();
				LOG.debug("removing document " + doc.getFileName());
				new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
					public Object start() {
						if(doc.getResourceType() == DocumentImpl.BINARY_FILE) {
							domDb.remove(doc.getAddress());
							domDb.removeOverflowValue(((BinaryDocument)doc).getPage());
						} else {
							NodeImpl node = (NodeImpl)doc.getFirstChild();
							Iterator k =
								getDOMIterator(
									new NodeProxy(
										doc,
										node.getGID(),
										node.getInternalAddress()));
							while(k.hasNext()) {
								k.next();
								k.remove();
							}
						}
						return null;
					}
				}
				.run();
				new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
					public Object start() {
						try {
							Value ref = new NodeRef(doc.getDocId());
							IndexQuery query =
								new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
							domDb.remove(query, null);
							domDb.flush();
						} catch (BTreeException e) {
							LOG.warn("btree error while removing document", e);
						} catch (DBException e) {
							LOG.warn("db error while removing document", e);
						} catch (IOException e) {
							LOG.warn("io error while removing document", e);
						}
						return null;
					}
				}
				.run();
				freeDocument(doc.getDocId());
			}
			return true;
		} catch (IOException ioe) {
			LOG.warn(ioe);
		} catch (BTreeException bte) {
			LOG.warn(bte);
		} catch (ReadOnlyException e) {
			LOG.warn(DATABASE_IS_READ_ONLY);
		}
		return false;
	}

	public void removeDocument(String docName) throws PermissionDeniedException {
		if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		try {
			if (!docName.startsWith("/"))
				docName = '/' + docName;
			final DocumentImpl doc = (DocumentImpl) getDocument(docName);
			if (doc == null) {
				LOG.debug("document " + docName + " not found");
				return;
			}
			LOG.info("removing document " + doc.getDocId() + " ...");

			// drop element-index
			short collectionId = doc.getCollection().getId();
			Value ref = new ElementValue(collectionId);
			IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
			Lock lock = elementsDb.getLock();
			try {
				lock.acquire(Lock.WRITE_LOCK);
				ArrayList elements = elementsDb.findKeys(query);
				LOG.debug("found " + elements.size() + " elements.");

				Value key;
				Value value;
				byte[] data;
				// byte[] ndata;
				VariableByteInputStream is;
				VariableByteOutputStream os;
				int len;
				int docId;
				long delta;
				long address;
				boolean changed;
				for (int i = 0; i < elements.size(); i++) {
					key = (Value) elements.get(i);
					value = elementsDb.get(key);
					data = value.getData();
					is = new VariableByteInputStream(data);
					os = new VariableByteOutputStream();
					changed = false;
					try {
						while (is.available() > 0) {
							docId = is.readInt();
							len = is.readInt();
							if (docId != doc.getDocId()) {
								// copy data to new buffer
								os.writeInt(docId);
								os.writeInt(len);
								for (int j = 0; j < len; j++) {
									delta = is.readLong();
									address = StorageAddress.read(is);
									os.writeLong(delta);
									StorageAddress.write(address, os);
								}
							} else {
								changed = true;
								// skip
								is.skip(len * 4);
							}
						}
					} catch (EOFException e) {
						LOG.debug("eof: " + is.available());
					}
					if (changed) {
						//ndata = os.toByteArray();
						if (elementsDb.put(key, os.data()) < 0)
							LOG.debug("could not save element");
					}
				}
			} catch (LockException e) {
				LOG.warn("could not acquire lock on elements", e);
			} finally {
				lock.release();
			}
			elementPool.clear();

			((NativeTextEngine) textEngine).removeDocument(doc);
			LOG.debug("removing dom");
			new DOMTransaction(this, domDb) {
				public Object start() {
					NodeList children = doc.getChildNodes();
					NodeImpl node;
					for (int i = 0; i < children.getLength(); i++) {
						node = (NodeImpl) children.item(i);
						Iterator j =
							getDOMIterator(
								new NodeProxy(
									doc,
									node.getGID(),
									node.getInternalAddress()));
						removeNodes(j);
					}
					domDb.remove(doc.getAddress());
					return null;
				}
			}
			.run();
			
			ref = new NodeRef(doc.getDocId());
			final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
			new DOMTransaction(this, domDb) {
				public Object start() {
					try {
						domDb.remove(idx, null);
						domDb.flush();
					} catch (BTreeException e) {
						LOG.warn("error while removing doc", e);
					} catch (DBException e) {
						LOG.warn("error while removing doc", e);
					} catch (IOException e) {
						LOG.warn("error while removing doc", e);
					}
					return null;
				}
			}
			.run();
			freeDocument(doc.getDocId());
			LOG.info("removed document.");
		} catch (IOException ioe) {
			ioe.printStackTrace();
			LOG.warn(ioe);
		} catch (BTreeException bte) {
			bte.printStackTrace();
			LOG.warn(bte);
		} catch (ReadOnlyException e) {
			LOG.warn(DATABASE_IS_READ_ONLY);
		}
	}

	private void removeNodes(Iterator domIterator) {
		final Value next = (Value) domIterator.next();
		if (next == null)
			return;
		final byte[] data = next.data();
		final short type = Signatures.getType(data[next.start()]);
		switch (type) {
			case Node.ELEMENT_NODE :
				int children = ByteConversion.byteToInt(data, next.start() + 1);
				domIterator.remove();
				for (int i = 0; i < children; i++)
					removeNodes(domIterator);
				break;
			default :
				domIterator.remove();
		}
	}

	public void removeNode(final NodeImpl node, String currentPath) {
		final IndexPaths idx =
			(IndexPaths) idxPathMap.get(node.getOwnerDocument().getDoctype().getName());
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final long gid = node.getGID();
		final short nodeType = node.getNodeType();
		final String nodeName = node.getNodeName();
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() {
				final long address = node.getInternalAddress();
				if (address > -1)
					domDb.remove(new NodeRef(doc.getDocId(), node.getGID()), address);
				else
					domDb.remove(new NodeRef(doc.getDocId(), node.getGID()));
				return null;
			}
		}
		.run();
		final NodeProxy tempProxy = new NodeProxy(doc, gid, node.getInternalAddress());
		QName qname;
		switch (nodeType) {
			case Node.ELEMENT_NODE :
				// save element by calling ElementIndex
				qname = node.getQName();
				qname.setNameType(ElementValue.ELEMENT);
				elementIndex.setDocument(doc);
				elementIndex.addRow(qname, tempProxy);
				break;
			case Node.ATTRIBUTE_NODE :
				elementIndex.setDocument(doc);
				qname =
					new QName(
						node.getLocalName(),
						node.getNamespaceURI(),
						node.getPrefix());
				qname.setNameType(ElementValue.ATTRIBUTE);
				elementIndex.addRow(qname, tempProxy);
				// check if attribute value should be fulltext-indexed
				// by calling IndexPaths.match(path) 
				if (idx == null || idx.getIncludeAttributes())
					textEngine.storeAttribute(idx, (AttrImpl) node);
				// if the attribute has type ID, store the ID-value
				// to the element index as well
				if (((AttrImpl) node).getType() == AttrImpl.ID) {
					qname = new QName(((AttrImpl) node).getValue(), "", null);
					qname.setNameType(ElementValue.ATTRIBUTE_ID);
					elementIndex.addRow(qname, tempProxy);
				}
				break;
			case Node.TEXT_NODE :
				// check if this textual content should be fulltext-indexed
				// by calling IndexPaths.match(path)
				// if (idx == null || idx.match(currentPath))
				textEngine.storeText(idx, (TextImpl) node);
				break;
		}
	}

	public void addDocument(Collection collection, DocumentImpl doc)
		throws PermissionDeniedException {
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire();

			Value name;
			try {
				name = new Value(collection.getName().getBytes("UTF-8"));
			} catch (UnsupportedEncodingException uee) {
				LOG.debug(uee);
				name = new Value(collection.getName().getBytes());
			}
			storeDocument(doc);
			VariableByteOutputStream ostream = new VariableByteOutputStream(6);
			doc.write(ostream);
			long address = collectionsDb.append(name, ostream.data());
			if (address < 0) {
				LOG.debug("could not store collection data for " + collection.getName());
				return;
			}
			collection.setAddress(address);
			if (!name.equals(ROOT_COLLECTION)) {
				Collection parent = collection.getParent(this);
				parent.update(collection);
				saveCollection(parent);
			}
			ostream.close();
		} catch (IOException ioe) {
			LOG.debug(ioe);
		} catch (ReadOnlyException e) {
			LOG.warn(DATABASE_IS_READ_ONLY);
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections.dbx");
		} finally {
			lock.release();
		}
	}

	public void saveCollection(Collection collection) throws PermissionDeniedException {
		if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		Lock lock = null;
		try {
			lock = collectionsDb.getLock();
			lock.acquire(Lock.WRITE_LOCK);

			if (collection.getId() < 0)
				collection.setId(getNextCollectionId());

			Value name;
			try {
				name = new Value(collection.getName().getBytes("UTF-8"));
			} catch (UnsupportedEncodingException uee) {
				LOG.debug(uee);
				name = new Value(collection.getName().getBytes());
			}
			try {
				final VariableByteOutputStream ostream = new VariableByteOutputStream(8);
				collection.write(this, ostream);
				final long addr = collectionsDb.put(name, ostream.data());
				if (addr < 0) {
					LOG.debug(
						"could not store collection data for " + collection.getName());
					return;
				}
				collection.setAddress(addr);
				if (!name.equals(ROOT_COLLECTION)) {
					Collection parent = collection.getParent(this);
					parent.update(collection);
					saveCollection(parent);
				}
				collectionsDb.getCollectionCache().add(collection);
				ostream.close();
			} catch (IOException ioe) {
				LOG.debug(ioe);
			}
		} catch (ReadOnlyException e) {
			LOG.warn(DATABASE_IS_READ_ONLY);
		} catch (LockException e) {
			LOG.warn("could not acquire lock for collections store", e);
		} finally {
			lock.release();
		}
	}

	/**
	 *  Do a sequential search through the DOM-file.
	 *
	 *@param  context     Description of the Parameter
	 *@param  doc         Description of the Parameter
	 *@param  relation    Description of the Parameter
	 *@param  truncation  Description of the Parameter
	 *@param  expr        Description of the Parameter
	 *@return             Description of the Return Value
	 */
	protected NodeSet scanSequential(
		NodeSet context,
		DocumentSet doc,
		int relation,
		int truncation,
		String expr) {
		ArraySet resultNodeSet = new ArraySet(context.getLength());
		NodeProxy p;
		String content;
		// StringBuffer buf = new StringBuffer(128);
		// byte[] data;
		// long filePos;
		// int offset;
		// NodeRef nodeRef;
		String cmp;
		// Iterator domIterator = null;
		Pattern regexp = null;
		if (relation == Constants.REGEXP)
			try {
				regexp =
					compiler.compile(
						expr.toLowerCase(),
						Perl5Compiler.CASE_INSENSITIVE_MASK);
				truncation = Constants.REGEXP;
			} catch (MalformedPatternException e) {
				LOG.debug(e);
			}
		for (Iterator i = context.iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			try {
				domDb.setOwnerObject(this);
				domDb.getLock().acquire(Lock.READ_LOCK);
				content = domDb.getNodeValue(p);
			} catch (LockException e) {
				LOG.warn("failed to acquire read lock on dom.dbx");
				continue;
			} finally {
				domDb.getLock().release();
			}
			if (isCaseSensitive())
				cmp = content;
			else {
				cmp = content.toLowerCase();
			}
			//System.out.println("context = " + p.gid + "; context-length = " + 
			//	(p.getContext() == null ? -1 : p.getContext().getSize()));
			switch (truncation) {
				case Constants.TRUNC_LEFT :
					if (cmp.endsWith(expr))
						resultNodeSet.add(p);
					break;
				case Constants.TRUNC_RIGHT :
					if (cmp.startsWith(expr))
						resultNodeSet.add(p);
					break;
				case Constants.TRUNC_BOTH :
					if (-1 < cmp.indexOf(expr))
						resultNodeSet.add(p);
					break;
				case Constants.TRUNC_NONE :
					if (compare(cmp, expr, relation))
						resultNodeSet.add(p);
					break;
				case Constants.REGEXP :
					if (regexp != null && matcher.contains(cmp, regexp)) {
						resultNodeSet.add(p);
					}
					break;
			}
		}
		return resultNodeSet;
	}

	public void shutdown() {
		super.shutdown();
		try {
			flush();
			sync();
			textEngine.close();
			domDb.close();
			elementsDb.close();
			collectionsDb.close();
		} catch (Exception e) {
			LOG.debug(e);
			e.printStackTrace();
		}
	}

	/**
	 *  Store a node into the database. This method is called by the parser to
	 *  write a node to the storage backend.
	 *
	 *@param  node         the node to be stored
	 *@param  currentPath  path expression which points to this node's
	 *      element-parent or to itself if it is an element (currently used by
	 *      the Broker to determine if a node's content should be
	 *      fulltext-indexed).
	 */
	public void store(final NodeImpl node, CharSequence currentPath) {
		// first, check available memory
		if (nodesCount > MEM_LIMIT_CHECK) {
			final int percent = (int) (run.freeMemory() / (run.totalMemory() / 100));
			if (percent < memMinFree) {
				LOG.info(
					"total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
				flush();
				System.gc();
				LOG.info(
					"total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
			}
		}
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final IndexPaths idx = (IndexPaths) idxPathMap.get(doc.getDoctype().getName());
		final long gid = node.getGID();
		if (gid < 0) {
			LOG.debug("illegal node: " + gid + "; " + node.getNodeName());
			Thread.dumpStack();
			return;
		}
		final short nodeType = node.getNodeType();
		final String nodeName = node.getNodeName();
		// final String localName = node.getLocalName();
		final int depth = idx == null ? defaultIndexDepth : idx.getIndexDepth();
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				long address = -1;
				final byte data[] = node.serialize();
				if (nodeType == Node.TEXT_NODE
					|| nodeType == Node.ATTRIBUTE_NODE
					|| doc.getTreeLevel(gid) > depth)
					address = domDb.add(data);
				else {
					address = domDb.put(new NodeRef(doc.getDocId(), gid), data);
				}
				if (address < 0)
					LOG.warn("address is missing");
				node.setInternalAddress(address);
				ByteArrayPool.releaseByteArray(data);
				return null;
			}
		}
		.run();
		++nodesCount;
		NodeProxy tempProxy = null;
		QName qname;
		switch (nodeType) {
			case Node.ELEMENT_NODE :
				tempProxy = new NodeProxy(doc, gid, node.getInternalAddress());
				tempProxy.setHasIndex(idx == null || idx.match(currentPath));
				// save element by calling ElementIndex
				elementIndex.setDocument(doc);
				elementIndex.addRow(node.getQName(), tempProxy);
				break;
			case Node.ATTRIBUTE_NODE :
				tempProxy = new NodeProxy(doc, gid, node.getInternalAddress());
				tempProxy.setHasIndex(idx == null || idx.match(currentPath));
				qname =
					new QName(
						node.getLocalName(),
						node.getNamespaceURI(),
						node.getPrefix());
				qname.setNameType(ElementValue.ATTRIBUTE);
				elementIndex.setDocument(doc);
				elementIndex.addRow(qname, tempProxy);
				// check if attribute value should be fulltext-indexed
				// by calling IndexPaths.match(path) 
				if (idx == null
					|| (idx.getIncludeAttributes()
						&& idx.match(currentPath + "/@" + nodeName)))
					textEngine.storeAttribute(idx, (AttrImpl) node);
				// if the attribute has type ID, store the ID-value
				// to the element index as well
				if (((AttrImpl) node).getType() == AttrImpl.ID) {
					qname = new QName(((AttrImpl) node).getValue(), "", null);
					//LOG.debug("found ID: " + qname.getLocalName());
					qname.setNameType(ElementValue.ATTRIBUTE_ID);
					elementIndex.addRow(qname, tempProxy);
				}
				break;
			case Node.TEXT_NODE :
				// check if this textual content should be fulltext-indexed
				// by calling IndexPaths.match(path)
				if (idx == null || idx.match(currentPath))
					textEngine.storeText(idx, (TextImpl) node);
				break;
		}
	}

	public void storeDocument(final DocumentImpl doc) {
		final byte data[] = doc.serialize();
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				if (doc.getAddress() > -1) {
					domDb.remove(doc.getAddress());
				}
				doc.setAddress(domDb.add(data));
				return null;
			}
		}
		.run();
	}

	public void storeBinaryResource(final BinaryDocument blob, final byte[] data) {
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
//				if (blob.getPage() > -1) {
//					domDb.remove(blob.getPage());
//				}
				LOG.debug("Storing binary resource " + blob.getFileName());
				blob.setPage(domDb.addBinary(data));
				return null;
			}
		}
		.run();
	}

	public byte[] getBinaryResourceData(final BinaryDocument blob) {
		byte[] data = (byte[]) new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				return domDb.getBinary(blob.getPage());
			}
		}
		.run();
		return data;
	}

	public void removeBinaryResource(final BinaryDocument blob)
		throws PermissionDeniedException {
		if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		LOG.info("removing binary resource " + blob.getDocId() + "...");
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				domDb.remove(blob.getAddress());
				domDb.removeOverflowValue(blob.getPage());
				return null;
			}
		}
		.run();
	}

	public void readDocumentMetadata(final DocumentImpl doc) {
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				final Value val = domDb.get(doc.getAddress());
				doc.deserialize(val.getData());
				return null;
			}
		}
		.run();

	}

	public void sync() {
		// uncomment this to get statistics on page buffer usage
		elementsDb.printStatistics();
		collectionsDb.printStatistics();
		domDb.printStatistics();
		try {
			Lock lock = collectionsDb.getLock();
			try {
				lock.acquire(Lock.WRITE_LOCK);
				collectionsDb.flush();
			} catch (LockException e) {
				LOG.warn("failed to acquire lock on collections store", e);
			} finally {
				lock.release();
			}
			new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
				public Object start() {
					try {
						domDb.flush();
					} catch (DBException e) {
						LOG.warn("error while flushing dom.dbx", e);
					}
					return null;
				}
			}
			.run();
			elementIndex.sync();
			textEngine.sync();
		} catch (DBException dbe) {
			dbe.printStackTrace();
			LOG.debug(dbe);
		}
		System.gc();
	}

	public void closeDocument() {
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() {
				domDb.closeDocument();
				return null;
			}
		}
		.run();
	}

	public void update(final NodeImpl node) {
		try {
			final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
			final long internalAddress = node.getInternalAddress();
			final byte[] data = node.serialize();
			new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
				public Object start() throws ReadOnlyException {
					final NodeRef ref = new NodeRef(doc.getDocId(), node.getGID());
					if (-1 < internalAddress)
						domDb.update(ref, internalAddress, data);
					else
						domDb.update(ref, data);
					return null;
				}
			}
			.run();
			ByteArrayPool.releaseByteArray(data);
		} catch (Exception e) {
			LOG.debug(
				"Exception while storing "
					+ node.getNodeName()
					+ "; gid = "
					+ node.getGID()
					+ "; address = ",
				e);
		}
	}

	public void insertAfter(final NodeImpl previous, final NodeImpl node) {
		final byte data[] = node.serialize();
		final DocumentImpl doc = (DocumentImpl) previous.getOwnerDocument();
		new DOMTransaction(this, domDb) {
			public Object start() {
				long address = previous.getInternalAddress();
				if (address > -1)
					address = domDb.insertAfter(doc, address, data);
				else {
					NodeRef ref = new NodeRef(doc.getDocId(), previous.getGID());
					address = domDb.insertAfter(doc, ref, data);
				}
				node.setInternalAddress(address);
				return null;
			}
		}
		.run();
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public final static class NodeRef extends Value {

		public NodeRef() {
			data = new byte[12];
		}

		public NodeRef(int docId, long gid) {
			data = new byte[12];
			ByteConversion.intToByte(docId, data, 0);
			ByteConversion.longToByte(gid, data, 4);
			len = 12;
			pos = 0;
		}

		public NodeRef(int docId) {
			data = new byte[4];
			ByteConversion.intToByte(docId, data, 0);
			len = 4;
			pos = 0;
		}

		int getDocId() {
			return ByteConversion.byteToInt(data, 0);
		}

		long getGid() {
			return ByteConversion.byteToLong(data, 4);
		}

		void set(int docId, long gid) {
			ByteConversion.intToByte(docId, data, 0);
			ByteConversion.longToByte(gid, data, 4);
			len = 12;
			pos = 0;
		}
	}

}
