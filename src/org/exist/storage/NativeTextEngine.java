/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001/2002 Wolfgang M. Meier
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

import it.unimi.dsi.fastUtil.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastUtil.Object2ObjectRBTreeMap;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Category;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.dbxml.core.DBException;
import org.dbxml.core.data.Value;
import org.dbxml.core.filer.BTreeCallback;
import org.dbxml.core.filer.BTreeException;
import org.dbxml.core.indexer.IndexQuery;
import org.exist.dom.ArraySet;
import org.exist.dom.AttrImpl;
import org.exist.dom.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.TextImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.analysis.TextToken;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.LongLinkedList;
import org.exist.util.Occurrences;
import org.exist.util.OrderedLongLinkedList;
import org.exist.util.ProgressIndicator;
import org.exist.util.ReadOnlyException;
import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  This class is responsible for fulltext-indexing. Text-nodes are handed over
 *  to this class to be fulltext-indexed. Method storeText() is called by
 *  RelationalBroker whenever it finds a TextNode. Method getNodeIDsContaining()
 *  is used by the XPath-engine to process queries where a fulltext-operator is
 *  involved. The class keeps two database tables: table words stores the words
 *  found with their unique id. Table inv_idx contains the word occurrences for
 *  every word-id per document.
 *
 *@author     Wolfgang Meier
 *@created    25. Mai 2002
 */
public class NativeTextEngine extends TextSearchEngine {

	private static Category LOG =
		Category.getInstance(NativeTextEngine.class.getName());
	protected BFile dbWords;
	protected InvertedIndex invIdx;
	protected boolean useCompression = false;
	protected PatternCompiler regexCompiler = new Perl5Compiler();
	protected PatternCompiler globCompiler = new GlobCompiler();
	protected PatternMatcher matcher = new Perl5Matcher();

	public NativeTextEngine(DBBroker broker, Configuration config) {
		super(broker, config);
		String dataDir;
		String temp;
		int buffers;
		boolean compress = false;
		if ((dataDir = (String) config.getProperty("db-connection.data-dir"))
			== null)
			dataDir = "data";

		if ((buffers = config.getInteger("db-connection.words.buffers")) < 0)
			buffers = 1024;

		if ((temp = (String) config.getProperty("db-connection.compress"))
			!= null)
			compress = temp.equals("true");

		String pathSep = System.getProperty("file.separator", "/");
		try {
			if ((dbWords = (BFile) config.getProperty("db-connection.words"))
				== null) {
				dbWords =
					new BFile(
						new File(dataDir + pathSep + "words.dbx"),
						buffers / 2,
						buffers);
				LOG.debug("words index buffer size: " + buffers);
				if (!dbWords.exists())
					dbWords.create();
				else
					dbWords.open();

				dbWords.setCompression(compress);
				config.setProperty("db-connection.words", dbWords);
			}
			invIdx = new InvertedIndex();
		} catch (BTreeException bte) {
			LOG.warn(bte);
		} catch (DBException dbe) {
			LOG.warn(dbe);
		}
	}

	/**
	 *  check if string contains non-letters (maybe it's a regular expression?
	 *
	 *@param  str  Description of the Parameter
	 *@return      Description of the Return Value
	 */
	public final static boolean containsWildcards(String str) {
		for (int i = 0; i < str.length(); i++)
			switch (str.charAt(i)) {
				case '*' :
				case '?' :
				case '\\' :
				case '[' :
				case ']' :
					return true;
			}
		return false;
	}

	public final static boolean startsWithWildcard(String str) {
		if (str == null || str.length() == 0)
			return false;
		switch (str.charAt(0)) {
			case '*' :
			case '?' :
			case '\\' :
			case '[' :
				return true;
		}
		return false;
	}

	public void close() {
		try {
			flush();
			sync();
			dbWords.close();
		} catch (DBException dbe) {
			LOG.debug(dbe);
		}
	}

