/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001/2002 Wolfgang M. Meier
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

import it.unimi.dsi.fastutil.Object2ObjectAVLTreeMap;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import org.exist.collections.Collection;
import org.exist.dom.AVLTreeNodeSet;
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.Match;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.TextImpl;
import org.exist.dom.TextSearchResult;
import org.exist.dom.XMLUtil;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.analysis.TextToken;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.store.BFile;
import org.exist.util.ByteArray;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.LongLinkedList;
import org.exist.util.Occurrences;
import org.exist.util.OrderedLongLinkedList;
import org.exist.util.ProgressIndicator;
import org.exist.util.ReadOnlyException;
import org.exist.util.UTF8;
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
	protected int trackMatches = Serializer.TAG_ELEMENT_MATCHES;

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

		temp =
			(String) config.getProperty("serialization.match-tagging-elements");
		if (temp != null)
			trackMatches =
				temp.equalsIgnoreCase("true")
					? Serializer.TAG_ELEMENT_MATCHES
					: Serializer.TAG_NONE;
		temp =
			(String) config.getProperty(
				"serialization.match-tagging-attributes");
		if (temp != null && temp.equalsIgnoreCase("true"))
			trackMatches = trackMatches | Serializer.TAG_ATTRIBUTE_MATCHES;

		String pathSep = System.getProperty("file.separator", "/");
		try {
			if ((dbWords = (BFile) config.getProperty("db-connection.words"))
				== null) {
				dbWords =
					new BFile(
						new File(dataDir + pathSep + "words.dbx"),
						buffers,
						128);
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
					word = token.getText().toString();
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
					word = token.getText().toString();
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

	public void reindex(DocumentImpl oldDoc, NodeImpl node) {
		invIdx.reindex(oldDoc, node);
	}

	public void remove() {
		invIdx.remove();
	}

	public NodeSet getNodesContaining(
		DocumentSet docs,
		NodeSet context,
		String expr,
		int type) {
		if (type == DBBroker.MATCH_EXACT && containsWildcards(expr)) {
			type = DBBroker.MATCH_WILDCARDS;
		}
		switch (type) {
			case DBBroker.MATCH_EXACT :
				return getNodesExact(docs, context, expr);
			default :
				return getNodesRegexp(docs, context, expr, type);
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
	public NodeSet getNodesExact(
		DocumentSet docs,
		NodeSet context,
		String expr) {
		if (expr == null)
			return null;
		if (stoplist.contains(expr))
			return null;
		long start = System.currentTimeMillis();
		DocumentImpl doc;
		Value ref;
		byte[] data;
		long gid;
		int docId;
		int len;
		int sizeHint = -1;
		long last;
		long delta;
		Collection collection;
		short collectionId;
		VariableByteInputStream is;
		InputStream dis = null;
		NodeProxy parent, temp = new NodeProxy();
		NodeSet result;
		if (context == null)
			result = new TextSearchResult(trackMatches != Serializer.TAG_NONE);
		else
			result = new ExtArrayNodeSet(250);
		String term =
			(stem) ? stemmer.stem(expr.toLowerCase()) : expr.toLowerCase();
		int count = 0;
		for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
			collection = (Collection) iter.next();
			collectionId = collection.getId();

			ref = new WordRef(collectionId, term);
			Lock lock = dbWords.getLock();
			try {
				lock.acquire();
				dis = dbWords.getAsStream(ref);

				if (dis == null) {
					continue;
				}

				is = new VariableByteInputStream(dis);
				while (is.available() > 0) {
					docId = is.readInt();
					len = is.readInt();
					if ((doc = docs.getDoc(docId)) == null
						|| (context != null && !context.containsDoc(doc))) {
						is.skip(len);
						continue;
					}
					if(context != null)
						sizeHint = context.getSizeHint(doc);
					last = 0;
					for (int j = 0; j < len; j++) {
						delta = is.readLong();
						gid = last + delta;
						last = gid;
						count++;
						if (context != null) {
							parent = context.parentWithChild(doc, gid, false, true, -1);
							if (parent != null) {
								result.add(parent, sizeHint);
								if (trackMatches != Serializer.TAG_NONE)
									parent.addMatch(new Match(term, gid));
							}
						} else
							 ((TextSearchResult) result).add(doc, gid, term);
					}
				}
			} catch (EOFException e) {
			} catch (LockException e) {
				LOG.warn("could not acquire lock on words db", e);
			} catch (IOException e) {
				LOG.warn("io error while reading words", e);
			} finally {
				lock.release();
			}
		}
		if(context != null)
			((ExtArrayNodeSet)result).sort();
		LOG.debug(
			"found " + expr + ": "
				+ result.getLength() + " (" + count + ") "
				+ " in "
				+ (System.currentTimeMillis() - start)
				+ "ms.");
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
	private NodeSet getNodesRegexp(
		DocumentSet docs,
		NodeSet context,
		String expr,
		int type) {
		long start = System.currentTimeMillis();
		NodeSet result;
		if (context == null)
			result = new TextSearchResult(trackMatches != Serializer.TAG_NONE);
		else
			result = new ExtArrayNodeSet();
		Value ref;
		StringBuffer term;
		Collection collection;
		short collectionId;
		Pattern regexp;
		WordsCallback cb;
		Lock lock = dbWords.getLock();
		if (expr == null)
			return null;
		if (stoplist.contains(expr))
			return null;
		expr = expr.toLowerCase();
		try {
			regexp =
				(type == DBBroker.MATCH_REGEXP
					? regexCompiler.compile(
						expr,
						Perl5Compiler.CASE_INSENSITIVE_MASK)
					: globCompiler.compile(
						expr,
						GlobCompiler.CASE_INSENSITIVE_MASK
							| GlobCompiler.QUESTION_MATCHES_ZERO_OR_ONE_MASK));
		} catch (MalformedPatternException e) {
			LOG.debug(e);
			return result;
		}
		term = new StringBuffer();
		for (int j = 0; j < expr.length(); j++)
			if (Character.isLetterOrDigit(expr.charAt(j)))
				term.append(expr.charAt(j));
			else
				break;
		cb = new WordsCallback(regexp, result, context, docs);
		for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
			collection = (Collection) iter.next();
			collectionId = collection.getId();

			if (term.length() > 0)
				ref = new WordRef(collectionId, term.toString());
			else
				ref = new WordRef(collectionId);

			IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
			try {
				lock.acquire();
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
				lock.release();
			}
		}
		LOG.debug(
			"regexp found: "
				+ result.getLength()
				+ " in "
				+ (System.currentTimeMillis() - start)
				+ "ms.");
		return result;
	}

	public Occurrences[] scanIndexTerms(
		User user,
		Collection collection,
		String start,
		String end,
		boolean inclusive)
		throws PermissionDeniedException {
		if (!collection.getPermissions().validate(user, Permission.READ))
			throw new PermissionDeniedException("permission denied");
		List collections =
			inclusive
				? collection.getDescendants(broker, user)
				: new ArrayList();
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
					IndexQuery.BW,
					new WordRef(collectionId, start),
					new WordRef(collectionId, end));
			try {
				lock.acquire();
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
				lock.release();
			}
		}
		Occurrences[] result = new Occurrences[map.size()];
		return (Occurrences[]) map.values().toArray(result);
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
				lock.acquire(Lock.WRITE_LOCK);
				dbWords.flush();
			} catch (LockException e) {
				LOG.warn("could not acquire lock on words db", e);
				return;
			} finally {
				lock.release();
			}
			LOG.debug("removing words ...");
			WordRef ref = new WordRef(collection.getId());
			IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);

			ArrayList entries = null;
			try {
				lock.acquire(Lock.WRITE_LOCK);
				dbWords.removeAll(query);
			} catch (LockException e) {
				LOG.warn("could not acquire lock on words db", e);
				entries = null;
			} finally {
				lock.release();
			}
			LOG.debug("removed words index");
		} catch (BTreeException bte) {
			LOG.debug(bte);
		} catch (IOException ioe) {
			LOG.debug(ioe);
		} catch (DBException dbe) {
			LOG.warn(dbe);
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
				Iterator j =
					broker.getDOMIterator(
						new NodeProxy(
							doc,
							node.getGID(),
							node.getInternalAddress()));
				collect(words, j);
			}
			String word;
			Value val;
			WordRef ref;
			VariableByteInputStream is;
			InputStream dis;
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
					lock.acquire(Lock.READ_LOCK);
					dis = dbWords.getAsStream(ref);
				} catch (LockException e) {
					LOG.warn("could not acquire lock on words db", e);
					dis = null;
				} catch (IOException e) {
					LOG.error("io error while reading words", e);
					dis = null;
				} finally {
					lock.release();
				}
				if (dis == null)
					continue;
				is = new VariableByteInputStream(dis);
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
							is.copyTo(os, len);
						} else {
							changed = true;
							// skip
							is.skip(len);
						}
					}
				} catch (EOFException e) {
				} catch (IOException e) {
				}
				if (changed) {
					try {
						lock.acquire(Lock.WRITE_LOCK);
						if (os.data().size() == 0) {
							dbWords.remove(ref);
						} else {
							if (dbWords.put(ref, os.data()) < 0)
								LOG.debug("could not remove index for " + word);
						}
					} catch (LockException e) {
						LOG.warn("could not acquire lock on words db", e);
					} finally {
						lock.release();
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
				&& token.getType() == TextToken.ALPHANUM) {
				continue;
			}
			word = token.getText().toLowerCase();
			if (stoplist.contains(word)) {
				continue;
			}
			invIdx.setDocument(doc);
			invIdx.addRow(word, gid);
		}
	}

	/**
	 *  Index a text node
	 *
	 *@param idx IndexPaths object passed in by the broker
	 *@param  text  the text node to be indexed
	 *@return boolean indicates if all of the text content has been added
	 *to the index
	 */
	public void storeText(IndexPaths idx, TextImpl text) {
		final DocumentImpl doc = (DocumentImpl) text.getOwnerDocument();
		tokenizer.setText(text.getXMLString().transformToLower());
		TextToken token;
		String word;
		final long gid = text.getGID();
		while (null != (token = tokenizer.nextToken())) {
			if (idx != null
				&& idx.getIncludeAlphaNum() == false
				&& token.isAlpha() == false) {
				continue;
			}
			word = token.getText();
			if (stoplist.contains(word)) {
				continue;
			}
			invIdx.setDocument(doc);
			invIdx.addRow(word, gid);
		}
	}

	public void sync() {
		// uncomment this to get statistics about page buffer usage
		dbWords.printStatistics();
		Lock lock = dbWords.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			try {
				dbWords.flush();
			} catch (DBException dbe) {
				LOG.warn(dbe);
			}
		} catch (LockException e) {
			LOG.warn("could not acquire lock on words db", e);
		} finally {
			lock.release();
		}
	}

	private final static class WordRef extends Value {

		public WordRef(short collectionId) {
			data = new byte[2];
			ByteConversion.shortToByte(collectionId, data, 0);
			len = 2;
		}

		public WordRef(short collectionId, String word) {
			data = new byte[UTF8.encoded(word) + 2];
			ByteConversion.shortToByte(collectionId, data, 0);
			UTF8.encode(word, data, 2);
			len = data.length;
		}
		
		/**
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return ByteConversion.byteToShort(data, pos)
				+ new String(data, pos, len);
		}

	}

	/**
	 *  This inner class is responsible for actually storing the list of
	 *  occurrences.
	 *
	 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
	 *@created    25. Mai 2002
	 */
	class InvertedIndex {

		private DocumentImpl doc = null;
		private boolean flushed = false;
		private Object2ObjectAVLTreeMap words = new Object2ObjectAVLTreeMap();
		private VariableByteOutputStream os = new VariableByteOutputStream(7);

		public InvertedIndex() {
		}

		public void addRow(String word, long gid) {
			LongLinkedList buf = (LongLinkedList) words.get(word);
			if (buf == null) {
				buf = new OrderedLongLinkedList();
				words.put(word, buf);
			} else if(buf.getLast() == gid) {
				return; 	// double entry: skip
			}
			buf.add(gid);
		}

		public void remove() {
			if (doc == null)
				return;
			final short collectionId = doc.getCollection().getId();
			int len, docId;
			Map.Entry entry;
			String word;
			LongLinkedList idList;
			long[] ids;
			byte[] data;
			long last, gid;
			long delta;
			NodeProxy p;
			WordRef ref;
			LongLinkedList newList;
			Value val = null;
			VariableByteInputStream is;
			Lock lock = dbWords.getLock();
			for (Iterator i = words.entrySet().iterator(); i.hasNext();) {
				entry = (Map.Entry) i.next();
				word = (String) entry.getKey();
				idList = (LongLinkedList) entry.getValue();
				ref = new WordRef(collectionId, word);
				try {
					lock.acquire(Lock.READ_LOCK);
					val = dbWords.get(ref);
				} catch (LockException e) {
					LOG.error(
						"could not acquire lock on index for '" + word + "'");
				} finally {
					lock.release();
				}
				os.clear();
				newList = new LongLinkedList();
				if (val != null) {
					// add old entries to the new list 
					data = val.getData();
					is = new VariableByteInputStream(data);
					try {
						while (is.available() > 0) {
							docId = is.readInt();
							len = is.readInt();
							if (docId == doc.getDocId()) {
								// copy data to new buffer; skip
								// removed nodes
								last = 0;
								for (int j = 0; j < len; j++) {
									delta = is.readLong();
									last = last + delta;
									if (!idList.contains(last))
										newList.add(last);
								}
							} else {
								// section belongs to another document:
								// copy data to new buffer
								os.writeInt(docId);
								os.writeInt(len);
								for (int j = 0; j < len; j++)
									is.copyTo(os);
							}
						}
					} catch (EOFException e) {
						LOG.error(
							"end-of-file while reading index entry for "
								+ word);
					} catch (IOException e) {
						LOG.error(
							"io-error while reading index entry for " + word);
					}
				}
				ids = newList.getData();
				//i.remove();
				Arrays.sort(ids);
				len = ids.length;
				os.writeInt(doc.getDocId());
				os.writeInt(len);
				last = 0;
				for (int j = 0; j < len; j++) {
					delta = ids[j] - last;
					if (delta < 0) {
						LOG.debug("neg. delta: " + delta + " for " + word);
						LOG.debug("id = " + ids[j] + "; prev = " + last);
					}
					os.writeLong(delta);
					last = ids[j];
				}
				try {
					lock.acquire(Lock.WRITE_LOCK);
					try {
						if (val == null)
							dbWords.put(ref, os.data());
						else
							dbWords.update(val.getAddress(), ref, os.data());
					} catch (ReadOnlyException e) {
					}
				} catch (LockException e) {
					LOG.warn("could not acquire lock", e);
				} finally {
					lock.release();
				}
			}
			words.clear();
		}

		public void reindex(DocumentImpl oldDoc, NodeImpl node) {
			final short collectionId = doc.getCollection().getId();
			int len, docId;
			Map.Entry entry;
			String word;
			LongLinkedList idList;
			long[] ids;
			long last, gid;
			long delta;
			NodeProxy p;
			WordRef ref;
			VariableByteInputStream is = new VariableByteInputStream();
			InputStream dis = null;
			Lock lock = dbWords.getLock();
			for (Iterator i = words.entrySet().iterator(); i.hasNext();) {
				entry = (Map.Entry) i.next();
				word = (String) entry.getKey();
				idList = (LongLinkedList) entry.getValue();
				ref = new WordRef(collectionId, word);
				try {
					lock.acquire(Lock.READ_LOCK);
					dis = dbWords.getAsStream(ref);
				} catch (LockException e) {
					LOG.error(
						"could not acquire lock on index for '" + word + "'");
					dis = null;
				} catch (IOException e) {
					LOG.error("io error while reindexing word '" + word + "'");
					dis = null;
				} finally {
					lock.release();
				}
				os.clear();
				if (dis != null) {
					// add old entries to the new list 
					is.setInputStream(dis);
					try {
						while (dis.available() > 0) {
							docId = is.readInt();
							len = is.readInt();
							if (docId != oldDoc.getDocId()) {
								// section belongs to another document:
								// copy data to new buffer
								os.writeInt(docId);
								os.writeInt(len);
								is.copyTo(os, len);
							} else {
								// copy nodes to new list
								gid = 0;
								for (int j = 0; j < len; j++) {
									delta = is.readLong();
									gid += delta;
									if (node == null
										&& oldDoc.getTreeLevel(gid)
											< oldDoc.reindexRequired()) {
										idList.add(gid);
									} else if (
										node != null
											&& (!XMLUtil
												.isDescendantOrSelf(
													oldDoc,
													node.getGID(),
													gid))) {
										idList.add(gid);
									}
								}
							}
						}
					} catch (EOFException e) {
						//LOG.error("end-of-file while reading index entry for " + word, e);
					} catch (IOException e) {
						LOG.error(
							"io-error while reading index entry for " + word,
							e);
					}
				}
				ids = idList.getData();
				Arrays.sort(ids);
				len = ids.length;
				os.writeInt(oldDoc.getDocId());
				os.writeInt(len);
				last = 0;
				for (int j = 0; j < len; j++) {
					delta = ids[j] - last;
					if (delta < 0) {
						LOG.debug("neg. delta: " + delta + " for " + word);
						LOG.debug("id = " + ids[j] + "; prev = " + last);
					}
					os.writeLong(delta);
					last = ids[j];
				}
				try {
					lock.acquire(Lock.WRITE_LOCK);
					try {
						if (dis == null)
							dbWords.put(ref, os.data());
						else {
							dbWords.update(
								((BFile.PageInputStream) dis).getAddress(),
								ref,
								os.data());
						}
					} catch (ReadOnlyException e) {
					}
				} catch (LockException e) {
					LOG.warn("could not acquire lock", e);
				} finally {
					lock.release();
				}
			}
			words.clear();
		}

		public void flush() {
			final int wordsCount = words.size();
			if (doc == null || wordsCount == 0)
				return;
			final ProgressIndicator progress =
				new ProgressIndicator(wordsCount, 100);
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
				os.clear();
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
				flushWord(collectionId, word, os.data());
				progress.setValue(count);
				if (progress.changed()) {
					setChanged();
					notifyObservers(progress);
				}
			}
			if (wordsCount > 100) {
				progress.finish();
				setChanged();
				notifyObservers(progress);
			}
			words.clear();
		}

		private void flushWord(
			short collectionId,
			String word,
			ByteArray data) {
			if (data.size() == 0)
				return;
			// if data has already been written to the table,
			// we may need to do updates.
			final WordRef ref = new WordRef(collectionId, word);
			Lock lock = dbWords.getLock();
			try {
				lock.acquire(Lock.WRITE_LOCK);
				try {
					dbWords.append(ref, data);
				} catch (ReadOnlyException e) {
				} catch (IOException ioe) {
					LOG.warn("io error while writing '" + word + "'", ioe);
				}
			} catch (LockException e) {
				LOG.warn("could not acquire lock", e);
			} finally {
				lock.release();
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
		NodeSet result, context;
		NodeProxy proxy = new NodeProxy();

		public WordsCallback(
			Pattern regexp,
			NodeSet result,
			NodeSet context,
			DocumentSet docs) {
			this.regexp = regexp;
			this.result = result;
			this.docs = docs;
			this.context = context;
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
				InputStream dis = null;
				try {
					dis = dbWords.getAsStream(pointer);
				} catch (IOException ioe) {
					LOG.warn(ioe.getMessage(), ioe);
				}
				if (dis == null)
					return true;
				int k = 0;
				int docId;
				int len;
				long gid;
				long last = -1;
				long delta;
				int sizeHint = -1;
				DocumentImpl doc;
				NodeProxy parent;
				VariableByteInputStream is = new VariableByteInputStream(dis);
				try {
					while (is.available() > 0) {
						docId = is.readInt();
						len = is.readInt();
						if ((doc = docs.getDoc(docId)) == null) {
							is.skip(len);
							continue;
						}
						if(context != null)
							sizeHint = context.getSizeHint(doc);
						last = -1;
						for (int j = 0; j < len; j++) {
							delta = is.readLong();
							gid = (last < 0 ? delta : last + delta);
							last = gid;
							if (context != null) {
								parent =
									context.parentWithChild(doc, gid, false, true, -1);
								if (parent != null) {
									result.add(parent, sizeHint);
									if (trackMatches != Serializer.TAG_NONE)
										parent.addMatch(new Match(word, gid));
								}
							} else
								((TextSearchResult) result).add(doc, gid, word);
						}
					}
				} catch (EOFException e) {
					// EOFExceptions are normal
				} catch (IOException e) {
					LOG.warn("io error while reading index", e);
				}
				try {
					dis.close();
				} catch (IOException e1) {
				}
			}
			if(context != null)
				((ExtArrayNodeSet)result).sort();
			return true;
		}
	}
}
