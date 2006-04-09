/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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

//import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.NodeSetHelper;
import org.exist.dom.StoredNode;
import org.exist.dom.TextImpl;
import org.exist.dom.VirtualNodeSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.analysis.TextToken;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.index.BFile;
import org.exist.storage.io.VariableByteArrayInput;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.Lock;
import org.exist.util.ByteArray;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.Occurrences;
import org.exist.util.ProgressIndicator;
import org.exist.util.ReadOnlyException;
import org.exist.util.UTF8;
import org.exist.util.XMLString;
import org.exist.xquery.Constants;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class is responsible for fulltext-indexing. Text-nodes are handed over
 * to this class to be fulltext-indexed. Method storeText() is called by
 * RelationalBroker whenever it finds a TextNode. Method getNodeIDsContaining()
 * is used by the XPath-engine to process queries where a fulltext-operator is
 * involved. The class keeps two database tables: table <code>dbTokens</code> stores the words
 * found with their unique id. Table <code>invertedIndex</code> contains the word occurrences for
 * every word-id per document.
 * 
 * TODO: store node type (attribute or text) with each entry
 * 
 * @author Wolfgang Meier
 */
public class NativeTextEngine extends TextSearchEngine implements ContentLoadingObserver {

    public final static byte TEXT_SECTION = 0;
	public final static byte ATTRIBUTE_SECTION = 1;
  
    /** Length limit for the tokens */
	public final static int MAX_TOKEN_LENGTH = 2048;
	
	/** The datastore for this token index */
	protected BFile dbTokens;
	protected InvertedIndex invertedIndex;
    
    /** Work output Stream that should be cleared before every use */
    private VariableByteOutputStream os = new VariableByteOutputStream();    

	public NativeTextEngine(DBBroker broker, Configuration config, BFile db) {
		super(broker, config);
        this.dbTokens = db;
        this.invertedIndex = new InvertedIndex();
	}

	/**
	 * Checks if the given string could be a regular expression.
	 * 
	 * @param str The string
	 */
	public final static boolean containsWildcards(String str) {
        if (str == null || str.length() == 0)
            return false;
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

	public int getTrackMatches() {
		return trackMatches;
	}
	
	public void setTrackMatches(int flags) {
		trackMatches = flags;
	}
    
    public void setDocument(DocumentImpl document) {
        //TODO Auto-generated method stub        
    }    
    
    /**
     * Indexes the tokens contained in an attribute.
     * 
     * @param attr The attribute to be indexed
     */
    //TODO : unify functionalities with storeText -pb
    public void storeAttribute(FulltextIndexSpec indexSpec, AttrImpl attr) {
        final DocumentImpl doc = attr.getDocument();
        final long gid = attr.getGID();        
        //TODO : case conversion should be handled by the tokenizer -pb
        tokenizer.setText(attr.getValue().toLowerCase());   
        TextToken token;
        while (null != (token = tokenizer.nextToken())) {
            if (token.length() > MAX_TOKEN_LENGTH) {
                continue;
            } 
            if (stoplist.contains(token)) {
                continue;
            }            
            if (indexSpec != null) {  
                //TODO : the tokenizer should strip unwanted token types itself -pb
                if (!indexSpec.getIncludeAlphaNum() && !token.isAlpha()) {
                    continue;
                }
            }
            invertedIndex.setDocument(doc);
            invertedIndex.addAttribute(token, gid);
        }
    }

    /**
     * Indexes the tokens contained in a text node.
     * 
     * @param indexSpec The index configuration
     * @param text The text node to be indexed
     * @param noTokenizing
     *                if <code>true</code>, given text is indexed as a single token
     *                if <code>false</code>, it is tokenized before being indexed
     */
    //TODO : use an indexSpec member in order to get rid of <code>noTokenizing</code>
    public void storeText(FulltextIndexSpec indexSpec, TextImpl text, boolean noTokenizing) {
        final DocumentImpl doc = text.getDocument();
        final long gid = text.getGID();
        //TODO : case conversion should be handled by the tokenizer -pb
        final XMLString t = text.getXMLString().transformToLower();        
        TextToken token;        
        if (noTokenizing) {            
            token = new TextToken(TextToken.ALPHA, t, 0, t.length());
            invertedIndex.setDocument(doc);
            invertedIndex.addText(t, token, gid);           
        } else {
            tokenizer.setText(t);
            while (null != (token = tokenizer.nextToken())) {
                if (token.length() > MAX_TOKEN_LENGTH) {
                    continue;
                } 
                if (stoplist.contains(token)) {
                    continue;
                }
                if (indexSpec != null) {
                    //TODO : the tokenizer should strip unwanted token types itself -pb
                    if (!indexSpec.getIncludeAlphaNum() && !token.isAlpha()) {
                        continue;
                    }
                }                
                invertedIndex.setDocument(doc);
                invertedIndex.addText(t, token, gid);
            }
        }
    } 
    
    public void storeAttribute(RangeIndexSpec spec, AttrImpl node) {
        // TODO Auto-generated method stub  
    }

    public void storeAttribute(AttrImpl node, NodePath currentPath, boolean fullTextIndexSwitch) {
        //TODO Auto-generated method stub        
    }

    public void storeText(TextImpl node, NodePath currentPath, boolean fullTextIndexSwitch) {
        // TODO Auto-generated method stub      
    }

    public void startElement(ElementImpl impl, NodePath currentPath, boolean index) {
        // TODO Auto-generated method stub      
    }

    public void endElement(int xpathType, ElementImpl node, String content) {
        // TODO Auto-generated method stub  
    }

    public void removeElement(ElementImpl node, NodePath currentPath, String content) {
        // TODO Auto-generated method stub      
    }    

    /* (non-Javadoc)
     * @see org.exist.storage.ContentLoadingObserver#sync()
     */    
    public void sync() {
        final Lock lock = dbTokens.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            dbTokens.flush();
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "'", e); 
            //TODO : throw an exception ? -pb            
        } catch (DBException e) {
            LOG.error(e.getMessage(), e); 
            //TODO : throw an exception ? -pb
        } finally {
            lock.release();
        }
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.ContentLoadingObserver#flush()
     */    
	public void flush() {
		invertedIndex.flush();
	}