	/**
	 *  Collect all words in a document to be removed
	 *
	 *@param  words        Description of the Parameter
	 *@param  domIterator  Description of the Parameter
	 */
	protected void collect(HashSet words, Iterator domIterator) {
		byte[] data = ((Value) domIterator.next()).getData();
		short type = Signatures.getType(data[0]);
		String word;
		TextToken token;
		switch (type) {
			case Node.ELEMENT_NODE :
				int children = ByteConversion.byteToInt(data, 1);
				for (int i = 0; i < children; i++)
					collect(words, domIterator);

				break;
			case Node.TEXT_NODE :
				String s;
				try {
					s = new String(data, 1, data.length - 1, "UTF-8");
				} catch (UnsupportedEncodingException uee) {
					s = new String(data, 1, data.length - 1);
				}
				tokenizer.setText(s);
				while (null != (token = tokenizer.nextToken())) {
					word = token.getText();
					if (stoplist.contains(word))
						continue;
					words.add(word);
				}
				break;
			case Node.ATTRIBUTE_NODE :
				byte idSizeType = (byte) (data[0] & 0x3);
				String val;
				try {
					val =
						new String(
							data,
							1 + Signatures.getLength(idSizeType),
							data.length - 1 - Signatures.getLength(idSizeType),
							"UTF-8");
				} catch (UnsupportedEncodingException uee) {
					val =
						new String(
							data,
							1 + Signatures.getLength(idSizeType),
							data.length - 1 - Signatures.getLength(idSizeType));
				}
				tokenizer.setText(val);
				while (null != (token = tokenizer.nextToken())) {
					word = token.getText();
					if (stoplist.contains(word))
						continue;
					words.add(word);
				}
				break;
		}
	}

	public void flush() {
		invIdx.flush();
	}

	public void reindex(DocumentImpl oldDoc) {
		invIdx.reindex(oldDoc);
	}

	/**
	 *  Find    all the nodes containing the search terms given by the array
	 * expr from the fulltext-index.
	 *
	 * @param the input document set
	 * @param array of regular expression search terms
	 * @return array containing a NodeSet for each of the search terms
	 *
	 */
	public NodeSet[] getNodesContaining(DocumentSet docs, String[] expr) {
		return getNodesContaining(docs, expr, DBBroker.MATCH_EXACT);
	}

	/**
	 *  Get   all  the nodes containing the search terms given by the array expr
	 * using the fulltext-index.
	 *
	 * @param docs the input document set
	 * @param expr array of search terms
	 * @param type either MATCH_EXACT or MATCH_REGEX
	 * @return array containing a NodeSet for each of the search terms
	 */
	public NodeSet[] getNodesContaining(
		DocumentSet docs,
		String[] expr,
		int type) {
		if (type == DBBroker.MATCH_EXACT) {
			for (int i = 0; i < expr.length; i++)
				if (containsWildcards(expr[i])) {
					type = DBBroker.MATCH_WILDCARDS;
					break;
				}
		}
		switch (type) {
			case DBBroker.MATCH_EXACT :
				return getNodesExact(docs, expr);
			default :
				return getNodesRegexp(docs, expr, type);
		}
	}

	/**
	 *  Get all nodes whose content exactly matches the terms passed
	 * in expr. Called by method getNodesContaining.
	 *
	 * @param the input document set
	 * @param array of regular expression search terms
	 * @return array containing a NodeSet for each of the search terms
	 */
	public NodeSet[] getNodesExact(DocumentSet docs, String[] expr) {
		long start = System.currentTimeMillis();
		ArraySet[] result = new ArraySet[expr.length];
		DocumentImpl doc;
		Value ref;
		Value value;
		byte[] data;
		long gid;
		int docId;
		int len;
		long last;
		long delta;
		String term;
		Collection collection;
		short collectionId;
		VariableByteInputStream is;
		Lock lock = dbWords.getLock();
		for (int i = 0; i < expr.length; i++) {
			if (expr[i] == null)
				continue;
			if (stoplist.contains(expr[i]))
				continue;
			term =
				(stem)
					? stemmer.stem(expr[i].toLowerCase())
					: expr[i].toLowerCase();
			result[i] = new ArraySet(1000);
			for (Iterator iter = docs.getCollectionIterator();
				iter.hasNext();
				) {
				collection = (Collection) iter.next();
				collectionId = collection.getId();

				ref = new WordRef(collectionId, term);
				try {
					lock.acquire(this);
					lock.enter(this);
					value = dbWords.get(ref);
				} catch (LockException e) {
					LOG.warn("could not acquire lock on words db", e);
					value = null;
				} finally {
					lock.release(this);
				}
				if (value == null) {
					continue;
				}

				data = value.getData();
				is = new VariableByteInputStream(data);
				try {
					while (is.available() > 0) {
						docId = is.readInt();
						len = is.readInt();
						if ((doc = docs.getDoc(docId)) == null) {
							is.skip(len);
							continue;
						}
						last = 0;
						for (int j = 0; j < len; j++) {
							delta = is.readLong();
							gid = last + delta;
							last = gid;
							result[i].add(
								new NodeProxy(doc, gid, Node.TEXT_NODE));
						}
					}
				} catch (EOFException e) {
				}
			}
			//( (ArraySet) result[i] ).setIsSorted( true );
						LOG.debug(
							"found: "
								+ result[i].getLength()
								+ " in "
								+ (System.currentTimeMillis() - start)
								+ "ms.");
		}
		return result;
	}

