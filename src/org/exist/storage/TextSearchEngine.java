
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
import org.exist.dom.*;
import org.exist.storage.analysis.Tokenizer;
import org.exist.storage.analysis.SimpleTokenizer;

public abstract class TextSearchEngine extends Observable {

	private static Category LOG = Category.getInstance(TextSearchEngine.class.getName());
	protected TreeSet stoplist = new TreeSet();
	protected DBBroker broker = null;
	protected Tokenizer tokenizer;
	protected Configuration config;
	protected boolean indexNumbers = false, stem = false;
	protected PorterStemmer stemmer = null;

	public TextSearchEngine(DBBroker broker, Configuration conf) {
		this.broker = broker;
		this.config = conf;
		String stopword, tokenizerClass;
		Boolean num, stemming;
		if((num = (Boolean)config.getProperty("indexer.indexNumbers")) != null)
			indexNumbers = num.booleanValue();
		if((stemming = (Boolean)config.getProperty("indexer.stem")) != null)
			stem = stemming.booleanValue();
		if((tokenizerClass = (String)config.getProperty("indexer.tokenizer")) != null) {
			try {
				Class tokClass = Class.forName( tokenizerClass );
				tokenizer = (Tokenizer)tokClass.newInstance();
				LOG.debug("using tokenizer: " + tokenizerClass);
			} catch (ClassNotFoundException e) {
				LOG.debug(e);
			} catch (InstantiationException e) {
				LOG.debug(e);
			} catch (IllegalAccessException e) {
				LOG.debug(e);
			}
		}
		if(tokenizer == null) {
			LOG.debug("using simple tokenizer");
			tokenizer = new SimpleTokenizer();
		}
			
		if(stem) stemmer = new PorterStemmer();
		tokenizer.setStemming(stem);
		if((stopword = 
		    (String)config.getProperty("stopwords")) == null)
		    stopword = null;
		if(stopword != null) {
		  try {
			FileReader in = new FileReader(stopword);
			StreamTokenizer tok = new StreamTokenizer(in);
			int next = tok.nextToken();
			while(next != StreamTokenizer.TT_EOF) {
			    if(next !=  StreamTokenizer.TT_WORD)
				continue;
			    stoplist.add(tok.sval);
			    next = tok.nextToken();
			}
		  } catch(FileNotFoundException e) {
			LOG.debug(e);
		  } catch(IOException e) {
			LOG.debug(e);
		  }
        }
	}
	
	public Tokenizer getTokenizer() {
		return tokenizer;
	}

	public abstract void storeText(IndexPaths idx, TextImpl text);
	public void storeAttribute(IndexPaths idx, TextImpl text) {
		throw new RuntimeException("not implemented");
	}

	public abstract void flush();
	public abstract void close();

	public abstract NodeSet[] getNodesContaining(DocumentSet doc, String expr[]);
}