	public void reindex(DocumentImpl document, StoredNode node) {
		invertedIndex.reindex(document, node);
	}

	public void remove() {
		invertedIndex.remove();
	}
    
    /* Drop all index entries for the given collection.
     * @see org.exist.storage.ContentLoadingObserver#dropIndex(org.exist.collections.Collection)
     */
    public void dropIndex(Collection collection) {
        final WordRef value = new WordRef(collection.getId());
        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, value);            
        final Lock lock = dbTokens.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            dbTokens.removeAll(query);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "'", e);
        } catch (BTreeException e) {
            LOG.error(e.getMessage(), e);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }
    
    /* Drop all index entries for the given document.
     * @see org.exist.storage.ContentLoadingObserver#dropIndex(org.exist.dom.DocumentImpl)
     */
    public void dropIndex(DocumentImpl document) {        
        //Collect document's tokens
        final TreeSet tokens = new TreeSet();
        final NodeList children = document.getChildNodes();        
        for (int i = 0; i < children.getLength(); i++) {
            StoredNode node = (StoredNode) children.item(i);
            Iterator j = broker.getDOMIterator(new NodeProxy(document, node.getGID(), 
            		node.getNodeType(), node.getInternalAddress()));
            collect(tokens, j);
        }
        final short collectionId = document.getCollection().getId();        
        final Lock lock = dbTokens.getLock();
        for (Iterator iter = tokens.iterator(); iter.hasNext();) {
            String token = (String) iter.next();
            WordRef key = new WordRef(collectionId, token);
            try {
                lock.acquire(Lock.WRITE_LOCK);
                boolean changed = false;                
                os.clear();    
                VariableByteInput is = dbTokens.getAsStream(key);
                //Does the token already has data in the index ?
                if (is == null)
                    continue;            
                //try {
                    while (is.available() > 0) {
                        int storedDocId = is.readInt();
                        byte section = is.readByte();
                        int gidsCount = is.readInt();
                        //TOUNDERSTAND -pb        
                        int size = is.readFixedInt();
                        if (storedDocId != document.getDocId()) {
                            // data are related to another document:
                            // copy them to any existing data
                            os.writeInt(storedDocId);
                            os.writeByte(section);
                            os.writeInt(gidsCount);
                            os.writeFixedInt(size);
                            is.copyRaw(os, size);
                        } else {
                            // data are related to our document:
                            // skip them      
                            changed = true;                            
                            is.skipBytes(size);
                        }
                    }
                //} catch (EOFException e) {
                    //EOF is expected here 
                //}
                //Store new data, if relevant
                if (changed) {
                    //Well, nothing to store : remove the existing data
                    if (os.data().size() == 0) {
                        dbTokens.remove(key);
                    } else {                        
                        if (dbTokens.put(key, os.data()) == BFile.UNKNOWN_ADDRESS) {
                            LOG.error("Could not put index data for token '" +  token + "' in '" + dbTokens.getFile().getName() + "'");
                        }                    
                    }
                }
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "'", e);                
            } catch (IOException e) {
                LOG.error(e.getMessage() + " in '" + dbTokens.getFile().getName() + "'", e);                
            } catch (ReadOnlyException e) {
                LOG.error(e.getMessage() + " in '" + dbTokens.getFile().getName() + "'", e);                       
            } finally {
                lock.release();
            }
        }        
    }    

	public NodeSet getNodesContaining(XQueryContext context, DocumentSet docs, NodeSet contextSet,
	        String expr, int type) throws TerminatedException {
		if (type == DBBroker.MATCH_EXACT && containsWildcards(expr)) {
            //TODO : log this fallback ? -pb
			type = DBBroker.MATCH_WILDCARDS;
		}
		switch (type) {
			case DBBroker.MATCH_EXACT :
				return getNodesExact(context, docs, contextSet, expr);
                //TODO : stricter control -pb
			default :
				return getNodesRegexp(context, docs, contextSet, expr, type);
		}
	}


	/** Get all nodes whose content exactly matches the give expression.
	 * @param context
	 * @param docs
	 * @param contextSet
	 * @param expr
	 * @return
	 * @throws TerminatedException
	 */
	public NodeSet getNodesExact(XQueryContext context, DocumentSet docs, NodeSet contextSet, String expr) 
	    throws TerminatedException {
        //Return early
		if (expr == null)
			return null;
        //TODO : filter the expression *before* -pb
		if (stoplist.contains(expr))
			return null;
        
        //TODO : case conversion should be handled by the tokenizer -pb
        expr = expr.toLowerCase();
        //TODO : use an indexSpec member in order to get rid of this or do the job *before* -pb
        String token;        
        if (stem)
            token = stemmer.stem(expr);
        else
            token = expr;
        final NodeSet result = new ExtArrayNodeSet(docs.getLength(), 250);         
		for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
            final short collectionId = ((Collection) iter.next()).getId();
            final Value key = new WordRef(collectionId, token);
			final Lock lock = dbTokens.getLock();
			try {
				lock.acquire();
                VariableByteInput is = dbTokens.getAsStream(key);
                //Does the token already has data in the index ?
				if (is == null)
					continue;				
				while (is.available() > 0) {
                    int storedDocId = is.readInt();
                    int storedSection = is.readByte();
                    int gidsCount = is.readInt();
                    //TOUNDERSTAND -pb       
                    int size = is.readFixedInt();
                    DocumentImpl storedDocument = docs.getDoc(storedDocId);
                    //Exit if the document is not concerned
                    if (storedDocument == null) {
                        is.skipBytes(size);
                        continue;                        
                    }                    
					if (contextSet != null) {
                        //Exit if the document is not concerned
                        if (!contextSet.containsDoc(storedDocument)) {                    
                            is.skipBytes(size);
                            continue;
                        }                        
					}
                    //Process the nodes
                    long previousGID = 0;
					for (int j = 0; j < gidsCount; j++) {
                        long delta = is.readLong();
                        long storedGID = previousGID + delta;                        
                        int freq = is.readInt();
                        NodeProxy storedNode;
                        switch (storedSection) {
                            case ATTRIBUTE_SECTION :
                                storedNode = new NodeProxy(storedDocument, storedGID, Node.ATTRIBUTE_NODE);
                                break;
                            case TEXT_SECTION :
                                storedNode = new NodeProxy(storedDocument, storedGID, Node.TEXT_NODE);
                                break;
                            default :
                                throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + "'");
                        }						
						// if a context set is specified, we can directly check if the
						// matching text node is a descendant of one of the nodes
						// in the context set.
						if (contextSet != null) {
                            NodeProxy parent;
                            switch(storedSection) {
                                case ATTRIBUTE_SECTION :
                                    if (contextSet instanceof VirtualNodeSet) {
                                        parent = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                        if (parent != null && parent.getGID() != storedGID)
                                            parent = null;
                                    } else
                                        parent = contextSet.get(storedNode);
                                    break;
                                case TEXT_SECTION :
                                    parent = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                    break;
                                default :
                                    throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + "'");
                            }                               
							if (parent != null) {
                                Match match = new Match(storedGID, token, freq);
                                readOccurrences(freq, is, match, token.length());
                                parent.addMatch(match);
                                int sizeHint = contextSet.getSizeHint(storedDocument);
								result.add(parent, sizeHint);
							} else {
							    is.skip(freq);
                            }
						// otherwise, we add all text nodes without check
						} else {
                            Match match = new Match(storedGID, token, freq);
                            readOccurrences(freq, is, match, token.length());
                            storedNode.addMatch(match);
							result.add(storedNode, Constants.NO_SIZE_HINT);							
						}
						context.proceed();
                        previousGID = storedGID;                        
					}
				}
            //} catch (EOFException e) {
            //    // EOF is expected here 
            //    LOG.warn("REPORT ME for confirmation " + e.getMessage(), e); 
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "'", e);              
			} catch (IOException e) {
                LOG.error(e.getMessage() + " in '" + dbTokens.getFile().getName() + "'", e);                
                //TODO : return ?
			} finally {
				lock.release();
			}
		}
		return result;
	}
    
    private NodeSet getNodesRegexp(XQueryContext context, DocumentSet docs, NodeSet contextSet,
            String expr, int type) throws TerminatedException {
        //Return early
        if (expr == null)
            return null;
        if (stoplist.contains(expr))
            return null;
        //TODO : case conversion should be handled by the tokenizer -pb
        expr = expr.toLowerCase();

        // if the regexp starts with a char sequence, we restrict the index scan to entries starting with
        // the same sequence. Otherwise, we have to scan the whole index.
        final StringBuffer token = new StringBuffer();
        for (int i = 0; i < expr.length(); i++) {
            if (Character.isLetterOrDigit(expr.charAt(i)))
                token.append(expr.charAt(i));
            else
                break;
        }
        try {
            TermMatcher comparator = new RegexMatcher(expr, type, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return getNodes(context, docs, contextSet, comparator, token);
        } catch (EXistException e) {
            return null;
        }
    }
    
	/* Return all nodes for wich the matcher matches.
	 * @see org.exist.storage.TextSearchEngine#getNodes(org.exist.xquery.XQueryContext, org.exist.dom.DocumentSet, org.exist.dom.NodeSet, org.exist.storage.TermMatcher, java.lang.CharSequence)
	 */
	public NodeSet getNodes(XQueryContext context, DocumentSet docs, NodeSet contextSet,
			TermMatcher matcher, CharSequence startTerm) throws TerminatedException {
		final NodeSet result = new ExtArrayNodeSet();
        final SearchCallback cb = new SearchCallback(context, matcher, result, contextSet, docs); 
		final Lock lock = dbTokens.getLock();		
		for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
            final short collectionId = ((Collection) iter.next()).getId();        
            //Compute a key for the token                
            Value value;
			if (startTerm != null && startTerm.length() > 0)
                //TODO : case conversion should be handled by the tokenizer -pb
				value = new WordRef(collectionId, startTerm.toString().toLowerCase());
			else
				value = new WordRef(collectionId);
			IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, value);
			try {
				lock.acquire();	
				dbTokens.query(query, cb);
			} catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "'", e);
            } catch (BTreeException e) {
                LOG.error(e.getMessage(), e);
                //TODO return null ? rethrow ? -pb                
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);   
                //TODO return null ? rethrow ? -pb
			} finally {
				lock.release();
			}
		}
		return result;
	}

	public String[] getIndexTerms(DocumentSet docs, TermMatcher matcher) {
        final IndexCallback cb = new IndexCallback(null, matcher);		
		final Lock lock = dbTokens.getLock();		
		for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
            final short collectionId = ((Collection) iter.next()).getId();  
            //Compute a key for the token                                 
            Value value = new WordRef(collectionId);
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, value);
			try {
				lock.acquire();
				dbTokens.query(query, cb);
			} catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "'", e);
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            } catch (BTreeException e) {
                LOG.error(e.getMessage(), e);
            } catch (TerminatedException e) {
                LOG.warn(e.getMessage(), e);                       
			} finally {
				lock.release();
			}
		}
		return cb.getMatches();
	}    

	public Occurrences[] scanIndexTerms(DocumentSet docs, NodeSet contextSet, String start, String end) 
            throws PermissionDeniedException {
        final IndexScanCallback cb = new IndexScanCallback(docs, contextSet);
        final Lock lock = dbTokens.getLock();
		for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {            
            final short collectionId = ((Collection) i.next()).getId();          
            final IndexQuery query;
            if (end == null) {
                Value startRef = new WordRef(collectionId, start.toLowerCase());
                query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startRef);
            } else {
                Value startRef = new WordRef(collectionId,  start.toLowerCase());
                Value endRef = new WordRef(collectionId, end.toLowerCase());
    			query = new IndexQuery(IndexQuery.BW, startRef, endRef);
            }
			try {
				lock.acquire();
				dbTokens.query(query, cb);
			} catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "'", e);
			} catch (IOException e) {
                LOG.error(e.getMessage(), e);
			} catch (BTreeException e) {
                LOG.error(e.getMessage(), e);
			} catch (TerminatedException e) {
                LOG.warn(e.getMessage(), e);
            } finally {
				lock.release();
			}
		}		
		Occurrences[] result = new Occurrences[cb.map.size()];		
		return (Occurrences[]) cb.map.values().toArray(result);
	}
    
    /**
     * @param freq
     * @param is
     * @param match
     * @throws IOException
     */
    private void readOccurrences(int freq, VariableByteInput is, Match match, int length) 
            throws IOException {
        for (int k = 0; k < freq; k++) {
            match.addOffset(is.readInt(), length);
        }
    }        
	
    /**
     * Collect all words in a document to be removed
     * 
     * @param words
     *                Description of the Parameter
     * @param domIterator
     *                Description of the Parameter
     */
    //TODO : unify functionalities with storeText -pb
    private void collect(Set words, Iterator domIterator) {
        byte[] data = ((Value) domIterator.next()).getData();
        short type = Signatures.getType(data[0]);
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
                    tokenizer.setText(s);
                    TextToken token;
                    while (null != (token = tokenizer.nextToken())) {
                        String word = token.getText();
                        if (stoplist.contains(word))
                            continue;
                        words.add(word.toLowerCase());
                    }                    
                } catch (UnsupportedEncodingException e) {
                    //s = new String(data, 1, data.length - 1);
                    LOG.error(e.getMessage(), e);
                    s = null;
                }                
                break;
            case Node.ATTRIBUTE_NODE :
                byte idSizeType = (byte) (data[0] & 0x3);
                String val;
                try {
                    val = new String(data,
                        1 + Signatures.getLength(idSizeType), 
                        data.length - 1 - Signatures.getLength(idSizeType),
                        "UTF-8");
                    tokenizer.setText(val);
                    TextToken token;
                    while (null != (token = tokenizer.nextToken())) {
                        String word = token.getText();
                        if (stoplist.contains(word))
                            continue;
                        words.add(word.toLowerCase());
                    }                    
                } catch (UnsupportedEncodingException e) {
                    //val = new String(data,
                    //        1 + Signatures.getLength(idSizeType), data.length
                    //                - 1 - Signatures.getLength(idSizeType));
                    LOG.error(e.getMessage(), e);
                    val = null;
                }                
                break;
           default :
               //Other types are ignored : some may be useful though -pb
               //TOUNDERSTAND : it looks like other types (got : Node.PROCESSING_INSTRUCTION_NODE)
               //are stored in the index ??? -pb
        }
    }
    
    public boolean close() throws DBException {
        return dbTokens.close();        
    }  
    
    public void printStatistics() {
        dbTokens.printStatistics();
    }
    
    public String toString() {
        return this.getClass().getName() + " at "+ dbTokens.getFile().getName() +
        " owned by " + broker.toString();
    }    

	/**
	 * This inner class is responsible for actually storing the list of
	 * occurrences.
	 * 
	 * @author Wolfgang Meier <meier@ifs.tu-darmstadt.de>
	 */
	final class InvertedIndex {

		private DocumentImpl doc = null;
        // To distinguish between attribute values and text, we use
        // two maps: words[0] collects text, words[1] stores attribute
        // values.        
        //TODO : very tricky. Why not 2 inverted indexes ??? -pb
		private Map words[] = new HashMap[2];
		private VariableByteOutputStream os = new VariableByteOutputStream(7);

		public InvertedIndex() {
			words[0] = new HashMap(512);
			words[1] = new HashMap(256);
		}
        
        public void setDocument(DocumentImpl document) {
            if (this.doc != null && this.doc.getDocId() != document.getDocId())
                flush();
            this.doc = document;
        }        

		public void addText(XMLString text, TextToken token, long gid) {
            //Is this token already pending ?
            OccurrenceList list = (OccurrenceList) words[0].get(token);
            //Create a GIDs list
            if (list == null) {
                list = new OccurrenceList();
                list.add(gid, token.startOffset());
                words[0].put(token.getText(), list);
            } else {
                //Add node's GID to the list
                list.add(gid, token.startOffset());
            }
		}

        //TODO : unify functionalities with addText -pb
		public void addAttribute(TextToken token, long gid) {
            //Is this token already pending ?
            OccurrenceList list = (OccurrenceList) words[1].get(token);
            //Create a GIDs list
            if (list == null) {
                list = new OccurrenceList();
                list.add(gid, token.startOffset());
                words[1].put(token.getText(), list);
            } else {
                //Add node's GID to the list
                list.add(gid, token.startOffset());
            }
		}
        
        public void flush() {
            //return early
            if (this.doc == null)
                return;            
            final int wordsCount = words[0].size() + words[1].size();
            if (wordsCount == 0)
                return;
            final ProgressIndicator progress = new ProgressIndicator(wordsCount, 100);
            final short collectionId = this.doc.getCollection().getId();
            int count = 0;
            for (byte currentSection = 0; currentSection <= ATTRIBUTE_SECTION; currentSection++) {
            	//Not very necessary, but anyway...
                switch (currentSection) {
	                case TEXT_SECTION :
	                case ATTRIBUTE_SECTION :
	                	break;
	                default :
	                    throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + 
	                    "' (inverted index)");
	            }                    
                for (Iterator i = words[currentSection].entrySet().iterator(); i.hasNext(); count++) {
                    Map.Entry entry = (Map.Entry) i.next();
                    String token = (String) entry.getKey();
                    OccurrenceList occurences = (OccurrenceList) entry.getValue();
                    //Don't forget this one
                    occurences.sort();
                    os.clear();
                    os.writeInt(this.doc.getDocId());
                    os.writeByte(currentSection);
                    os.writeInt(occurences.getTermCount());
                    //TOUNDERSTAND -pb             
                    int lenOffset = os.position();
                    os.writeFixedInt(0);
                    saveOccurrences(occurences);
                    os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);                    
                    flushWord(collectionId, token, os.data());
                    progress.setValue(count);
                    if (progress.changed()) {
                        setChanged();
                        notifyObservers(progress);
                    }
                }
                //TOUNDERSTAND : is this a flush ? 
                //If so, the ProgressIndicator should be reinitialized -pb
                if (wordsCount > 100) {
                    progress.finish();
                    setChanged();
                    notifyObservers(progress);
                }
                words[currentSection].clear();
            }
        }

        private void flushWord(short collectionId, String token, ByteArray data) {
            //return early
            //TODO : is this ever called ? -pb
            if (data.size() == 0)
                return;
            final Lock lock = dbTokens.getLock();
            try {
                lock.acquire(Lock.WRITE_LOCK); 
                WordRef key = new WordRef(collectionId, token);
                dbTokens.append(key, data);
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "' (inverted index)", e);
            } catch (ReadOnlyException e) {
                LOG.warn("Read-only error on '" + dbTokens.getFile().getName() + "' (inverted index)", e);
            } catch (IOException e) {
                LOG.error(e.getMessage() + "' in '" + dbTokens.getFile().getName() + "' (inverted index)", e);   
            } finally {
                lock.release();
            }
        }        

		/**
		 * Remove the entries in the current list from the index.
		 */
        //TODO: use VariableInputStream
		public void remove() {
		    //Return early
			if (doc == null)
				return;                    
            final short collectionId = this.doc.getCollection().getId();
            final Lock lock = dbTokens.getLock();
            for (byte currentSection = 0; currentSection <= ATTRIBUTE_SECTION; currentSection++) {
            	//Not very necessary, but anyway...
                switch (currentSection) {
	                case TEXT_SECTION :
	                case ATTRIBUTE_SECTION :
	                    break;
	                default :
	                    throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + 
	                            "' (inverted index)");
	            }
				for (Iterator i = words[currentSection].entrySet().iterator(); i.hasNext();) {
                    //Compute a key for the token
                    Map.Entry entry = (Map.Entry) i.next();
                    OccurrenceList storedOccurencesList = (OccurrenceList) entry.getValue();
                    String token = (String) entry.getKey();
                    WordRef key = new WordRef(collectionId, token);
                    OccurrenceList newOccurencesList = new OccurrenceList();
                    os.clear();
                    try {
                        lock.acquire(Lock.WRITE_LOCK);
                        Value value = dbTokens.get(key);
                        //Does the token already has data in the index ?
					    if (value != null) {
					        //Add its data to the new list    
                            VariableByteArrayInput is = new VariableByteArrayInput(value.getData());
                            //try {
    				            while (is.available() > 0) {
                                    int storedDocId = is.readInt();
                                    byte storedSection = is.readByte();
                                    int termCount = is.readInt();
                                    //TOUNDERSTAND -pb           
                                    int size = is.readFixedInt();
    				                if (storedSection != currentSection || storedDocId != this.doc.getDocId()) {
                                        // data are related to another section or document:
                                        // append them to any existing data
                                        os.writeInt(storedDocId);
                                        os.writeByte(storedSection);
                                        os.writeInt(termCount);
                                        os.writeFixedInt(size);
                                        is.copyRaw(os, size);
                                    } else {
                                        // data are related to our section and document:
                                        // feed the new list with the GIDs
                                        long previousGID = 0;
    				                    for (int j = 0; j < termCount; j++) {                                            
                                            long delta = is.readLong();
                                            long storedGID = previousGID + delta;
                                            int freq = is.readInt();
    				                        // add the node to the new list if it is not 
    				                        // in the list of removed nodes
    				                        if (!storedOccurencesList.contains(storedGID)) {
                                                for (int k = 0; k < freq; k++) {
                                                    newOccurencesList.add(storedGID, is.readInt());
                                                }
    				                        } else {
                                                is.skip(freq);
                                            }
                                            previousGID = storedGID;
    				                    }
    				                }                                    
    				            }
                            //} catch (EOFException e) {
                                //Is it expected ? -pb
                                //LOG.warn("REPORT ME " + e.getMessage(), e);                            
                            //}                                
                            //append the data from the new list
                            if(newOccurencesList.getSize() > 0) {
                                //Don't forget this one
                                newOccurencesList.sort();                                
                                os.writeInt(this.doc.getDocId());                           
                                os.writeByte(currentSection);
                                os.writeInt(newOccurencesList.getTermCount());
                                //TOUNDERSTAND -pb           
                                int lenOffset = os.position();
                                os.writeFixedInt(0);
                                saveOccurrences(newOccurencesList);
                                os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);
                            }
                            //Store the data
    					    if(os.data().size() == 0) 		    	
    							dbTokens.remove(key);                            
					        else
					            if (dbTokens.update(value.getAddress(), key, os.data()) == BFile.UNKNOWN_ADDRESS) {
                                    LOG.error("Could not update index data for token '" +  token + "' in '" + dbTokens.getFile().getName() + 
                                        "' (inverted index)");
                                }                    						    
					    } else {                           
				            if (dbTokens.put(key, os.data()) == BFile.UNKNOWN_ADDRESS) {
                                LOG.error("Could not put index data for token '" +  token + "' in '" + dbTokens.getFile().getName() + 
                                    "' (inverted index)");  
                            }                    
					    }
					} catch (LockException e) {
                        LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "' (inverted index)", e);
                    } catch (ReadOnlyException e) {
                        LOG.warn("Read-only error on '" + dbTokens.getFile().getName() + "' (inverted index)", e);
                    } catch (IOException e) {
                        LOG.error(e.getMessage() + "' in '" + dbTokens.getFile().getName() + "' (inverted index)", e);
                    } finally {
					    lock.release();
					}
				}
				words[currentSection].clear();
			}
		}

		public void reindex(DocumentImpl document, StoredNode node) {
            final short collectionId = document.getCollection().getId();
		    final Lock lock = dbTokens.getLock();
            for (byte currentSection = 0; currentSection <= ATTRIBUTE_SECTION; currentSection++) {
            	//Not very necessary, but anyway...
                switch (currentSection) {
	                case TEXT_SECTION :
	                case ATTRIBUTE_SECTION :
	                    break;
	                default :
	                    throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + 
	                        "' (inverted index)");
	            }
		        for (Iterator i = words[currentSection].entrySet().iterator(); i.hasNext();) {                   
                    //Compute a key for the token
                    Map.Entry entry = (Map.Entry) i.next();
                    String token = (String) entry.getKey();                    
                    WordRef key = new WordRef(collectionId, token);
                    OccurrenceList storedOccurencesList = (OccurrenceList) entry.getValue();
                    os.clear();                     
		            try {
		                lock.acquire(Lock.WRITE_LOCK);
                        VariableByteInput is = dbTokens.getAsStream(key);	
                        //Does the token already has data in the index ?
		                if (is != null) {
		                    //Add its data to the new list    
		                    //try {
		                        while (is.available() > 0) {
                                    int storedDocId = is.readInt();
                                    byte storedSection = is.readByte();
                                    int termCount = is.readInt();
                                    //TOUNDERSTAND -pb         
                                    int size = is.readFixedInt();
		                            if (storedSection != currentSection || storedDocId != document.getDocId()) {
                                        // data are related to another section or document:
                                        // append them to any existing data
		                                os.writeInt(storedDocId);
		                                os.writeByte(storedSection);
		                                os.writeInt(termCount);
		                                os.writeFixedInt(size);
		                                is.copyRaw(os, size);
		                            } else {
                                        // data are related to our section and document:
                                        // feed the new list with the GIDs
                                        long previousGID = 0;
		                                for (int j = 0; j < termCount; j++) {
                                            long delta = is.readLong();
                                            long storedGID = previousGID + delta;
                                            int freq = is.readInt();
		                                    if (node == null) {
		                                        if (document.getTreeLevel(storedGID) < document.getMetadata().reindexRequired()) {
                                                    for (int l = 0; l < freq; l++) {
                                                        //Note that we use the existing list
                                                        storedOccurencesList.add(storedGID, is.readInt());
                                                    }
                                                } else
                                                    is.skip(freq);
                                                
		                                    } else {
		                                        if (!NodeSetHelper.isDescendantOrSelf(
                                                        document,
                                                        node.getGID(),
                                                        storedGID)) {
                                                    for (int l = 0; l < freq; l++) {
                                                        //Note that we use the existing list
                                                        storedOccurencesList.add(storedGID, is.readInt());
                                                    }
                                                } else
                                                    is.skip(freq);
                                            }
                                            previousGID = storedGID;
		                                }
		                            }
		                        }
		                    //} catch (EOFException e) {
		                        //EOF is expected here
		                    //}
		                }
                        if (storedOccurencesList.getSize() > 0) {
                            //append the data from the new list
                            storedOccurencesList.sort();
    		                os.writeInt(document.getDocId());
                            os.writeByte(currentSection);
    		                os.writeInt(storedOccurencesList.getTermCount());
                            //TOUNDERSTAND -pb         
                            int lenOffset = os.position();
    	                    os.writeFixedInt(0);
                            saveOccurrences(storedOccurencesList);
    		                os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);
                        }
		                //Store the data		                
	                    if (is == null) {
                            //TOUNDERSTAND : Should is be null, what will there be in os.data() ? -pb
	                        if (dbTokens.put(key, os.data()) == BFile.UNKNOWN_ADDRESS) {
                                LOG.error("Could not put index data for token '" +  token + "' in '" + dbTokens.getFile().getName() + 
                                        "' (inverted index)");
                            }
	                    } else {  
                            long address = ((BFile.PageInputStream) is).getAddress();
	                        if (dbTokens.update(address, key, os.data()) == BFile.UNKNOWN_ADDRESS) {
                                LOG.error("Could not update index data for value '" +  token +  "' in '" + dbTokens.getFile().getName() + 
                                    "' (inverted index)"); 
                            }
	                    }		                
		            } catch (LockException e) {
                        LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "' (inverted index)", e);		               
                    } catch (ReadOnlyException e) {
                        LOG.warn("Read-only error on '" + dbTokens.getFile().getName() + "' (inverted index)", e);                        
		            } catch (IOException e) {
		                LOG.error("io error while reindexing word '" + token  + "' in '" + dbTokens.getFile().getName() + "' (inverted index)", e);		               
		            } finally {
		                lock.release(Lock.WRITE_LOCK);
		            }
		        }
		        words[currentSection].clear();
		    }
		}

		private void saveOccurrences(OccurrenceList storedOccurencesList) {
			long previousGID = 0;
			for (int m = 0; m < storedOccurencesList.getSize(); ) {
			    long delta = storedOccurencesList.nodes[m] - previousGID;
			    os.writeLong(delta);                            
			    int freq = storedOccurencesList.getOccurrences(m);
			    os.writeInt(freq);
			    for (int n = 0; n < freq; n++) {
			        os.writeInt(storedOccurencesList.offsets[m + n]);
			    }
			    previousGID = storedOccurencesList.nodes[m];
			    m += freq;
			}
		}
	}
	
	private class IndexCallback implements BTreeCallback {
		
		List matches = new ArrayList();
		TermMatcher matcher;
		XQueryContext context;
		
		public IndexCallback(XQueryContext context, TermMatcher matcher) {
			this.matcher = matcher;
			this.context = context;
		}
		
		public String[] getMatches() {
			String[] a = new String[matches.size()];
			return (String[]) matches.toArray(a);
		}
		
		/* (non-Javadoc)
		 * @see org.dbxml.core.filer.BTreeCallback#indexInfo(org.dbxml.core.data.Value, long)
		 */
		public boolean indexInfo(Value key, long pointer) throws TerminatedException {
		    if(context != null)
		        context.proceed();			
			try {
                final String word = new String(key.getData(), 2, key.getLength() - 2, "UTF-8");
                if (matcher.matches(word))
                    matches.add(word);
                return true;
			} catch (UnsupportedEncodingException e) {
                LOG.error(e.getMessage(), e);
				//word = new String(key.getData(), 2, key.getLength() - 2);
                return true;
			} 
		}
	}
	
	private final class SearchCallback implements BTreeCallback {

		DocumentSet docs;
		TermMatcher matcher;
		NodeSet result;
		NodeSet contextSet;
		XQueryContext context;
		XMLString word = new XMLString(64);
        
		public SearchCallback(XQueryContext context, TermMatcher comparator, NodeSet result,
				NodeSet contextSet, DocumentSet docs) {
			this.matcher = comparator;
			this.result = result;
			this.docs = docs;
			this.contextSet = contextSet;
			this.context = context;
		}

		public boolean indexInfo(Value key, long pointer) throws TerminatedException {            
            VariableByteInput is;
            try {
                is = dbTokens.getAsStream(pointer);
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                return true; 
            } 
            word.reuse();
            word = UTF8.decode(key.getData(), 2, key.getLength() - 2, word);
			if (matcher.matches(word)) {                 
				try {
					while (is.available() > 0) {                        
					    if(context != null)
					        context.proceed();                        
                        int storedDocId = is.readInt();
                        byte storedSection = is.readByte();
                        int termCount = is.readInt();
                        //TOUNDERSTAND -pb
                        int size = is.readFixedInt();
                        DocumentImpl storedDocument = docs.getDoc(storedDocId);                        
                        //Exit if the document is not concerned
						if (storedDocument == null) {
							is.skipBytes(size);
							continue;
						}                        
                        long previousGID = 0;
						for (int j = 0; j < termCount; j++) {
                            long delta = is.readLong();
                            long storedGID = previousGID + delta;
                            int freq = is.readInt();
                            NodeProxy storedNode;
                            switch (storedSection) {
	                            case TEXT_SECTION :
	                                storedNode = new NodeProxy(storedDocument, storedGID, Node.TEXT_NODE);
	                                break;
	                            case ATTRIBUTE_SECTION :
	                                storedNode = new NodeProxy(storedDocument, storedGID, Node.ATTRIBUTE_NODE);
	                                break;
	                            default :
	                                throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + "'");
	                        } 
							if (contextSet != null) {
                                NodeProxy parentNode;  
                                switch (storedSection) {
                                    case TEXT_SECTION :
                                        parentNode = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                        break;
                                    case ATTRIBUTE_SECTION :
                                        if (contextSet instanceof VirtualNodeSet) {
                                        	parentNode = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                        	if (parentNode != null && parentNode.getGID() != storedGID)
                                        		parentNode = null;
                                        } else {
                                            parentNode = contextSet.get(storedNode);
                                        }
                                        break;
                                    default :
                                        throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + "'");
                                }
								if (parentNode != null) {
                                    Match match = new Match(storedGID, word.toString(), freq);
                                    readOccurrences(freq, is, match, word.length());
                                    parentNode.addMatch(match);
                                    int sizeHint = contextSet.getSizeHint(storedDocument);
									result.add(parentNode, sizeHint);
								} else
                                    is.skip(freq);
							} else {
                                Match match = new Match(storedGID, word.toString(), freq);
							    readOccurrences(freq, is, match, word.length());
                                storedNode.addMatch(match);
							    result.add(storedNode, Constants.NO_SIZE_HINT);
                            }
                            previousGID = storedGID;
						}
					}
				//} catch (EOFException e) {
					// EOFExceptions are normal
				} catch (IOException e) {
                    LOG.error(e.getMessage() + " in '" + dbTokens.getFile().getName() + "'", e);   
                    //TODO : return early -pb
				}
			}
            
            //TOUNDERSTAND : why sort here ? -pb
			if (contextSet != null)
				((ExtArrayNodeSet) result).sort();
            
			return true;
		}
	}
	
	private final class IndexScanCallback implements BTreeCallback{
		
		private DocumentSet docs;
		private NodeSet contextSet;
		private Map map = new TreeMap();
		
		IndexScanCallback(DocumentSet docs, NodeSet contextSet) {
			this.docs = docs;
			this.contextSet = contextSet;
		}
		
		/* (non-Javadoc)
		 * @see org.dbxml.core.filer.BTreeCallback#indexInfo(org.dbxml.core.data.Value, long)
		 */
		public boolean indexInfo(Value key, long pointer) throws TerminatedException {
            
            String term;
            try {
                term = new String(key.getData(), 2, key.getLength() - 2, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LOG.error(e.getMessage(), e);
                //term = new String(key.getData(), 2, key.getLength() - 2);
                return true;
            }
            
            VariableByteInput is;
            try {
                is = dbTokens.getAsStream(pointer);
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                return true;
            } 
           
			try {
				while (is.available() > 0) {
                    boolean docAdded = false;                    
                    int storedDocId = is.readInt();
                    byte storedSection = is.readByte();
                    int termCount = is.readInt();
                    //TOUNDERSTAND -pb
                    int size = is.readFixedInt();
                    DocumentImpl storedDocument = docs.getDoc(storedDocId);
                    //Exit if the document is not concerned
					if (storedDocument == null) {
						is.skipBytes(size);
						continue;
					}					
                    long previousGID = 0;
					for (int j = 0; j < termCount; j++) {
                        long delta = is.readLong(); 
                        long storedGID = previousGID + delta;
                        int freq = is.readInt();                        
                        is.skip(freq);
						if (contextSet != null) {
                            boolean include = false;
                            NodeProxy parentNode = contextSet.parentWithChild(storedDocument, storedGID, false, true);                           
                            switch (storedSection) {
                                case TEXT_SECTION :
                                    //TODO : also test on Node.TEXT_NODE like below ? -pb                                    
                                    include = (parentNode != null);
                                    break;
                                case ATTRIBUTE_SECTION :
                                    include = (parentNode != null && parentNode.getNodeType() == Node.ATTRIBUTE_NODE);
                                    break;
                                default :
                                    throw new IllegalArgumentException("Invalid section type  in '" + dbTokens.getFile().getName() + "'");
                            } 
                            if (include) {
                                Occurrences oc = (Occurrences) map.get(term);
                                if (oc == null) {
                                    oc = new Occurrences(term);
                                    map.put(term, oc);
                                }
                                if (!docAdded) {
                                    oc.addDocument(storedDocument);
                                    docAdded = true;
                                }
                                oc.addOccurrences(freq);
                            }                            
						}						
                        previousGID = storedGID;
					}
				}
			//} catch(EOFException e) {
                //EOFExceptions are expected 
			} catch(IOException e) {
                LOG.error(e.getMessage() + " in '" + dbTokens.getFile().getName() + "'", e);   
                //TODO : return early -pb
			}
			return true;
		}
	}
	
    private static class OccurrenceList {
        
        private long nodes[] = new long[4];
        private int offsets[] = new int[4];
        
        private int position = 0;
        
        void add(long id, int offset) {
            ensureCapacity(position);
            nodes[position] = id;
            offsets[position++] = offset;
        }
        
        int getSize() {
            return position;
        }
        
        int getTermCount() {
            int count = 1;
            for (int i = 1; i < position; i++) {
                if (nodes[i] != nodes[i - 1])
                    count++;
            }
            return count;
        }
        
        int getOccurrences(int start) {
            int count = 1;
            for (int i = start + 1; i < position; i++) {
                if (nodes[i] == nodes[start])
                    count++;
                else
                    break;
            }
            return count;
        }
        
        boolean contains(long l) {
            for (int i = 0; i < position; i++)
                if (nodes[i] == l)
                    return true;
            return false;
        }
        
        private void ensureCapacity(int count) {
            if (count == nodes.length) {
                long[] nn = new long[count * 2];
                int[] no = new int[nn.length];
                System.arraycopy(nodes, 0, nn, 0, count);
                System.arraycopy(offsets, 0, no, 0, count);
                nodes = nn;
                offsets = no;
            }
        }
        
        void sort() {
            sort(0, position - 1);
        }
        
        /** Standard quicksort */
        //TODO : use methods in org.exist.util ?
        private void sort(int lo0, int hi0) {
            int lo = lo0;
            int hi = hi0;
            
            if ( hi0 > lo0) {
                int mid = ( lo0 + hi0 ) / 2;

                while ( lo <= hi ) {
                    while (( lo < hi0 ) && ( nodes[lo] < nodes[mid] ))
                        ++lo;
                    while (( hi > lo0 ) && ( nodes[hi] > nodes[mid]))
                        --hi;
                    if ( lo <= hi ) {
                        if (lo!=hi) {
                            // swap
                            long l = nodes[lo];
                            nodes[lo] = nodes[hi];
                            nodes[hi] = l;
                            
                            int i = offsets[lo];
                            offsets[lo] = offsets[hi];
                            offsets[hi] = i;
                            
                            if (lo==mid) {
                                mid=hi;
                            } else if (hi==mid) {
                                mid=lo;
                            }
                        }
                        ++lo;
                        --hi;
                    }
                }
                if ( lo0 < hi )
                    sort( lo0, hi );
                if ( lo < hi0 )
                    sort( lo, hi0 );
            }
        }
    }
    
	private static class TermFrequencyList {
		
		protected static class TermFreq implements Comparable {
			
			long l;
			int count = 1;
			TermFreq next = null;
			
			public TermFreq(long l) {
				this.l = l;
			}
			
			public void increment() {
				++count;
			}
			
			public int compareTo(Object o) {
				final TermFreq other = (TermFreq)o;
				if(l == other.l)
					return Constants.EQUAL;
				else
					return l < other.l ? Constants.INFERIOR : Constants.SUPERIOR;
			}
		}
		
		private TermFreq first = null;
		private TermFreq last = null;
		private int count = 0;
		
		public void add( long l ) {
			if(first == null) {
				first = new TermFreq( l );
				last = first;
			} else {
				TermFreq next = new TermFreq( l );
				last.next = next;
				last = next;
			}
			++count;
		}
		
		public void incLastTerm() {
			if(last != null)
				last.increment();
		}
		
		public void setLastTermFreq(int freq) {
			if(last != null)
				last.count = freq;
		}
		
		public long getLast() {
	    	if(last != null)
	    		return last.l;
	    	else
	    		return -1;
	    }
		
		public boolean contains(long l) {
	    	TermFreq next = first;
	    	while( next != null ) {
	    		if(next.l == l)
	    			return true;
	    		next = next.next;
	    	}
	    	return false;
	    }
		
		public int getSize() {
			return count;
		}
		
		public TermFreq[] toArray() {
			TermFreq[] data = new TermFreq[count];
			TermFreq next = first;
			int i = 0;
			while( next != null ) {
				data[i++] = next;
				next = next.next;
			}
			return data;
		}
	}

	private final static class WordRef extends Value {

		public WordRef(short collectionId) {
			data = new byte[2];
			ByteConversion.shortToByte(collectionId, data, 0);
			len = 2;
		}

		public WordRef(short collectionId, String word) {
			len = UTF8.encoded(word) + 2;
			data = new byte[len];
			ByteConversion.shortToByte(collectionId, data, 0);
			UTF8.encode(word, data, 2);
		}

		public String toString() {
			return ByteConversion.byteToShort(data, pos) + new String(data, pos, len);
		}
	}
    
}
