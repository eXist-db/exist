
/* eXist Open Source Native XML Database
 * Copyright (C) 2001/02,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.storage;

import org.apache.log4j.Category;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.FileReader;
import java.util.TreeSet;
import java.util.Observable;
import org.exist.util.*;
import org.exist.collections.*;
import org.exist.dom.*;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.analysis.Tokenizer;
import org.exist.storage.analysis.SimpleTokenizer;

/**
 * This is the base class for all classes providing access to the fulltext index.
 * 
 * The class has methods to add text and attribute nodes to the fulltext index,
 * or to search for nodes matching selected search terms.
 *  
 * @author wolf
 */
public abstract class TextSearchEngine extends Observable {

	protected final static Category LOG =
		Category.getInstance(TextSearchEngine.class.getName());
		
	protected TreeSet stoplist = new TreeSet();
	protected DBBroker broker = null;
	protected Tokenizer tokenizer;
	protected Configuration config;
	protected boolean indexNumbers = false, stem = false;
	protected PorterStemmer stemmer = null;

	/**
	 * Construct a new instance and configure it.
	 * 
	 * @param broker
	 * @param conf
	 */
	public TextSearchEngine(DBBroker broker, Configuration conf) {
		this.broker = broker;
		this.config = conf;
		String stopword, tokenizerClass;
		Boolean num, stemming;
		if ((num = (Boolean) config.getProperty("indexer.indexNumbers"))
			!= null)
			indexNumbers = num.booleanValue();
		if ((stemming = (Boolean) config.getProperty("indexer.stem")) != null)
			stem = stemming.booleanValue();
		if ((tokenizerClass = (String) config.getProperty("indexer.tokenizer"))
			!= null) {
			try {
				Class tokClass = Class.forName(tokenizerClass);
				tokenizer = (Tokenizer) tokClass.newInstance();
				LOG.debug("using tokenizer: " + tokenizerClass);
			} catch (ClassNotFoundException e) {
				LOG.debug(e);
			} catch (InstantiationException e) {
				LOG.debug(e);
			} catch (IllegalAccessException e) {
				LOG.debug(e);
			}
		}
		if (tokenizer == null) {
			LOG.debug("using simple tokenizer");
			tokenizer = new SimpleTokenizer();
		}

		if (stem)
			stemmer = new PorterStemmer();
		tokenizer.setStemming(stem);
		if ((stopword = (String) config.getProperty("stopwords")) != null) {
			try {
				FileReader in = new FileReader(stopword);
				StreamTokenizer tok = new StreamTokenizer(in);
				int next = tok.nextToken();
				while (next != StreamTokenizer.TT_EOF) {
					if (next != StreamTokenizer.TT_WORD)
						continue;
					stoplist.add(tok.sval);
					next = tok.nextToken();
				}
			} catch (FileNotFoundException e) {
				LOG.debug(e);
			} catch (IOException e) {
				LOG.debug(e);
			}
		}
	}

	/**
	 * Returns the Tokenizer used for tokenizing strings into
	 * words.
	 * 
	 * @return
	 */
	public Tokenizer getTokenizer() {
		return tokenizer;
	}

	/**
	 * Tokenize and index the given text node.
	 * 
	 * @param idx
	 * @param text
	 */
	public abstract void storeText(IndexPaths idx, TextImpl text);
	
	/**
	 * Tokenize and index the given attribute node.
	 * 
	 * @param idx
	 * @param text
	 */
	public abstract void storeAttribute(IndexPaths idx, AttrImpl text);

	public abstract void flush();
	public abstract void close();

	/**
	 * For each of the given search terms and each of the documents in the
	 * document set, return a node-set of matching nodes. 
	 * 
	 * This method uses MATCH_EXACT for comparing search terms.
	 * 
	 * @param doc
	 * @param expr
	 * @return
	 */
	public NodeSet getNodesContaining(DocumentSet doc, NodeSet context, String expr) {
		return getNodesContaining(doc, context, expr, DBBroker.MATCH_EXACT);
	}

	/**
	 * For each of the given search terms and each of the documents in the
	 * document set, return a node-set of matching nodes. 
	 * 
	 * The type-argument indicates if search terms should be compared using
	 * a regular expression. Valid values are DBBroker.MATCH_EXACT or
	 * DBBroker.MATCH_REGEXP.
	 * 
	 * @param doc
	 * @param expr
	 * @return
	 */
	public abstract NodeSet getNodesContaining(DocumentSet docs, NodeSet context, String expr, int type);
	
	public abstract NodeSet getNodes(DocumentSet docs, NodeSet context, TermMatcher matcher,
		CharSequence startTerm);
	
	/**
	 * Scan the fulltext index and return an Occurrences object for each
	 * of the index keys.
	 * 
	 * Arguments start and end are used to restrict the range of keys returned.
	 * For example start="a" and end="az" will return all keywords starting
	 * with letter "a".
	 * 
	 * @param user
	 * @param collection
	 * @param start
	 * @param end
	 * @param inclusive
	 * @return
	 * @throws PermissionDeniedException
	 */
	public abstract Occurrences[] scanIndexTerms(
		User user,
		Collection collection,
		String start,
		String end,
		boolean inclusive) throws PermissionDeniedException;

	public abstract String[] getIndexTerms(DocumentSet docs, TermMatcher matcher);
	
	/**
	 * Remove index entries for an entire collection.
	 * 
	 * @param collection
	 */
	public abstract void removeCollection(Collection collection);
	
	/**
	 * Remove all index entries for the given document.
	 * 
	 * @param doc
	 */
	public abstract void removeDocument(DocumentImpl doc);
	
	/**
	 * Reindex a document or node.
	 * 
	 * If node is null, all levels of the document tree starting with
	 * DocumentImpl.reindexRequired() will be reindexed.
	 *  
	 * @param oldDoc
	 * @param node
	 */
	public abstract void reindex(DocumentImpl oldDoc, NodeImpl node);
}
