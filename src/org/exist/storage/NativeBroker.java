/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
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
 *  $Id:
 */
package org.exist.storage;

import it.unimi.dsi.fastutil.Int2ObjectAVLTreeMap;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Observer;
import java.util.StringTokenizer;

import org.apache.log4j.Category;
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
import org.exist.dom.ArraySet;
import org.exist.dom.AttrImpl;
import org.exist.dom.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.NodeListImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SymbolTable;
import org.exist.dom.TextImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.serializers.NativeSerializer;
import org.exist.storage.serializers.Serializer;
import org.exist.util.ByteConversion;
import org.exist.util.CollectionCache;
import org.exist.util.Configuration;
import org.exist.util.IndexCallback;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.Occurrences;
import org.exist.util.ReadOnlyException;
import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;
import org.exist.util.XMLUtil;
import org.exist.xpath.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 *  NativeBroker.
 *
 *@author     Wolfgang Meier
 *@created    15. Mai 2002
 */
public class NativeBroker extends DBBroker {
	protected final static int BUFFERS = 256;

	protected static int FILE_BUFFER_SIZE = 131072;
	protected static int MEM_LIMIT_CHECK = 10000;

	private static Category LOG = Category.getInstance(NativeBroker.class.getName());

	protected static RelationalBroker.TableLock writeLock = new RelationalBroker.TableLock();
	protected BFile collectionsDb = null;
	protected DOMFile domDb = null;
	protected NativeElementIndex elementIndex;
	protected ElementPool elementPool = new ElementPool(50);
	protected BFile elementsDb = null;
	protected BFile namespacesDb = null;
	protected NativeTextEngine textEngine;
	protected Serializer xmlSerializer;
	protected static SymbolTable symbols = null;
	protected PatternCompiler compiler = new Perl5Compiler();
	protected PatternMatcher matcher = new Perl5Matcher();
	protected int defaultIndexDepth = 1;
	protected boolean readOnly = false;
	protected int memMinFree;
	protected int nodesCount = 0;
	protected final static CollectionCache collections = new CollectionCache(512);
	private final Runtime run = Runtime.getRuntime();