	/**
	 * Return all nodes whose content matches any of the search terms
	 * in expr. This method interprets the search terms as regular 
	 * expressions and matches them against all indexed words.
	 * 
	 * @param the input document set
	 * @param array of regular expression search terms
	 * @return array containing a NodeSet for each of the search terms
	 */
	private NodeSet[] getNodesRegexp(
		DocumentSet docs,
		String[] expr,
		int type) {
		long start = System.currentTimeMillis();
		ArraySet[] result = new ArraySet[expr.length];
		Value ref;
		StringBuffer term;
		Collection collection;
		short collectionId;
		Pattern regexp;
		WordsCallback cb;
		Lock lock = dbWords.getLock();
		for (int i = 0; i < expr.length; i++) {
			if (expr[i] == null)
				continue;
			if (stoplist.contains(expr[i]))
				continue;
			expr[i] = expr[i].toLowerCase();
			result[i] = new ArraySet(1000);
			try {
				regexp =
					(type == DBBroker.MATCH_REGEXP
						? regexCompiler.compile(
							expr[i],
							Perl5Compiler.CASE_INSENSITIVE_MASK)
						: globCompiler.compile(
							expr[i],
							GlobCompiler.CASE_INSENSITIVE_MASK
								| GlobCompiler
									.QUESTION_MATCHES_ZERO_OR_ONE_MASK));
			} catch (MalformedPatternException e) {
				LOG.debug(e);
				continue;
			}
			term = new StringBuffer();
			for (int j = 0; j < expr[i].length(); j++)
				if (Character.isLetterOrDigit(expr[i].charAt(j)))
					term.append(expr[i].charAt(j));
				else
					break;
			cb = new WordsCallback(regexp, result[i], docs);
			for (Iterator iter = docs.getCollectionIterator();
				iter.hasNext();
				) {
				collection = (Collection) iter.next();
				collectionId = collection.getId();

				if (term.length() > 0)
					ref = new WordRef(collectionId, term.toString());
				else
					ref = new WordRef(collectionId);

				IndexQuery query =
					new IndexQuery(null, IndexQuery.TRUNC_RIGHT, ref);
				try {
					lock.acquire(this);
					lock.enter(this);
					try {
						dbWords.query(query, cb);
					} catch (IOException ioe) {
						LOG.debug(ioe);
					} catch (BTreeException bte) {
						LOG.debug(bte);
					}
				} catch (LockException e) {
					LOG.debug(e);
				} finally {
					lock.release(this);
				}
			}
			LOG.debug(
				"regexp found: "
					+ result[i].getLength()
					+ " in "
					+ (System.currentTimeMillis() - start)
					+ "ms.");
		}
		return result;
	}

