
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

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.Observable;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.fulltext.ElementContent;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.analysis.SimpleTokenizer;
import org.exist.storage.analysis.Tokenizer;
import org.exist.storage.btree.DBException;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.util.Occurrences;
import org.exist.util.PorterStemmer;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XQueryContext;

/**
 * This is the base class for all classes providing access to the fulltext index.
 * 
 * The class has methods to add text and attribute nodes to the fulltext index,
 * or to search for nodes matching selected search terms.
 *  
 * @author wolf
 */
public abstract class TextSearchEngine extends Observable {

	protected final static Logger LOG =
		Logger.getLogger(TextSearchEngine.class);
		
    protected TreeSet stoplist = new TreeSet();
	protected DBBroker broker = null;
	protected Tokenizer tokenizer;
	protected Configuration config;
	protected boolean indexNumbers = false ;
	protected boolean stem = false ;
	protected boolean termFreq = true;
	protected PorterStemmer stemmer = null;
	protected int trackMatches = Serializer.TAG_ELEMENT_MATCHES;
	
	public final static String INDEX_NUMBERS_ATTRIBUTE = "parseNumbers";
	public final static String STEM_ATTRIBUTE = "stemming";
	public final static String STORE_TERM_FREQUENCY_ATTRIBUTE = "track-term-freq";
	public final static String TOKENIZER_ATTRIBUTE = "tokenizer";
	public static final String CONFIGURATION_STOPWORDS_ELEMENT_NAME = "stopwords";
	public final static String STOPWORD_FILE_ATTRIBUTE = "file";
	
	public final static String PROPERTY_INDEX_NUMBERS = "indexer.indexNumbers";
	public final static String PROPERTY_STEM = "indexer.stem";
	public final static String PROPERTY_STORE_TERM_FREQUENCY = "indexer.store-term-freq";
	public final static String PROPERTY_TOKENIZER = "indexer.tokenizer";
	public final static String PROPERTY_STOPWORD_FILE = "stopwords";
	
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
		Boolean num, stemming, termFrequencies;
		if ((num = (Boolean) config.getProperty(PROPERTY_INDEX_NUMBERS)) != null)
			indexNumbers = num.booleanValue();
		if ((stemming = (Boolean) config.getProperty(PROPERTY_STEM)) != null)
			stem = stemming.booleanValue();
		if((termFrequencies = (Boolean) config.getProperty(PROPERTY_STORE_TERM_FREQUENCY)) != null)
			termFreq = termFrequencies.booleanValue();
		String track = (String) config.getProperty(Serializer.PROPERTY_TAG_MATCHING_ELEMENTS);
		if (track != null)
			trackMatches = track.equalsIgnoreCase("yes")
			? Serializer.TAG_ELEMENT_MATCHES
					: Serializer.TAG_NONE;
		track = (String) config.getProperty(Serializer.PROPERTY_TAG_MATCHING_ATTRIBUTES);
		if (track != null && track.equalsIgnoreCase("yes"))
			trackMatches = trackMatches | Serializer.TAG_ATTRIBUTE_MATCHES;
		
		if ((tokenizerClass = (String) config.getProperty(PROPERTY_TOKENIZER)) != null) {
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
		if ((stopword = (String) config.getProperty(PROPERTY_STOPWORD_FILE)) != null) {
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
	 * @return tokenizer
	 */
	public Tokenizer getTokenizer() {
		return tokenizer;
	}
    
    /**
	 * Tokenize and index the given text node.
	 * 
	 * @param indexSpec
	 * @param node
	 */
	public abstract void storeText(CharacterDataImpl node, int indexingHint, FulltextIndexSpec indexSpec, boolean remove);
    public abstract void storeText(StoredNode parent, ElementContent text, int indexingHint, FulltextIndexSpec indexSpec, boolean remove);

	public abstract void flush();
	public abstract boolean close() throws DBException;

	public int getTrackMatches() {
		return trackMatches;
	}
	
	public void setTrackMatches(int flags) {
		trackMatches = flags;
	}

    public NodeSet getNodesContaining(XQueryContext context, DocumentSet docs,
	        NodeSet contextSet, int axis, QName qname, String expr, int type) throws TerminatedException {
        return getNodesContaining(context, docs, contextSet, axis, qname, expr, type, true);
    }

    /**
	 * For each of the given search terms and each of the documents in the
	 * document set, return a node-set of matching nodes.
	 *
	 * The type-argument indicates if search terms should be compared using
	 * a regular expression. Valid values are DBBroker.MATCH_EXACT or
	 * DBBroker.MATCH_REGEXP.
	 */
	public abstract NodeSet getNodesContaining(XQueryContext context, DocumentSet docs,
	        NodeSet contextSet, int axis, QName qname, String expr, int type, boolean matchAll) throws TerminatedException;
	
	public abstract NodeSet getNodes(XQueryContext context, DocumentSet docs, NodeSet contextSet, int axis, QName qname,
	        TermMatcher matcher, CharSequence startTerm) throws TerminatedException;
	
	/**
	 * Queries the fulltext index to retrieve information on indexed words contained
	 * in the index for the current collection. Returns a list of {@link Occurrences} for all 
	 * words contained in the index. If param end is null, all words starting with 
	 * the string sequence param start are returned. Otherwise, the method 
	 * returns all words that come after start and before end in lexical order.
	 */
	public abstract Occurrences[] scanIndexTerms(
		DocumentSet docs,
		NodeSet contextSet,
		String start,
		String end) throws PermissionDeniedException;

    public abstract Occurrences[] scanIndexTerms(
		DocumentSet docs,
		NodeSet contextSet,
        QName[] qnames,
        String start,
		String end) throws PermissionDeniedException;
    
    public abstract String[] getIndexTerms(DocumentSet docs, TermMatcher matcher);
	
	/**
	 * Remove index entries for an entire collection.
	 * 
	 * @param collection
	 */
	public abstract void dropIndex(Collection collection);
	
	/**
	 * Remove all index entries for the given document.
	 * 
	 * @param doc
	 */
	public abstract void dropIndex(DocumentImpl doc);
	
}