	/**
	 *  Constructor for the NativeBroker object
	 *
	 *@param  config              Description of the Parameter
	 *@exception  EXistException  Description of the Exception
	 */
	public NativeBroker(BrokerPool pool, Configuration config) throws EXistException {
		super(pool, config);
		String dataDir;
		int buffers, pageSize;
		String temp;
		boolean compress = false;
		if ((dataDir = (String) config.getProperty("db-connection.data-dir")) == null)
			dataDir = "data";

		if ((buffers = config.getInteger("db-connection.buffers")) < 0)
			buffers = BUFFERS;
		if ((pageSize = config.getInteger("db-connection.page-size")) < 0)
			pageSize = 4096;
		if ((temp = (String) config.getProperty("db-connection.compress")) != null)
			compress = temp.equals("true");
		if ((defaultIndexDepth = config.getInteger("indexer.index-depth")) < 0)
			defaultIndexDepth = 1;
		if ((memMinFree = config.getInteger("db-connection.min_free_memory")) < 0)
			memMinFree = 5000000;
		Paged.setPageSize(pageSize);
		String pathSep = System.getProperty("file.separator", "/");
		try {
			if ((elementsDb = (BFile) config.getProperty("db-connection.elements")) == null) {
				int elementsBuffers;
				if ((elementsBuffers = config.getInteger("db-connection.elements.buffers")) < 0)
					elementsBuffers = buffers * 4;

				LOG.debug("elements index buffer size: " + elementsBuffers);
				elementsDb =
					new BFile(
						new File(dataDir + pathSep + "elements.dbx"),
						elementsBuffers >> 3,
						elementsBuffers);
				elementsDb.fixedKeyLen = 4;
				if (!elementsDb.exists()) {
					LOG.info("creating elements.dbx");
					elementsDb.create();
				} else
					elementsDb.open();

				elementsDb.setCompression(compress);
				config.setProperty("db-connection.elements", elementsDb);
				readOnly = elementsDb.isReadOnly();
			}
			if ((namespacesDb = (BFile) config.getProperty("db-connection.namespaces")) == null) {
				namespacesDb =
					new BFile(
						new File(dataDir + pathSep + "namespaces.dbx"),
						buffers / 4,
						buffers / 4);
				if (!namespacesDb.exists()) {
					LOG.info("creating namespaces.dbx");
					namespacesDb.create();
					symbols = new SymbolTable();
					saveSymbols(namespacesDb);
				} else {
					namespacesDb.open();
					symbols = loadSymbols(namespacesDb);
				}
				config.setProperty("db-connection.namespaces", namespacesDb);
				if (!readOnly)
					readOnly = namespacesDb.isReadOnly();
			}

			if ((domDb = (DOMFile) config.getProperty("db-connection.dom")) == null) {
				LOG.debug("page buffer size = " + buffers);
				domDb = new DOMFile(new File(dataDir + pathSep + "dom.dbx"), buffers, 128);
				if (!domDb.exists()) {
					LOG.info("creating dom.dbx");
					domDb.create();
				} else
					domDb.open();

				config.setProperty("db-connection.dom", domDb);
				if (!readOnly)
					readOnly = domDb.isReadOnly();
			}
			if ((collectionsDb = (BFile) config.getProperty("db-connection.collections"))
				== null) {
				int collectionBuffers;
				if ((collectionBuffers = config.getInteger("db-connection.collections.buffers"))
					< 0)
					collectionBuffers = buffers * 2;
				LOG.debug("collections index buffer size: " + collectionBuffers);
				collectionsDb =
					new BFile(
						new File(dataDir + pathSep + "collections.dbx"),
						collectionBuffers / 2,
						collectionBuffers);
				if (!collectionsDb.exists()) {
					LOG.info("creating collections.dbx");
					collectionsDb.create();
				} else
					collectionsDb.open();

				collectionsDb.setCompression(compress);
				config.setProperty("db-connection.collections", collectionsDb);
				if (!readOnly)
					readOnly = collectionsDb.isReadOnly();
			}
			if (readOnly)
				LOG.info("database runs in read-only mode");
			textEngine = new NativeTextEngine(this, config);
			xmlSerializer = new NativeSerializer(this, config);
			elementIndex = new NativeElementIndex(this, config, elementsDb);
			getOrCreateCollection("/db");
		} catch (Exception e) {
			LOG.debug(e);
			e.printStackTrace();
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  name  Description of the Parameter
	 *@return       Description of the Return Value
	 */
	protected final static String normalizeCollectionName(String name) {
		StringBuffer out = new StringBuffer();
		for (int i = 0; i < name.length(); i++)
			if (name.charAt(i) == '/' && name.length() > i + 1 && name.charAt(i + 1) == '/')
				i++;
			else
				out.append(name.charAt(i));

		return out.toString();
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public Object acquireWriteLock() {
		return null;
	}

	/**
	 *  Adds a feature to the Observer attribute of the NativeBroker object
	 *
	 *@param  o  The feature to be added to the Observer attribute
	 */
	public void addObserver(Observer o) {
		super.addObserver(o);
		textEngine.addObserver(o);
		elementIndex.addObserver(o);
	}

	private final boolean compare(String o1, String o2, int relation) {
		int cmp;
		if (!isCaseSensitive())
			cmp = o1.toLowerCase().compareTo(o2.toLowerCase());
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

	public Occurrences[] scanIndexedElements(User user, Collection collection, boolean inclusive)
		throws PermissionDeniedException {
		if (!collection.getPermissions().validate(user, Permission.READ))
			throw new PermissionDeniedException(
				"you don't have the permission" + " to read collection " + collection.getName());
		List collections = inclusive ? collection.getDescendants(user) : new ArrayList();
		collections.add(collection);
		short collectionId, elementId;
		ElementValue ref;
		IndexQuery query;
		ArrayList values;
		Value val[];
		String name;
		Collection current;
		Int2ObjectAVLTreeMap map = new Int2ObjectAVLTreeMap();
		Occurrences oc;
		VariableByteInputStream is;
		int docId;
		int len;
		final Lock lock = elementsDb.getLock();
		for (Iterator i = collections.iterator(); i.hasNext();) {
			current = (Collection) i.next();
			collectionId = current.getId();
			ref = new ElementValue(collectionId);
			query = new IndexQuery(null, IndexQuery.TRUNC_RIGHT, ref);
			try {
				lock.acquire(this);
				lock.enter(this);
				values = elementsDb.findEntries(query);
				for (Iterator j = values.iterator(); j.hasNext();) {
					val = (Value[]) j.next();
					elementId = ByteConversion.byteToShort(val[0].getData(), 2);
					name = NativeBroker.getSymbols().getName(elementId);
					oc = (Occurrences) map.get(elementId);
					if (oc == null) {
						oc = new Occurrences(name);
						map.put(elementId, oc);
					}

					is =
						new VariableByteInputStream(val[1].data(), val[1].start(), val[1].length());
					try {
						while (is.available() > 0) {
							docId = is.readInt();
							len = is.readInt();
							oc.addOccurrences(len);
							is.skip(len * 3);
						}
					} catch (EOFException e) {
						e.printStackTrace();
					}
				}
			} catch (BTreeException e) {
				LOG.warn("exception while reading element index", e);
			} catch (IOException e) {
				LOG.warn("exception while reading element index", e);
			} catch (LockException e) {
				LOG.warn("failed to acquire lock", e);
			} finally {
				lock.release(this);
			}
		}
		Occurrences[] result = new Occurrences[map.size()];
		return (Occurrences[]) map.values().toArray(result);
	}

	/**
	 *  find elements by their tag name. This method is comparable to the DOM's
	 *  method call getElementsByTagName. All elements matching tagName and
	 *  belonging to one of the documents in the DocumentSet docs are returned.
	 *
	 *@param  docs     Description of the Parameter
	 *@param  tagName  Description of the Parameter
	 *@return          Description of the Return Value
	 */
	public NodeSet findElementsByTagName(DocumentSet docs, String tagName) {
		final long start = System.currentTimeMillis();
		final NodeSet result = new ArraySet(100000);
		DocumentImpl doc;
		int docId;
		int len;
		short collectionId;
		long gid;
		long address;
		long delta;
		long last = -1;
		int tid;
		int page;
		VariableByteInputStream is;
		ElementValue ref;
		Value val;
		//byte[] data;
		short sym;
		Collection collection;
		final Lock lock = elementsDb.getLock();
		for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
			collection = (Collection) i.next();
			collectionId = collection.getId();
			sym = NativeBroker.getSymbols().getSymbol(tagName);
			ref = new ElementValue(collectionId, sym);
			try {
				lock.acquire(this);
				lock.enter(this);
				val = elementsDb.get(ref);
			} catch (LockException e) {
				LOG.warn("failed to acquire lock", e);
				val = null;
			} finally {
				lock.release(this);
			}
			if (val == null)
				continue;
			//data = val.getData();
			is = new VariableByteInputStream(val.data(), val.start(), val.length());
			try {
				while (is.available() > 0) {
					docId = is.readInt();
					len = is.readInt();
					if ((doc = docs.getDoc(docId)) == null) {
						is.skip(len * 3);
						continue;
					}
					last = 0;
					for (int k = 0; k < len; k++) {
						delta = is.readLong();
						gid = last + delta;
						last = gid;
						page = is.readInt();
						tid = is.readInt();
						address = DOMFile.createPointer(page, tid);
						//LOG.debug("loaded " + docId + ':' + gid);
						result.add(new NodeProxy(doc, gid, Node.ELEMENT_NODE, address));
					}
				}
			} catch (EOFException e) {
				e.printStackTrace();
			}
		}
		LOG.debug(
			"found "
				+ tagName
				+ ": "
				+ result.getLength()
				+ " in "
				+ (System.currentTimeMillis() - start)
				+ "ms.");
		return result;
	}

	public void flush() {
		textEngine.flush();
		elementIndex.flush();
		if (symbols != null && symbols.hasChanged())
			saveSymbols(namespacesDb);
		nodesCount = 0;
	}

	/**
	 *  get all the documents in this database repository. The documents are
	 *  returned as a DocumentSet.
	 *
	 *@param  user  Description of the Parameter
	 *@return       The allDocuments value
	 */
	public DocumentSet getAllDocuments(User user) {
		long start = System.currentTimeMillis();
		Collection root = getCollection("/db");
		DocumentSet docs = root.allDocs(user, true);

		//		try {
		//			ArrayList collList = null;
		//			synchronized (collectionsDb) {
		//				collList = collectionsDb.getEntries();
		//			}
		//			if (collList == null)
		//				return docs;
		//
		//			Value val;
		//			Value[] entry;
		//			byte[] data;
		//			VariableByteInputStream istream;
		//			DocumentImpl doc;
		//			Collection collection;
		//			String collName;
		//			for (int i = 0; i < collList.size(); i++) {
		//				entry = (Value[]) collList.get(i);
		//				collName = entry[0].toString();
		//				if (collName.startsWith("__"))
		//					continue;
		//				collection = collections.get(collName);
		//				if(collection == null) {
		//					val = entry[1];
		//					data = val.getData();
		//					istream = new VariableByteInputStream(data);
		//					collection = new Collection(this, collName);
		//					collection.read(istream);
		//				}
		//				if (collection
		//					.getPermissions()
		//					.validate(user, Permission.READ))
		//					for (Iterator iter = collection.iterator();
		//						iter.hasNext();
		//						) {
		//						doc = (DocumentImpl) iter.next();
		//						if (doc
		//							.getPermissions()
		//							.validate(user, Permission.READ)) {
		//							docs.add(doc);
		//						}
		//					}
		//
		//			}
		//		} catch (BTreeException dbe) {
		//			LOG.error(dbe);
		//		} catch (IOException ioe) {
		//			LOG.debug(ioe);
		//		}
		LOG.debug(
			"loading "
				+ docs.getLength()
				+ " documents took "
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
	public NodeSet getAttributesByName(DocumentSet docs, String name) {
		NodeSet result = findElementsByTagName(docs, "@" + name);
		LOG.debug("found " + result.getLength() + " matching attributes");
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
		//		final long start = System.currentTimeMillis();
		name = normalizeCollectionName(name);
		if (name.length() > 0 && name.charAt(0) != '/')
			name = "/" + name;

		if (!name.startsWith("/db"))
			name = "/db" + name;

		if (name.endsWith("/") && name.length() > 1)
			name = name.substring(0, name.length() - 1);
		Value key = null;
		if(addr == -1)
			try {
				key = new Value(name.getBytes("UTF-8"));
			} catch (UnsupportedEncodingException uee) {
				key = new Value(name.getBytes());
			}
		synchronized (collections) {
			Collection collection = collections.get(name);
			if (collection != null)
				return collection;
			collection = new Collection(this, name);
			Value val = null;
			synchronized (collectionsDb) {
				if (addr < 0) {
					LOG.debug("loading collection " + name);
					val = collectionsDb.get(key);
				} else
					val = collectionsDb.get(addr);
			}
			if (val == null)
				return null;
			final byte[] data = val.getData();
			VariableByteInputStream istream = new VariableByteInputStream(data);
			try {
				collection.read(istream);
			} catch (IOException ioe) {
				LOG.warn(ioe);
				return null;
			}
			collections.add(collection);
			//			LOG.debug(
			//				"loading collection "
			//					+ name
			//					+ " took "
			//					+ (System.currentTimeMillis() - start)
			//					+ "ms.");
			return collection;
		}
	}

	public static SymbolTable getSymbols() {
		return symbols;
	}

	protected synchronized static SymbolTable loadSymbols(BFile namespacesDb) {
		Value key;
		try {
			key = new Value("__symbols".getBytes("UTF-8"));
		} catch (UnsupportedEncodingException uee) {
			key = new Value("__symbols".getBytes());
		}
		Value val = null;
		synchronized (namespacesDb) {
			val = namespacesDb.get(key);
		}
		if (val == null) {
			LOG.warn("symbol-table not found!");
			return null;
		}
		byte[] data = val.getData();
		VariableByteInputStream istream = new VariableByteInputStream(data);
		SymbolTable symbols = new SymbolTable();
		try {
			symbols.read(istream);
		} catch (IOException ioe) {
			LOG.warn("unexpected io error", ioe);
		}
		return symbols;
	}

	public Iterator getDOMIterator(DocumentImpl doc) {
		domDb.setOwnerObject(this);
		return domDb.iterator(doc, doc.getAddress());
	}

	/**
	 *  Gets the dOMIterator attribute of the NativeBroker object
	 *
	 *@param  proxy  Description of the Parameter
	 *@return        The dOMIterator value
	 */
	public Iterator getDOMIterator(NodeProxy proxy) {
		domDb.setOwnerObject(this);
		if (-1 < proxy.getInternalAddress())
			return domDb.iterator(proxy.doc, proxy.getInternalAddress());
		else
			return domDb.iterator(proxy.doc, proxy);
	}

	/**
	 *  Gets the dOMIterator attribute of the NativeBroker object
	 *
	 *@param  doc  Description of the Parameter
	 *@param  gid  Description of the Parameter
	 *@return      The dOMIterator value
	 */
	public Iterator getDOMIterator(Document doc, long gid) {
		domDb.setOwnerObject(this);
		return domDb.iterator((DocumentImpl) doc, new NodeProxy((DocumentImpl) doc, gid));
	}

	/**
	 *  Gets the databaseType attribute of the NativeBroker object
	 *
	 *@return    The databaseType value
	 */
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
	public Document getDocument(User user, String fileName) throws PermissionDeniedException {
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

	/**
	 *  Gets the documentsByCollection attribute of the NativeBroker object
	 *
	 *@param  collection                     Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The documentsByCollection value
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public DocumentSet getDocumentsByCollection(User user, String collection)
		throws PermissionDeniedException {
		return getDocumentsByCollection(user, collection, true);
	}

	/**
	 *  Gets the documentsByCollection attribute of the NativeBroker object
	 *
	 *@param  collection                     Description of the Parameter
	 *@param  inclusive                      Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The documentsByCollection value
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public DocumentSet getDocumentsByCollection(User user, String collection, boolean inclusive)
		throws PermissionDeniedException {
		DocumentSet docs = new DocumentSet();
		long start = System.currentTimeMillis();
		if (collection == null || collection.length() == 0)
			return docs;
		if (collection.charAt(0) != '/')
			collection = "/" + collection;
		if (!collection.startsWith("/db"))
			collection = "/db" + collection;
		Collection root = getCollection(collection);
		if (root == null) {
			LOG.debug("collection " + collection + " not found");
			return docs;
		}
		docs = root.allDocs(user, inclusive);
		//		ArrayList collections = null;
		//		// read collection + subcollections if inclusive=true
		//		if (inclusive) {
		//			Value key;
		//			try {
		//				key = new Value(collection.getBytes("UTF-8"));
		//			} catch (UnsupportedEncodingException uee) {
		//				key = new Value(collection.getBytes());
		//			}
		//			IndexQuery query =
		//				new IndexQuery(null, IndexQuery.TRUNC_RIGHT, key);
		//			CollectionsCallback cb = new CollectionsCallback(this);
		//			synchronized (collectionsDb) {
		//				try {
		//					collectionsDb.find(query, cb); 
		//				} catch (BTreeException e) {
		//					LOG.warn(
		//						"btree error while reading collection " + collection,
		//						e);
		//				} catch (IOException e) {
		//					LOG.warn(
		//						"io error while reading collection " + collection,
		//						e);
		//				}
		//			}
		//			collections = cb.getCollections();
		//			// read single collection
		//		} else {
		//			Collection coll = getCollection(collection);
		//			if (coll == null) {
		//				LOG.debug("collection " + collection + " not found");
		//				return docs;
		//			}
		//			collections = new ArrayList(1);
		//			collections.add(coll);
		//		}
		//		Collection temp;
		//		DocumentImpl doc;
		//		for (Iterator i = collections.iterator(); i.hasNext();) {
		//			temp = (Collection) i.next();
		//			if (!temp.getPermissions().validate(user, Permission.READ))
		//				throw new PermissionDeniedException("permission to read collection denied");
		//			for (Iterator j = temp.iterator(); j.hasNext();) {
		//				doc = (DocumentImpl) j.next();
		//				if (doc.getPermissions().validate(user, Permission.READ))
		//					docs.add(doc);
		//			}
		//		}
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
	public DocumentSet getDocumentsByDoctype(User user, String doctypeName) {
		DocumentSet docs = getAllDocuments(user);
		DocumentSet result = new DocumentSet();
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

	/**
	 *  Gets the namespacePrefix attribute of the NativeBroker object
	 *
	 *@param  namespace  Description of the Parameter
	 *@return            The namespacePrefix value
	 */
	public String getNamespacePrefix(String namespace) {
		Value ns;
		try {
			ns = new Value(namespace.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException uee) {
			ns = new Value(namespace);
		}
		Value prefix = null;
		synchronized (namespacesDb) {
			prefix = namespacesDb.get(ns);
		}
		if (prefix == null)
			return null;
		return prefix.toString();
	}

	/**
	 *  Gets the namespaceURI attribute of the NativeBroker object
	 *
	 *@param  prefix  Description of the Parameter
	 *@return         The namespaceURI value
	 */
	public String getNamespaceURI(String prefix) {
		return getNamespacePrefix(prefix);
	}

	/**
	 *  Gets the nextCollectionId attribute of the NativeBroker object
	 *
	 *@return    The nextCollectionId value
	 */
	protected short getNextCollectionId() throws ReadOnlyException {
		short nextCollectionId = 0;
		Value key = new Value("__next_collection_id");
		Value data;
		synchronized (collectionsDb) {
			data = collectionsDb.get(key);
			if (data != null) {
				nextCollectionId = ByteConversion.byteToShort(data.getData(), 0);
				++nextCollectionId;
			}
			byte[] d = new byte[2];
			ByteConversion.shortToByte(nextCollectionId, d, 0);
			collectionsDb.put(key, d);
		}
		return nextCollectionId;
	}

	/**
	 *  get the number of documents in the repository this is used to determine
	 *  a free document-id for the document to be stored.
	 *
	 *@param  collection  Description of the Parameter
	 *@return             The nextDocId value
	 */
	public int getNextDocId(Collection collection) {
		int nextDocId = 1;
		Value key = new Value("__next_doc_id");
		Value data;
		synchronized (collectionsDb) {
			data = collectionsDb.get(key);
			if (data != null) {
				nextDocId = ByteConversion.byteToInt(data.getData(), 0);
				++nextDocId;
			}
			byte[] d = new byte[4];
			ByteConversion.intToByte(nextDocId, d, 0);
			try {
				collectionsDb.put(key, d);
			} catch (ReadOnlyException e) {
				LOG.debug("database read-only");
				return -1;
			}
		}
		return nextDocId;
	}

	public void index(NodeImpl node) {
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final long gid = node.getGID();
		final short nodeType = node.getNodeType();
		final String nodeName = node.getNodeName();
		final long address = node.getInternalAddress();
		IndexPaths idx =
			(IndexPaths) config.getProperty(
				"indexScheme." + node.getOwnerDocument().getDoctype().getName());
		if (address < 0)
			LOG.debug("node " + gid + ": internal address missing");
		NodeProxy tempProxy = new NodeProxy(doc, gid, address);
		switch (nodeType) {
			case Node.ELEMENT_NODE :
				// save element by calling ElementIndex
				elementIndex.setDocument(doc);
				elementIndex.addRow(nodeName, tempProxy);
				break;
			case Node.ATTRIBUTE_NODE :
				elementIndex.setDocument(doc);
				elementIndex.addRow("@" + nodeName, tempProxy);
				// check if attribute value should be fulltext-indexed
				// by calling IndexPaths.match(path) 
				if (idx == null || idx.getIncludeAttributes())
					textEngine.storeAttribute(idx, (AttrImpl) node);
				// if the attribute has type ID, store the ID-value
				// to the element index as well
				if (((AttrImpl) node).getType() == AttrImpl.ID) {
					LOG.debug("storing ID");
					elementIndex.addRow("&" + ((AttrImpl) node).getValue(), tempProxy);
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

	public void reindex(DocumentImpl oldDoc, DocumentImpl doc) {
		int idxLevel = doc.reindexRequired();
		if (idxLevel < 0)
			return;
		long start = System.currentTimeMillis();
		// remove dom index
		LOG.debug("removing old nodes...");
		Value ref = new NodeRef(doc.getDocId());
		IndexQuery query = new IndexQuery(null, IndexQuery.TRUNC_RIGHT, ref);
		Lock lock = domDb.getLock();
		// try to acquire a lock on the file
		try {
			lock.acquire(this, Lock.WRITE_LOCK);
			lock.enter(this);
			domDb.setOwnerObject(this);
			ArrayList nodes = domDb.findKeys(query);
			long gid;
			for (Iterator i = nodes.iterator(); i.hasNext();) {
				ref = (Value) i.next();
				gid = ByteConversion.byteToLong(ref.data(), 4);
				if (oldDoc.getTreeLevel(gid) >= doc.reindexRequired()) {
					domDb.removeValue(ref);
				}
			}
			domDb.flush();
		} catch (DBException e) {
			LOG.warn("db error during reindex", e);
		} catch (IOException e) {
			LOG.warn("io error during reindex", e);
		} catch (LockException e) {
			// timed out
			e.printStackTrace();
			return;
		} finally {
			lock.release(this);
		}
		LOG.debug("reindexing...");
		NodeList nodes = doc.getChildNodes();
		NodeImpl n;
		Iterator iterator;
		for (int i = 0; i < nodes.getLength(); i++) {
			n = (NodeImpl) nodes.item(i);
			iterator = getDOMIterator(new NodeProxy(doc, n.getGID()));
			iterator.next();
			scanNodes(iterator, n, "");
		}
		LOG.debug("reindexing elements...");
		elementIndex.reindex(oldDoc);
		LOG.debug("reindexing text...");
		textEngine.reindex(oldDoc);
		flush();
		doc.setReindexRequired(-1);
		LOG.debug("reindex took " + (System.currentTimeMillis() - start) + "ms.");
	}

	private void reindex(final NodeImpl node, String currentPath) {
		if (node.getGID() < 0)
			LOG.debug("illegal node: " + node.getGID() + "; " + node.getNodeName());
		IndexPaths idx =
			(IndexPaths) config.getProperty(
				"indexScheme." + node.getOwnerDocument().getDoctype().getName());
		final short nodeType = node.getNodeType();
		final long gid = node.getGID();
		final String nodeName = node.getNodeName();
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final byte data[] = node.serialize();
		final int depth = idx == null ? defaultIndexDepth : idx.getIndexDepth();
		final int level = doc.getTreeLevel(gid);
		if (level >= doc.reindexRequired()) {
			NodeIndexListener listener;
			if ((listener = doc.getIndexListener()) != null)
				listener.nodeChanged(node);
			if (nodeType == Node.ELEMENT_NODE && level <= depth) {
				new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
					public Object start() throws ReadOnlyException {
						try {
							domDb.addValue(
								new NodeRef(doc.getDocId(), gid),
								node.getInternalAddress());
						} catch (BTreeException e) {
							LOG.warn("exception during reindex", e);
						} catch (IOException e) {
							LOG.warn("exception during reindex", e);
						}
						return null;
					}
				}
				.run();
			}
			final NodeProxy tempProxy = new NodeProxy(doc, gid, node.getInternalAddress());
			switch (nodeType) {
				case Node.ELEMENT_NODE :
					// save element by calling ElementIndex
					elementIndex.setDocument(doc);
					elementIndex.addRow(nodeName, tempProxy);
					break;
				case Node.ATTRIBUTE_NODE :
					elementIndex.setDocument(doc);
					elementIndex.addRow("@" + nodeName, tempProxy);
					// check if attribute value should be fulltext-indexed
					// by calling IndexPaths.match(path) 
					if (idx == null
						|| (idx.getIncludeAttributes() && idx.match(currentPath + "/@" + nodeName)))
						textEngine.storeAttribute(idx, (AttrImpl) node);
					// if the attribute has type ID, store the ID-value
					// to the element index as well
					if (((AttrImpl) node).getType() == AttrImpl.ID) {
						LOG.debug("storing ID");
						elementIndex.addRow("&" + ((AttrImpl) node).getValue(), tempProxy);
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
	}

	private void scanNodes(Iterator iterator, NodeImpl node, String currentPath) {
		if (node.getNodeType() == Node.ELEMENT_NODE)
			currentPath = currentPath + '/' + node.getNodeName();
		reindex(node, currentPath);
		if (node.hasChildNodes()) {
			long firstChildId =
				XMLUtil.getFirstChildId((DocumentImpl) node.getOwnerDocument(), node.getGID());
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
			final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
			long p;
			for (long gid = firstChildId; gid < lastChildId; gid++) {
				Value value = (Value) iterator.next();
				NodeImpl child = NodeImpl.deserialize(value.getData(), doc);
				child.setGID(gid);
				child.setOwnerDocument(doc);
				child.setInternalAddress(value.getAddress());
				scanNodes(iterator, child, currentPath);
			}
		}
	}

	/**
	 *  Gets the nodeValue attribute of the NativeBroker object
	 *
	 *@param  node  Description of the Parameter
	 *@return       The nodeValue value
	 */
	public String getNodeValue(NodeImpl node) {
		switch (node.getNodeType()) {
			case Node.ELEMENT_NODE :
				if (node.getChildCount() > 0) {
					long firstChild = node.firstChildID();
					NodeProxy proxy =
						new NodeProxy((DocumentImpl) node.getOwnerDocument(), firstChild);
					Iterator domIterator = getDOMIterator(proxy);
					ByteArrayOutputStream buf = new ByteArrayOutputStream(12);
					getNodeValue(buf, domIterator);
					byte[] data = buf.toByteArray();
					String value;
					try {
						value = new String(data, 0, data.length, "UTF-8");
					} catch (UnsupportedEncodingException uee) {
						value = new String(data, 0, data.length);
					}
					return value;
				} else
					return "";
			case Node.TEXT_NODE :
				return ((Text) node).getData();
			default :
				return "";
		}
	}

	/**
	 *  Gets the nodeValue attribute of the NativeBroker object
	 *
	 *@param  proxy  Description of the Parameter
	 *@return        The nodeValue value
	 */
	public String getNodeValue(NodeProxy proxy) {
		Iterator domIterator = getDOMIterator(proxy);
		ByteArrayOutputStream buf = new ByteArrayOutputStream(12);
		getNodeValue(buf, domIterator);
		byte[] data = buf.toByteArray();
		String value;
		try {
			value = new String(data, 0, data.length, "UTF-8");
		} catch (UnsupportedEncodingException uee) {
			value = new String(data, 0, data.length);
		}
		return value;
	}

	/**
	 *  Gets the nodeValue attribute of the NativeBroker object
	 *
	 *@param  buf          Description of the Parameter
	 *@param  domIterator  Description of the Parameter
	 */
	protected void getNodeValue(ByteArrayOutputStream buf, Iterator domIterator) {
		getNodeValue(buf, domIterator, true);
	}

	/**
	 *  Gets the nodeValue attribute of the NativeBroker object
	 *
	 *@param  buf          Description of the Parameter
	 *@param  domIterator  Description of the Parameter
	 *@param  firstNode    Description of the Parameter
	 */
	protected void getNodeValue(
		ByteArrayOutputStream buf,
		Iterator domIterator,
		boolean firstNode) {
		try {
			Value value = (Value) domIterator.next();
			if (value == null)
				return;
			//byte[] data = value.getData();
			short type = Signatures.getType(value.get(0));
			String val;
			switch (type) {
				case Node.ELEMENT_NODE :
					int children = ByteConversion.byteToInt(value.data(), value.start() + 1);
					for (int i = 0; i < children; i++)
						getNodeValue(buf, domIterator, false);

					break;
				case Node.TEXT_NODE :
					if (buf.size() > 0)
						buf.write((byte) ' ');
					value.streamTo(buf, 1);
					break;
				case Node.ATTRIBUTE_NODE :
					// use attribute value only if the
					// context node is an attribute (if this is
					// the top level call of this method)
					if (firstNode) {
						byte idSizeType = (byte) (value.get(0) & 0x3);
						value.streamTo(
							buf,
							1 + Signatures.getLength(idSizeType),
							value.getLength() - 1 - Signatures.getLength(idSizeType));
					}
			}
		} catch (IOException e) {
			LOG.warn(e);
		}
	}

	/**
	 *  get all the nodes containing the search terms given by the array expr
	 *  using the fulltext-index. Calls to this method are normally delegated to
	 *  the associated instance of class TextSearchEngine.
	 *
	 *@param  docs      Description of the Parameter
	 *@param  termList  Description of the Parameter
	 *@param  type      Description of the Parameter
	 *@return           NodeSet[] an array of node sets, one for each search
	 *      term
	 */
	public NodeSet[] getNodesContaining(DocumentSet docs, String[] termList, int type) {
		return textEngine.getNodesContaining(docs, termList, type);
	}

	/**
	 *  Gets the nodesContaining attribute of the NativeBroker object
	 *
	 *@param  docs      Description of the Parameter
	 *@param  termList  Description of the Parameter
	 *@return           The nodesContaining value
	 */
	public NodeSet[] getNodesContaining(DocumentSet docs, String[] termList) {
		return getNodesContaining(docs, termList, MATCH_EXACT);
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
	public NodeSet getNodesEqualTo(NodeSet context, DocumentSet docs, int relation, String expr) {
		long start = System.currentTimeMillis();
		NodeSet temp;
		int truncation = Constants.TRUNC_NONE;
		if (expr.charAt(0) == '%') {
			expr = expr.substring(1, expr.length());
			truncation = Constants.TRUNC_LEFT;
		}
		if (expr.charAt(expr.length() - 1) == '%') {
			expr = expr.substring(0, expr.length() - 1);
			truncation =
				(truncation == Constants.TRUNC_LEFT) ? Constants.TRUNC_BOTH : Constants.TRUNC_RIGHT;
		}
		NodeSet result = scanSequential(context, docs, relation, truncation, expr);
		LOG.debug(
			"searching "
				+ context.getLength()
				+ " nodes took "
				+ (System.currentTimeMillis() - start)
				+ "ms.");
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
	public Collection getOrCreateCollection(User user, String name)
		throws PermissionDeniedException {
		//		final long start = System.currentTimeMillis();
		name = normalizeCollectionName(name);
		if (name.length() > 0 && name.charAt(0) != '/')
			name = "/" + name;

		if (!name.startsWith("/db"))
			name = "/db" + name;

		if (name.endsWith("/") && name.length() > 1)
			name = name.substring(0, name.length() - 1);

		synchronized (collections) {
			Collection current;
			try {
				StringTokenizer tok = new StringTokenizer(name, "/");
				String temp = tok.nextToken();
				String path = "/db";
				Collection sub;
				current = getCollection("/db");
				if (current == null) {
					LOG.debug("creating root collection /db");
					current = new Collection(this, "/db");
					current.getPermissions().setPermissions(0777);
					current.getPermissions().setOwner(user);
					current.getPermissions().setGroup(user.getPrimaryGroup());
					current.setId(getNextCollectionId());
					saveCollection(current);
				}
				while (tok.hasMoreTokens()) {
					temp = tok.nextToken();
					path = path + "/" + temp;
					if (current.hasSubcollection(temp))
						current = getCollection(path);
					else {
						if (!current.getPermissions().validate(user, Permission.WRITE))
							throw new PermissionDeniedException("not allowed to write to collection");
						LOG.debug("creating collection " + path);
						sub = new Collection(this, path);
						sub.getPermissions().setOwner(user);
						sub.getPermissions().setGroup(user.getPrimaryGroup());
						sub.setId(getNextCollectionId());
						current.addCollection(sub);
						saveCollection(current);
						current = sub;
					}
				}
			} catch (ReadOnlyException e) {
				LOG.debug("database read-only");
				return null;
			}
			//			LOG.debug("getOrCreateCollection took " + 
			//				(System.currentTimeMillis() - start) + "ms.");
			return current;
		}
	}

	/**
	 *  Gets the range attribute of the NativeBroker object
	 *
	 *@param  doc    Description of the Parameter
	 *@param  first  Description of the Parameter
	 *@param  last   Description of the Parameter
	 *@return        The range value
	 */
	public NodeList getRange(final Document doc, final long first, final long last) {
		NodeListImpl result = new NodeListImpl((int) (last - first));
		for (long gid = first; gid <= last; gid++)
			result.add(objectWith(doc, gid));

		return result;
	}

	/**
	 *  Gets the serializer attribute of the NativeBroker object
	 *
	 *@return    The serializer value
	 */
	public Serializer getSerializer() {
		xmlSerializer.reset();
		return xmlSerializer;
	}

	/**
	 *  return a data-input-stream which is positioned at the first byte of the
	 *  node with id gid and owned by doc. This method may be used to do a
	 *  sequential walk through a node and it's descendants.
	 *
	 *@param  doc  Description of the Parameter
	 *@param  gid  Description of the Parameter
	 *@return      The stream value
	 */
	public DataInput getStream(Document doc, long gid) {
		return null;
	}

	/**
	 *  Gets the textEngine attribute of the NativeBroker object
	 *
	 *@return    The textEngine value
	 */
	public TextSearchEngine getTextEngine() {
		return textEngine;
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public Serializer newSerializer() {
		return new NativeSerializer(this, null);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  doc  Description of the Parameter
	 *@param  gid  Description of the Parameter
	 *@return      Description of the Return Value
	 */
	public Node objectWith(final Document doc, final long gid) {
		return (Node) new DOMTransaction(this, domDb) {
			public Object start() {
				Value val = domDb.get(new NodeProxy((DocumentImpl) doc, gid));
				if (val == null) {
					LOG.debug("node " + gid + " not found!");
					Thread.dumpStack();
					return null;
				}
				NodeImpl node = NodeImpl.deserialize(val.getData(), (DocumentImpl) doc);
				node.setGID(gid);
				node.setOwnerDocument(doc);
				node.setInternalAddress(val.getAddress());
				return node;
			}
		}
		.run();
	}

	/**
	 *  Description of the Method
	 *
	 *@param  doc  Description of the Parameter
	 *@return      Description of the Return Value
	 */
	public Node preloadDocument(DocumentImpl doc) {
		return null;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  namespace  Description of the Parameter
	 *@param  prefix     Description of the Parameter
	 */
	public void registerNamespace(String namespace, String prefix) {
		byte[] ns;
		byte[] pfx;
		try {
			ns = namespace.getBytes("UTF-8");
			pfx = prefix.getBytes("UTF-8");
		} catch (UnsupportedEncodingException uee) {
			ns = namespace.getBytes();
			pfx = prefix.getBytes();
		}
		LOG.debug("registering namespace: " + namespace + "; prefix: " + prefix);
		synchronized (namespacesDb) {
			try {
				namespacesDb.put(new Value(ns), pfx, false);
				namespacesDb.put(new Value(pfx), ns, false);
			} catch (ReadOnlyException e) {
				LOG.warn("database is read-only");
			}
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  lock  Description of the Parameter
	 */
	public void releaseWriteLock(Object lock) {
	}

	/**
	 *  Description of the Method
	 *
	 *@param  name                           Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public boolean removeCollection(User user, String name) throws PermissionDeniedException {
		if (readOnly)
			throw new PermissionDeniedException("database is read-only");
		try {
			if (!name.startsWith("/db"))
				name = "/db" + name;

			Collection collection = getCollection(name);
			if (collection == null) {
				LOG.debug("collection " + name + " not found!");
				return false;
			}
			if (!collection.getPermissions().validate(user, Permission.WRITE))
				throw new PermissionDeniedException("not allowed to remove collection");

			synchronized (collections) {
				if (!name.equals("/db"))
					collections.clear();
				Collection parent = collection.getParent();
				if (parent != null) {
					parent.removeCollection(name.substring(name.lastIndexOf("/") + 1));
					saveCollection(parent);
				}
			}
			((NativeTextEngine) textEngine).removeCollection(collection);

			LOG.debug("removing elements ...");
			short collectionId = collection.getId();

			Value ref = new ElementValue(collectionId);
			IndexQuery query = new IndexQuery(null, IndexQuery.TRUNC_RIGHT, ref);
			Lock lock = elementsDb.getLock();
			try {
				lock.acquire(this, Lock.WRITE_LOCK);
				lock.enter(this);
				ArrayList elements = elementsDb.findKeys(query);
				LOG.debug("found " + elements.size() + " elements.");
				Value val;
				for (Iterator i = elements.iterator(); i.hasNext();) {
					val = (Value) i.next();
					elementsDb.remove(val.getAddress());
					elementsDb.removeValue(val);
				}
			} catch (LockException e) {
				LOG.error("could not acquire lock on elements index", e);
			} finally {
				lock.release(this);
			}
			elementPool.clear();

			String childCollection;
			LOG.debug("removing sub-collections");
			for (Iterator i = collection.collectionIterator(); i.hasNext();) {
				childCollection = (String) i.next();
				removeCollection(
					(name.equals("/") ? "/" + childCollection : name + "/" + childCollection));
			}
			// if this is not the root collection remove it completely
			if (name.equals("/db"))
				saveCollection(collection);
			else {
				Value key;
				try {
					key = new Value(name.getBytes("UTF-8"));
				} catch (UnsupportedEncodingException uee) {
					key = new Value(name.getBytes());
				}
				synchronized (collectionsDb) {
					collectionsDb.remove(key);
				}
			}
			LOG.debug("removed collection ...");

			LOG.debug("removing dom ...");
			for (Iterator i = collection.iterator(); i.hasNext();) {
				final DocumentImpl doc = (DocumentImpl) i.next();
				new DOMTransaction(this, domDb) {
					public Object start() {
						domDb.remove(doc.getAddress());
						NodeList children = doc.getChildNodes();
						NodeImpl node;
						for (int j = 0; j < children.getLength(); j++) {
							node = (NodeImpl) children.item(j);
							Iterator k = getDOMIterator(doc, node.getGID());
							removeNodes(k);
						}
						return null;
					}
				}
				.run();
				new DOMTransaction(this, domDb) {
					public Object start() {
						try {
							Value ref = new NodeRef(doc.getDocId());
							IndexQuery query = new IndexQuery(null, IndexQuery.TRUNC_RIGHT, ref);
							ArrayList nodes = domDb.findKeys(query);
							for (Iterator j = nodes.iterator(); j.hasNext();) {
								ref = (Value) j.next();
								domDb.removeValue(ref);
							}
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
			}
			return true;
		} catch (IOException ioe) {
			LOG.warn(ioe);
		} catch (BTreeException bte) {
			LOG.warn(bte);
		} catch (ReadOnlyException e) {
			LOG.warn("database is read-only");
		}
		return false;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  docName                        Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public void removeDocument(User user, String docName) throws PermissionDeniedException {
		if (readOnly)
			throw new PermissionDeniedException("database is read-only");
		try {
			if (!docName.startsWith("/"))
				docName = '/' + docName;
			final DocumentImpl doc = (DocumentImpl) getDocument(docName);
			if (doc == null) {
				LOG.debug("document " + docName + " not found");
				return;
			}
			if (!doc.getCollection().getPermissions().validate(user, Permission.WRITE))
				throw new PermissionDeniedException(
					"write access to collection denied; user=" + user.getName());
			if (!doc.getPermissions().validate(user, Permission.WRITE))
				throw new PermissionDeniedException("permission to remove document denied");
			LOG.info("removing document " + doc.getDocId() + "...");
			// remove document
			synchronized (collections) {
				synchronized (collectionsDb) {
					Collection collection = doc.getCollection();
					collection.removeDocument(docName);
					saveCollection(collection);
					collectionsDb.flush();
					//collections.remove(collection);
				}
			}

			// drop element-index
			short collectionId = doc.getCollection().getId();
			Value ref = new ElementValue(collectionId);
			IndexQuery query = new IndexQuery(null, IndexQuery.TRUNC_RIGHT, ref);
			Lock lock = elementsDb.getLock();
			try {
				lock.acquire(this, Lock.WRITE_LOCK);
				lock.enter(this);
				ArrayList elements = elementsDb.findKeys(query);
				LOG.debug("found " + elements.size() + " elements.");

				Value key;
				Value value;
				byte[] data;
				byte[] ndata;
				VariableByteInputStream is;
				VariableByteOutputStream os;
				int len;
				int docId;
				long delta;
				int page;
				int tid;
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
									page = is.readInt();
									tid = is.readInt();
									os.writeLong(delta);
									os.writeInt(page);
									os.writeInt(tid);
								}
							} else {
								changed = true;
								// skip
								for (int j = 0; j < len; j++) {
									is.readLong();
									is.readInt();
									is.readInt();
								}
							}
						}
					} catch (EOFException e) {
						LOG.debug("eof: " + is.available());
					}
					if (changed) {
						ndata = os.toByteArray();
						if (elementsDb.put(key, ndata) < 0)
							LOG.debug("could not save element");
					}
				}
			} catch (LockException e) {
				LOG.warn("could not acquire lock on elements", e);
			} finally {
				lock.release(this);
			}
			elementPool.clear();

			((NativeTextEngine) textEngine).removeDocument(doc);

			new DOMTransaction(this, domDb) {
				public Object start() {
					NodeList children = doc.getChildNodes();
					NodeImpl node;
					for (int i = 0; i < children.getLength(); i++) {
						node = (NodeImpl) children.item(i);
						Iterator j = getDOMIterator(doc, node.getGID());
						removeNodes(j);
					}
					domDb.remove(doc.getAddress());
					return null;
				}
			}
			.run();

			ref = new NodeRef(doc.getDocId());
			final IndexQuery idx = new IndexQuery(null, IndexQuery.TRUNC_RIGHT, ref);
			new DOMTransaction(this, domDb) {
				public Object start() {
					try {
						ArrayList nodes = domDb.findKeys(idx);
						Value ref;
						for (Iterator i = nodes.iterator(); i.hasNext();) {
							ref = (Value) i.next();
							domDb.removeValue(ref);
						}
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
			LOG.info("removed document.");
		} catch (IOException ioe) {
			ioe.printStackTrace();
			LOG.warn(ioe);
		} catch (BTreeException bte) {
			bte.printStackTrace();
			LOG.warn(bte);
		} catch (DBException dbe) {
			LOG.warn(dbe);
		} catch (ReadOnlyException e) {
			LOG.warn("database is read-only");
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  domIterator  Description of the Parameter
	 */
	protected void removeNodes(Iterator domIterator) {
		final Value next = (Value) domIterator.next();
		final byte[] data = next.getData();
		final short type = Signatures.getType(data[0]);
		switch (type) {
			case Node.ELEMENT_NODE :
				int children = ByteConversion.byteToInt(data, 1);
				domIterator.remove();
				for (int i = 0; i < children; i++)
					removeNodes(domIterator);

				break;
			default :
				domIterator.remove();
		}
	}

	public void removeNode(final NodeImpl node) {
		LOG.debug("removing " + node.getGID());
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		new DOMTransaction(this, domDb) {
			public Object start() {
				final long address = node.getInternalAddress();
				final NodeRef key = new NodeRef(doc.getDocId(), node.getGID());
				if (address > -1)
					domDb.remove(key, address);
				else
					domDb.remove(key);
				return null;
			}
		}
		.run();
	}

	public void addDocument(Collection collection, DocumentImpl doc)
		throws PermissionDeniedException {
		Value name;
		try {
			name = new Value(collection.getName().getBytes("UTF-8"));
		} catch (UnsupportedEncodingException uee) {
			LOG.debug(uee);
			name = new Value(collection.getName().getBytes());
		}
		try {
			storeDocument(doc);
			VariableByteOutputStream ostream = new VariableByteOutputStream();
			doc.write(ostream);
			byte[] data = ostream.toByteArray();
			synchronized (collectionsDb) {
				long address = collectionsDb.append(name, data);
				if (address < 0) {
					LOG.debug("could not store collection data for " + collection.getName());
					return;
				}
				collection.setAddress(address);
				if (!name.equals("/db")) {
					Collection parent = collection.getParent();
					parent.update(collection);
					saveCollection(parent);
				}
			}
		} catch (IOException ioe) {
			LOG.debug(ioe);
		} catch (ReadOnlyException e) {
			LOG.warn("database is read-only");
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  collection  Description of the Parameter
	 */
	public void saveCollection(Collection collection) throws PermissionDeniedException {
		if (readOnly)
			throw new PermissionDeniedException("database is read-only");
		try {
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
				final VariableByteOutputStream ostream = new VariableByteOutputStream();
				collection.write(ostream);
				synchronized (collectionsDb) {
					final long addr = collectionsDb.put(name, ostream.toByteArray());
					if (addr < 0) {
						LOG.debug("could not store collection data for " + collection.getName());
						return;
					}
					collection.setAddress(addr);
					if (!name.equals("/db")) {
						Collection parent = collection.getParent();
						parent.update(collection);
						saveCollection(parent);
					}
				}
			} catch (IOException ioe) {
				LOG.debug(ioe);
			}
		} catch (ReadOnlyException e) {
			LOG.warn("database is read-only");
		}
		collections.add(collection);
	}

	public static void saveSymbols(BFile namespacesDb) {
		Value name;
		try {
			name = new Value("__symbols".getBytes("UTF-8"));
		} catch (UnsupportedEncodingException uee) {
			name = new Value("__symbols".getBytes());
		}
		try {
			VariableByteOutputStream ostream = new VariableByteOutputStream();
			symbols.write(ostream);
			byte[] data = ostream.toByteArray();
			synchronized (namespacesDb) {
				if (namespacesDb.put(name, data) < 0) {
					LOG.debug("could not store symbol table");
					return;
				}
			}
		} catch (IOException ioe) {
			LOG.warn(ioe);
		} catch (ReadOnlyException e) {
			LOG.warn("database is read-only");
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
		ByteArrayOutputStream buf = new ByteArrayOutputStream(12);
		byte[] data;
		long filePos;
		int offset;
		NodeRef nodeRef;
		String cmp;
		Iterator domIterator = null;
		Pattern regexp = null;
		if (relation == Constants.REGEXP)
			try {
				regexp = compiler.compile(expr.toLowerCase(), Perl5Compiler.CASE_INSENSITIVE_MASK);
				truncation = Constants.REGEXP;
			} catch (MalformedPatternException e) {
				LOG.debug(e);
			}
		for (Iterator i = context.iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			domIterator = getDOMIterator(p);
			buf.reset();
			getNodeValue(buf, domIterator);
			data = buf.toByteArray();
			try {
				content = new String(data, 0, data.length, "UTF-8");
			} catch (UnsupportedEncodingException uee) {
				content = new String(data, 0, data.length);
			}
			if (isCaseSensitive())
				cmp = content;
			else {
				cmp = content.toLowerCase();
				expr = expr.toLowerCase();
			}
			//System.out.println(content);
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
					if (regexp != null && matcher.contains(cmp, regexp))
						resultNodeSet.add(p);

			}
		}
		return resultNodeSet;
	}

	/**
	 *  Sets the retrvMode attribute of the NativeBroker object
	 *
	 *@param  mode  The new retrvMode value
	 */
	public void setRetrvMode(int mode) {
	}

	/**  Description of the Method */
	public void shutdown() {
		super.shutdown();
		try {
			flush();
			sync();
			textEngine.close();
			domDb.close();
			elementsDb.close();
			namespacesDb.close();
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
	public void store(final NodeImpl node, String currentPath) {
		// first, check available memory
		final int percent = (int) (run.freeMemory() / (run.totalMemory() / 100));
		if (nodesCount > MEM_LIMIT_CHECK && percent < memMinFree) {
			LOG.info("total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
			flush();
			System.gc();
			LOG.info("total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
		}
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final IndexPaths idx =
			(IndexPaths) config.getProperty("indexScheme." + doc.getDoctype().getName());
		final long gid = node.getGID();
		if (gid < 0) {
			LOG.debug("illegal node: " + gid + "; " + node.getNodeName());
			Thread.dumpStack();
			return;
		}
		final short nodeType = node.getNodeType();
		final String nodeName = node.getNodeName();
		final int depth = idx == null ? defaultIndexDepth : idx.getIndexDepth();
		final byte data[] = node.serialize();
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				long address = -1;
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
				return null;
			}
		}
		.run();
		++nodesCount;
		NodeProxy tempProxy = new NodeProxy(doc, gid, node.getInternalAddress());
		switch (nodeType) {
			case Node.ELEMENT_NODE :
				// save element by calling ElementIndex
				elementIndex.setDocument(doc);
				elementIndex.addRow(nodeName, tempProxy);
				break;
			case Node.ATTRIBUTE_NODE :
				elementIndex.setDocument(doc);
				elementIndex.addRow("@" + nodeName, tempProxy);
				// check if attribute value should be fulltext-indexed
				// by calling IndexPaths.match(path) 
				if (idx == null
					|| (idx.getIncludeAttributes() && idx.match(currentPath + "/@" + nodeName)))
					textEngine.storeAttribute(idx, (AttrImpl) node);
				// if the attribute has type ID, store the ID-value
				// to the element index as well
				if (((AttrImpl) node).getType() == AttrImpl.ID) {
					LOG.debug("storing ID");
					elementIndex.addRow("&" + ((AttrImpl) node).getValue(), tempProxy);
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
				doc.setAddress(domDb.add(data));
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
		LOG.debug("syncing broker");
		// uncomment this to get statistics on page buffer usage
		elementsDb.printStatistics();
		collectionsDb.printStatistics();
		domDb.printStatistics();
		try {
			synchronized (collectionsDb) {
				collectionsDb.flush();
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
			synchronized (namespacesDb) {
				namespacesDb.flush();
			}
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
					if (-1 < internalAddress)
						domDb.update(
							new NodeRef(doc.getDocId(), node.getGID()),
							internalAddress,
							data);
					else
						domDb.update(new NodeRef(doc.getDocId(), node.getGID()), data);

					return null;
				}
			}
			.run();
		} catch (Exception e) {
			LOG.debug(
				"Exception while storing "
					+ node.getNodeName()
					+ "; gid = "
					+ node.getGID()
					+ "; address = "
					+ DOMFile.printAddress(node.getInternalAddress()),
				e);
		}
	}

	public void insertAfter(final NodeImpl previous, final NodeImpl node) {
		final byte data[] = node.serialize();
		new DOMTransaction(this, domDb) {
			public Object start() {
				NodeRef ref =
					new NodeRef(
						((DocumentImpl) previous.getOwnerDocument()).getDocId(),
						previous.getGID());
				long address = previous.getInternalAddress();
				if (address > -1)
					address = domDb.insertAfter(address, data);
				else
					address = domDb.insertAfter(ref, data);
				node.setInternalAddress(address);
				return null;
			}
		}
		.run();
	}

	public String getId() {
		return "NativeBroker [" + Thread.currentThread().getName() + "]";
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	final static class ElementValue extends Value {

		ElementValue(short collectionId, String elementName) {
			super(new byte[1]);
			byte[] ed;
			try {
				ed = elementName.getBytes("UTF-8");
			} catch (UnsupportedEncodingException uee) {
				ed = elementName.getBytes();
			}
			len = ed.length + 2;
			data = new byte[len];
			ByteConversion.shortToByte(collectionId, data, 0);
			System.arraycopy(ed, 0, data, 2, ed.length);
			pos = 0;
		}

		ElementValue(short collectionId) {
			data = new byte[2];
			ByteConversion.shortToByte(collectionId, data, 0);
			len = 2;
			pos = 0;
		}

		ElementValue(short collectionId, short symbol) {
			len = 4;
			data = new byte[len];
			ByteConversion.shortToByte(collectionId, data, 0);
			ByteConversion.shortToByte(symbol, data, 2);
			pos = 0;
		}

		ElementValue(short collectionId, short symbol, short partition) {
			len = 6;
			data = new byte[len];
			ByteConversion.shortToByte(collectionId, data, 0);
			ByteConversion.shortToByte(symbol, data, 2);
			ByteConversion.shortToByte(partition, data, 4);
			pos = 0;
		}

		short getCollectionId() {
			return ByteConversion.byteToShort(data, 0);
		}

		String getElementName() {
			return new String(data, 2, len - 2);
		}
	}

	/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    3. Juni 2002
	 */
	final static class NodeRef extends Value {

		NodeRef() {
			data = new byte[12];
		}

		NodeRef(int docId, long gid) {
			data = new byte[12];
			ByteConversion.intToByte(docId, data, 0);
			ByteConversion.longToByte(gid, data, 4);
			len = 12;
			pos = 0;
		}

		NodeRef(int docId) {
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