	public Occurrences[] scanIndexTerms(
		User user,
		Collection collection,
		String start,
		String end,
		boolean inclusive) 
		throws PermissionDeniedException {
		if(!collection.getPermissions().validate(user, Permission.READ))
			throw new PermissionDeniedException("permission denied");
		List collections =
			inclusive ? collection.getDescendants(user) : new ArrayList();
		collections.add(collection);
		final Lock lock = dbWords.getLock();
		short collectionId;
		Collection current;
		IndexQuery query;
		ArrayList values;
		Value[] val;
		String term;
		Object2ObjectAVLTreeMap map = new Object2ObjectAVLTreeMap();
		Occurrences oc;
		VariableByteInputStream is;
		int docId;
		int len;
		for (Iterator i = collections.iterator(); i.hasNext();) {
			current = (Collection) i.next();
			collectionId = current.getId();
			query =
				new IndexQuery(
					null,
					IndexQuery.BW,
					new WordRef(collectionId, start),
					new WordRef(collectionId, end));
			try {
				lock.acquire(this);
				lock.enter(this);
				values = dbWords.findEntries(query);
				for (Iterator j = values.iterator(); j.hasNext();) {
					val = (Value[]) j.next();
					term =
						new String(
							val[0].getData(),
							2,
							val[0].getLength() - 2,
							"UTF-8");
					oc = (Occurrences) map.get(term);
					if (oc == null) {
						oc = new Occurrences(term);
						map.put(term, oc);
					}
					is = new VariableByteInputStream(val[1].getData());
					try {
						while (is.available() > 0) {
							docId = is.readInt();
							len = is.readInt();
							is.skip(len);
							oc.addOccurrences(len);
						}
					} catch (EOFException e) {
					}
				}
			} catch (LockException e) {
				LOG.warn("cannot get lock on words", e);
			} catch (IOException e) {
				LOG.warn("error while reading words", e);
			} catch (BTreeException e) {
				LOG.warn("error while reading words", e);
			} finally {
				lock.release(this);
			}
		}
		Occurrences[] result = new Occurrences[map.size()];
		return (Occurrences[])map.values().toArray(result);
	}

	/**
	 *  Remove indexed words for entire collection
	 *
	 *@param  collection  Description of the Parameter
	 */
	public void removeCollection(Collection collection) {
		Lock lock = dbWords.getLock();
		try {
			try {
				lock.acquire(this, Lock.WRITE_LOCK);
				lock.enter(this);
				dbWords.flush();
			} catch (LockException e) {
				LOG.warn("could not acquire lock on words db", e);
				return;
			} finally {
				lock.release(this);
			}
			LOG.debug("removing words ...");
			WordRef ref = new WordRef(collection.getId());
			IndexQuery query =
				new IndexQuery(null, IndexQuery.TRUNC_RIGHT, ref);

			ArrayList entries = null;
			try {
				lock.acquire(this, Lock.WRITE_LOCK);
				lock.enter(this);
				entries = dbWords.findKeys(query);
			} catch (LockException e) {
				LOG.warn("could not acquire lock on words db", e);
				entries = null;
			} finally {
				lock.release(this);
			}
			if (entries == null) {
				LOG.error("could not remove collection");
				return;
			}
			LOG.debug("found " + entries.size() + " words.");
			Value val;
			for (Iterator i = entries.iterator(); i.hasNext();) {
				val = (Value) i.next();
				try {
					lock.acquire(this, Lock.WRITE_LOCK);
					lock.enter(this);
					//dbWords.remove(val);
					dbWords.remove(val.getAddress());
					dbWords.removeValue(val);
				} catch (LockException e) {
					LOG.warn("could not acquire lock on words db", e);
				} finally {
					lock.release(this);
				}
			}
			LOG.debug("removed words index");
		} catch (BTreeException bte) {
			LOG.debug(bte);
		} catch (IOException ioe) {
			LOG.debug(ioe);
		} catch (DBException dbe) {
			LOG.warn(dbe);
		} catch (ReadOnlyException e) {
			LOG.warn("database is read-only");
		}
	}

	/**
	 *  Remove all index entries for the specified document
	 *
	 *@param  doc  The document
	 */
	public void removeDocument(DocumentImpl doc) {
		LOG.debug("removing text index ...");
		try {
			HashSet words = new HashSet();
			NodeList children = doc.getChildNodes();
			NodeImpl node;
			for (int i = 0; i < children.getLength(); i++) {
				node = (NodeImpl) children.item(i);
				Iterator j = broker.getDOMIterator(doc, node.getGID());
				collect(words, j);
			}
			String word;
			Value val;
			WordRef ref;
			byte[] data;
			byte[] ndata;
			VariableByteInputStream is;
			VariableByteOutputStream os;
			int len;
			int docId;
			long delta;
			short collectionId = doc.getCollection().getId();
			boolean changed;
			Lock lock = dbWords.getLock();
			for (Iterator iter = words.iterator(); iter.hasNext();) {
				word = (String) iter.next();
				ref = new WordRef(collectionId, word);
				try {
					lock.acquire(this, Lock.WRITE_LOCK);
					lock.enter(this);
					val = dbWords.get(ref);
				} catch (LockException e) {
					LOG.warn("could not acquire lock on words db", e);
					val = null;
				} finally {
					lock.release(this);
				}
				if (val == null)
					continue;
				data = val.getData();
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
								os.writeLong(delta);
							}
						} else {
							changed = true;
							// skip
							for (int j = 0; j < len; j++)
								delta = is.readLong();
						}
					}
				} catch (EOFException e) {
				}
				if (changed) {
					try {
						lock.acquire(this, Lock.WRITE_LOCK);
						lock.enter(this);
						ndata = os.toByteArray();
						if (ndata.length == 0) {
							dbWords.remove(ref);
						} else {
							if (!dbWords.put(ref, ndata))
								LOG.debug("could not remove index for " + word);
						}
					} catch (LockException e) {
						LOG.warn("could not acquire lock on words db", e);
					} finally {
						lock.release(this);
					}
				}
			}
			LOG.debug(words.size() + " words updated.");
		} catch (ReadOnlyException e) {
			LOG.warn("database is read-only");
		}
	}

	/**
	 *  Index an attribute value
	 *
	 *@param  attr  the attribute to be indexed
	 */
	public void storeAttribute(IndexPaths idx, AttrImpl attr) {
		final DocumentImpl doc = (DocumentImpl) attr.getOwnerDocument();
		tokenizer.setText(attr.getValue());
		String word;
		TextToken token;
		final long gid = attr.getGID();
		while (null != (token = tokenizer.nextToken())) {
			if (idx != null
				&& idx.getIncludeAlphaNum() == false
				&& token.getType() == TextToken.ALPHANUM)
				continue;
			word = token.getText().toLowerCase();
			if (stoplist.contains(word))
				continue;
			invIdx.setDocument(doc);
			invIdx.addRow(word, gid);
		}
	}

	/**
	 *  Index a text node
	 *
	 *@param idx IndexPaths object passed in by the broker
	 *@param  text  the text node to be indexed
	 */
	public void storeText(IndexPaths idx, TextImpl text) {
		final DocumentImpl doc = (DocumentImpl) text.getOwnerDocument();
		tokenizer.setText(text.getData());
		TextToken token;
		String word;
		final long gid = text.getGID();
		while (null != (token = tokenizer.nextToken())) {
			if (idx != null
				&& idx.getIncludeAlphaNum() == false
				&& token.isAlpha() == false)
				continue;
			word = token.getText().toLowerCase();
			//System.out.println( "'" + word + "'");
			if (stoplist.contains(word))
				continue;
			invIdx.setDocument(doc);
			invIdx.addRow(word, gid);
		}
	}

	public void sync() {
		// uncomment this to get statistics about page buffer usage
		dbWords.printStatistics();
		Lock lock = dbWords.getLock();
		try {
			lock.acquire(this, Lock.WRITE_LOCK);
			lock.enter(this);
			try {
				dbWords.flush();
			} catch (DBException dbe) {
				LOG.warn(dbe);
			}
		} catch (LockException e) {
			LOG.warn("could not acquire lock on words db", e);
		} finally {
			lock.release(this);
		}
	}

	final static class WordRef extends Value {

		public WordRef() {
			this(512);
		}

		public WordRef(int size) {
			data = new byte[size];
		}

		public WordRef(short collectionId) {
			this();
			set(collectionId);
		}

		public WordRef(short collectionId, String word) {
			this();
			set(collectionId, word);
		}

		public final void set(short collectionId) {
			ByteConversion.shortToByte(collectionId, data, 0);
			len = 2;
			pos = 0;
		}

		public final void set(short collectionId, String word) {
			ByteConversion.shortToByte(collectionId, data, 0);
			len = 2;
			writeChars(word);
			pos = 0;
		}

		private final void writeChars(String s) {
			final int slen = s.length();
			for (int i = 0; i < slen; i++) {
				final int code = (int) s.charAt(i);
				if (code >= 0x01 && code <= 0x7F)
					data[len++] = (byte) code;
				else if (((code >= 0x80) && (code <= 0x7FF)) || code == 0) {
					data[len++] = (byte) (0xC0 | (code >> 6));
					data[len++] = (byte) (0x80 | (code & 0x3F));
				} else {
					data[len++] = (byte) (0xE0 | (code >>> 12));
					data[len++] = (byte) (0x80 | ((code >> 6) & 0x3F));
					data[len++] = (byte) (0x80 | (code & 0x3F));
				}
			}
		}

		/**
		 * @see org.dbxml.core.data.Value#streamTo(java.io.OutputStream)
		 */
		public void streamTo(OutputStream out) throws IOException {
			super.streamTo(out);
		}

		/**
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return ByteConversion.byteToShort(data, pos)
				+ new String(data, pos, len);
		}

	}

	//	final static class WordRef extends Value {
	//
	//		WordRef(short collectionId) {
	//			data = new byte[2];
	//			ByteConversion.shortToByte(collectionId, data, 0);
	//			len = 2;
	//			pos = 0;
	//		}
	//
	//		WordRef(short collectionId, String word) {
	//			byte[] ndata;
	//			try {
	//				ndata = word.getBytes("UTF-8");
	//			} catch (UnsupportedEncodingException uee) {
	//				ndata = word.getBytes();
	//			}
	//			data = new byte[2 + ndata.length];
	//			ByteConversion.shortToByte(collectionId, data, 0);
	//			System.arraycopy(ndata, 0, data, 2, ndata.length);
	//			len = data.length;
	//			pos = 0;
	//		}
	//	}

	/**
	 *  This inner class is responsible for actually storing the list of
	 *  occurrences.
	 *
	 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
	 *@created    25. Mai 2002
	 */
	class InvertedIndex {
		private final int MAX_BUF = 100;

		protected DocumentImpl doc = null;
		protected boolean flushed = false;
		//protected TreeMap words = new TreeMap();
		private Object2ObjectRBTreeMap words = new Object2ObjectRBTreeMap();
		private VariableByteOutputStream os = new VariableByteOutputStream();
		private WordRef reusableWordRef = new WordRef(512);
		private long currentSize = 0;

		public InvertedIndex() {
		}

		public void addRow(String word, long gid) {
			LongLinkedList buf;
			if (words.containsKey(word)) {
				buf = (OrderedLongLinkedList) words.get(word);
			} else {
				buf = new OrderedLongLinkedList();
				words.put(word, buf);
			}
			buf.add(gid);
		}

		public void reindex(DocumentImpl oldDoc) {
			final short collectionId = doc.getCollection().getId();
			int count = 1, len;
			Map.Entry entry;
			String word;
			LongLinkedList idList;
			long[] ids;
			byte[] data;
			long prevId;
			long delta;
			NodeSet oldList;
			DocumentSet docs;
			NodeProxy p;
			WordRef ref;
			Lock lock;
			String[] terms = new String[1];
			for (Iterator i = words.entrySet().iterator();
				i.hasNext();
				count++) {
				entry = (Map.Entry) i.next();
				word = (String) entry.getKey();
				terms[0] = word;
				idList = (LongLinkedList) entry.getValue();
				docs = new DocumentSet();
				docs.add(oldDoc);
				oldList = getNodesExact(docs, terms)[0];
				for (Iterator j = oldList.iterator(); j.hasNext();) {
					p = (NodeProxy) j.next();
					if (oldDoc.getTreeLevel(p.gid) < oldDoc.reindexRequired())
						idList.add(p.gid);
				}
				ids = idList.getData();
				i.remove();
				Arrays.sort(ids);
				len = ids.length;
				os.writeInt(doc.getDocId());
				os.writeInt(len);
				prevId = 0;
				for (int j = 0; j < len; j++) {
					delta = ids[j] - prevId;
					if (delta < 0) {
						LOG.debug("neg. delta: " + delta + " for " + word);
						LOG.debug("id = " + ids[j] + "; prev = " + prevId);
					}
					os.writeLong(delta);
					prevId = ids[j];
				}
				data = os.toByteArray();
				os.clear();
				ref = new WordRef(collectionId, word);
				lock = dbWords.getLock();
				try {
					lock.acquire(this, Lock.WRITE_LOCK);
					lock.enter(this);
					try {
						dbWords.put(ref, data);
					} catch (ReadOnlyException e) {
					}
				} catch (LockException e) {
					LOG.warn("could not acquire lock", e);
				} finally {
					lock.release(this);
				}
			}
			//words = new TreeMap();
			words.clear();
		}

		public void flush() {
			final ProgressIndicator progress =
				new ProgressIndicator(words.size());
			if (doc == null)
				return;
			final short collectionId = doc.getCollection().getId();
			int count = 1, len;
			Map.Entry entry;
			String word;
			LongLinkedList idList;
			long[] ids;
			byte[] data;
			long prevId, id;
			long delta;
			for (Iterator i = words.entrySet().iterator();
				i.hasNext();
				count++) {
				entry = (Map.Entry) i.next();
				word = (String) entry.getKey();
				idList = (LongLinkedList) entry.getValue();
				i.remove();
				len = idList.getSize();
				os.writeInt(doc.getDocId());
				os.writeInt(len);
				prevId = 0;
				for (Iterator j = idList.iterator(); j.hasNext();) {
					id = ((LongLinkedList.ListItem) j.next()).l;
					delta = id - prevId;
					if (delta < 0) {
						LOG.debug("neg. delta: " + delta + " for " + word);
						LOG.debug("id = " + id + "; prev = " + prevId);
					}
					os.writeLong(delta);
					prevId = id;
				}
				data = os.toByteArray();
				os.clear();
				flushWord(collectionId, word, data);
				progress.setValue(count);
				setChanged();
				notifyObservers(progress);
			}
			words.clear();
			//words = new TreeMap();
		}

		private void flushWord(short collectionId, String word, byte[] data) {
			if (data.length == 0)
				return;
			// if data has already been written to the table,
			// we may need to do updates.
			//final WordRef ref = new WordRef(collectionId, word);
			reusableWordRef.set(collectionId, word);
			Lock lock = dbWords.getLock();
			try {
				lock.acquire(this, Lock.WRITE_LOCK);
				lock.enter(this);
				try {
					dbWords.append(reusableWordRef, data);
				} catch (ReadOnlyException e) {
				}
			} catch (LockException e) {
				LOG.warn("could not acquire lock", e);
			} finally {
				lock.release(this);
			}
		}

		public void setDocument(DocumentImpl doc) {
			if (this.doc != null && this.doc.getDocId() != doc.getDocId())
				flush();

			this.doc = doc;
		}
	}

	private class WordsCallback implements BTreeCallback {
		DocumentSet docs;

		Pattern regexp;
		ArraySet result;

		public WordsCallback(
			Pattern regexp,
			ArraySet result,
			DocumentSet docs) {
			this.regexp = regexp;
			this.result = result;
			this.docs = docs;
		}

		public boolean indexInfo(Value key, long pointer) {
			String word;
			try {
				word =
					new String(key.getData(), 2, key.getLength() - 2, "UTF-8");
			} catch (UnsupportedEncodingException uee) {
				word = new String(key.getData(), 2, key.getLength() - 2);
			}
			if (matcher.matches(word, regexp)) {
				//				LOG.debug("found: " + word);
				Value value = dbWords.get(pointer);
				if (value == null)
					return true;
				byte[] data = value.getData();
				int k = 0;
				int docId;
				int len;
				long gid;
				long last = -1;
				long delta;
				DocumentImpl doc;
				VariableByteInputStream is = new VariableByteInputStream(data);
				try {
					while (is.available() > 0) {
						docId = is.readInt();
						len = is.readInt();
						if ((doc = docs.getDoc(docId)) == null) {
							is.skip(len);
							continue;
						}
						last = -1;
						for (int j = 0; j < len; j++) {
							delta = is.readLong();
							gid = (last < 0 ? delta : last + delta);
							last = gid;
							result.add(new NodeProxy(doc, gid, Node.TEXT_NODE));
						}
					}
				} catch (EOFException e) {
					LOG.warn(e);
				}
			}
			return true;
		}
	}
}
