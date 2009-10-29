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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage;

//import java.io.EOFException;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.fulltext.ElementContent;
import org.exist.fulltext.FTMatch;
import org.exist.numbering.NodeId;
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

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

    public static final String FILE_NAME = "words.dbx";
    public static final String  FILE_KEY_IN_CONFIG = "db-connection.words";	
	
    public static final double DEFAULT_WORD_CACHE_GROWTH = 1.4;
    public static final double DEFAULT_WORD_KEY_THRESHOLD = 0.01;  
    public static final double DEFAULT_WORD_VALUE_THRESHOLD = 0.015;
    
    public final static byte TEXT_SECTION = 0;
	public final static byte ATTRIBUTE_SECTION = 1;
    public final static byte QNAME_SECTION = 2;
 
    private final static byte IDX_GENERIC = 0;
    private final static byte IDX_QNAME = 1;
    
    public static int ATTRIBUTE_BY_QNAME = 0;
    public static int ATTRIBUTE_NOT_BY_QNAME = 1;

    public static int TOKENIZE = 0;
    public static int DO_NOT_TOKENIZE = 1;
    public static int TEXT_BY_QNAME = 2;
    public static int FOURTH_OPTION = 3;
    
    public final static int LENGTH_NODE_TYPE = 1; //sizeof byte
    public final static int LENGTH_NODE_IDS_FREQ_OFFSETS = 4; //sizeof int
    
    public final static int OFFSET_NODE_TYPE = 0;    
    public final static int OFFSET_ELEMENT_CHILDREN_COUNT = OFFSET_NODE_TYPE + LENGTH_NODE_TYPE; //1
    public final static int OFFSET_ATTRIBUTE_DLN_LENGTH = OFFSET_NODE_TYPE + LENGTH_NODE_TYPE; //1
    public final static int OFFSET_TEXT_DLN_LENGTH = OFFSET_NODE_TYPE + LENGTH_NODE_TYPE; //1
    public final static int OFFSET_DLN = OFFSET_TEXT_DLN_LENGTH + NodeId.LENGTH_NODE_ID_UNITS; //3    

    /** Length limit for the tokens */
	public final static int MAX_TOKEN_LENGTH = 2048;
	
	/** The datastore for this token index */
	protected BFile dbTokens;
	protected InvertedIndex invertedIndex;

    /** The current document */
    private DocumentImpl doc;

    /** Work output Stream that should be cleared before every use */
    private VariableByteOutputStream os = new VariableByteOutputStream(7);    

    public NativeTextEngine(DBBroker broker, BFile dbFile, Configuration config) throws DBException {
		super(broker, config);	

        this.invertedIndex = new InvertedIndex();  
        this.dbTokens = dbFile;
    }
    
    public String getFileName() {
    	return FILE_NAME;      
    }
    
    public String getConfigKeyForFile() {
    	return FILE_KEY_IN_CONFIG;
    }
    
    public NativeTextEngine getInstance() {
    	return this;
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
        if (this.doc != null && this.doc.getDocId() != document.getDocId())
            flush();
    	this.doc = document;
    	invertedIndex.setDocument(doc);
    }    
    
    /**
     * Indexes the tokens contained in an attribute.
     * 
     * @param node The attribute to be indexed
     */
    //TODO : unify functionalities with storeText -pb
    public void storeAttribute(AttrImpl node, NodePath currentPath, int indexingHint, FulltextIndexSpec indexSpec, boolean remove) {
    	if ((indexingHint & ATTRIBUTE_BY_QNAME) == ATTRIBUTE_BY_QNAME || 
    			(indexingHint & ATTRIBUTE_NOT_BY_QNAME) == ATTRIBUTE_NOT_BY_QNAME) {
	        //final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
	        //TODO : case conversion should be handled by the tokenizer -pb
	        tokenizer.setText(node.getValue().toLowerCase());   
	        TextToken token;
	        while (null != (token = tokenizer.nextToken())) {
	            if (token.length() > MAX_TOKEN_LENGTH) {
//	            	LOG.warn("Token length exceeded " + MAX_TOKEN_LENGTH + ": " + token.getText().substring(0,20) + "...");
	                continue;
	            } 
	            if (stoplist.contains(token)) {
	                continue;
	            }            
                //TODO : the tokenizer should strip unwanted token types itself -pb
	            if (!token.isAlpha() && indexSpec != null && !indexSpec.getIncludeAlphaNum()) {  
                    continue;
	            }
	            if (indexingHint == ATTRIBUTE_BY_QNAME)
	                invertedIndex.addAttribute(token, node, remove);
	            else
	                invertedIndex.addAttribute(token, node.getNodeId(), remove);
	        }
    	}
    }
    
    //TODO : unify with above choosing one of these 2 strategies :
    //1) compute the indexing strategy from thhe broker (introduce some kind of dependency)
    //2) read the configuration from the indexer (possible performance loss)
	public void storeAttribute(AttrImpl node, NodePath currentPath, int indexingHint, RangeIndexSpec idx, boolean remove) {
	}
    
    /**
     * Indexes the tokens contained in a text node.
     * 
     * @param indexSpec The index configuration
     * @param node The text node to be indexed
     * @param indexingHint
     *                if <code>true</code>, given text is indexed as a single token
     *                if <code>false</code>, it is tokenized before being indexed
     */
    //TODO : use an indexSpec member in order to get rid of <code>noTokenizing</code>
    public void storeText(CharacterDataImpl node, int indexingHint, FulltextIndexSpec indexSpec, boolean remove) {
    	if (indexingHint == TOKENIZE || indexingHint == DO_NOT_TOKENIZE) {
	        //final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
	        //TODO : case conversion should be handled by the tokenizer -pb
	        final XMLString t = node.getXMLString().transformToLower();
	        TextToken token;
	        if (indexingHint == DO_NOT_TOKENIZE) {            
	            token = new TextToken(TextToken.ALPHA, t, 0, t.length());
	            //invertedIndex.setDocument(doc);
	            invertedIndex.addText(token, node.getNodeId(), remove);
	        } else if (indexingHint == TOKENIZE){
	            tokenizer.setText(t);
	            while (null != (token = tokenizer.nextToken())) {
	                if (token.length() > MAX_TOKEN_LENGTH) {
//	                	LOG.warn("Token length exceeded " + MAX_TOKEN_LENGTH + ": " + token.getText().substring(0,20) + "...");
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
	                //invertedIndex.setDocument(doc);
	                invertedIndex.addText(token, node.getNodeId(), remove);
	            }
	        }
    	}
    } 

    public void storeText(StoredNode parent, ElementContent text, int indexingHint, FulltextIndexSpec indexSpec, boolean remove) {
        //final DocumentImpl doc = (DocumentImpl)parent.getOwnerDocument();
        //TODO : case conversion should be handled by the tokenizer -pb
        TextToken token;
        ElementContent.TextSpan span = text.getFirst();
        XMLString data = null;
        int currentOffset = 0;
        while (span != null) {
            if (data == null)
                data = span.getContent().transformToLower();
            else {
                currentOffset = data.length();
                data.append(span.getContent().transformToLower());
            }
            tokenizer.setText(data, currentOffset);
            while (null != (token = tokenizer.nextToken())) {
                if (token.length() > MAX_TOKEN_LENGTH) {
                    LOG.warn("Token length exceeded " + MAX_TOKEN_LENGTH + ": " + token.getText().substring(0,20) + "...");
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
                //invertedIndex.setDocument(doc);
                if (indexingHint == TEXT_BY_QNAME)
                    invertedIndex.addText(token, (ElementImpl) parent, remove);
                else
                    invertedIndex.addText(token, parent.getNodeId(), remove);
            }
            span = span.getNext();
        }
    }

    public void storeText(TextImpl node, NodePath currentPath, int indexingHint) {
        // TODO Auto-generated method stub      
    }

    public void removeNode(StoredNode node, NodePath currentPath, String content) {
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
            lock.release(Lock.WRITE_LOCK);
        }
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.ContentLoadingObserver#flush()
     */    
	public void flush() {
		invertedIndex.flush();
	}

	public void remove() {
		invertedIndex.remove();
	}
    
    /* Drop all index entries for the given collection.
     * @see org.exist.storage.ContentLoadingObserver#dropIndex(org.exist.collections.Collection)
     */
    public void dropIndex(Collection collection) {
        final Lock lock = dbTokens.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            // remove generic index
            Value value = new WordRef(collection.getId());
            dbTokens.removeAll(null, new IndexQuery(IndexQuery.TRUNC_RIGHT, value));
            // remove QName index
            value = new QNameWordRef(collection.getId());
            dbTokens.removeAll(null, new IndexQuery(IndexQuery.TRUNC_RIGHT, value));
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
        invertedIndex.dropIndex(document);
    }

    public NodeSet getNodesContaining(XQueryContext context, DocumentSet docs, NodeSet contextSet, int axis,
	        QName qname, String expr, int type, boolean matchAll) throws TerminatedException {
		if (type == DBBroker.MATCH_EXACT && containsWildcards(expr)) {
            //TODO : log this fallback ? -pb
			type = DBBroker.MATCH_WILDCARDS;
		}
		switch (type) {
			case DBBroker.MATCH_EXACT :
				return getNodesExact(context, docs, contextSet, axis, qname, expr);
                //TODO : stricter control -pb
			default :
				return getNodesRegexp(context, docs, contextSet, axis, qname, expr, type, matchAll);
		}
	}


	/** 
         * Get all nodes whose content exactly matches the give expression.
	 */
	public NodeSet getNodesExact(XQueryContext context, DocumentSet docs, NodeSet contextSet, int axis,
                                 QName qname, String expr)
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
        final NodeSet result = new NewArrayNodeSet(docs.getDocumentCount(), 250);
		for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
            final int collectionId = ((Collection) iter.next()).getId();
            Value key;
            if (qname == null)
                key = new WordRef(collectionId, token);
            else {
                key = new QNameWordRef(collectionId, qname, token, broker.getBrokerPool().getSymbols());
//                LOG.debug("Using qname: " + qname.toString() + " " + key.dump() + " '" + key.toString() + "'");
            }
			final Lock lock = dbTokens.getLock();
			try {
				lock.acquire(Lock.READ_LOCK);
                VariableByteInput is = dbTokens.getAsStream(key);
                //Does the token already has data in the index ?
				if (is == null)
					continue;				
				while (is.available() > 0) {
                    int storedDocId = is.readInt();
                    int storedSection = is.readByte();
                    int gidsCount = is.readInt();
                    //Read (variable) length of node IDs + frequency + offsets       
                    int length = is.readFixedInt();
                    DocumentImpl storedDocument = docs.getDoc(storedDocId);
                    //Exit if the document is not concerned
                    if (storedDocument == null) {
                        is.skipBytes(length);
                        continue;
                    }
                    //Process the nodes
                    NodeId previous = null;
                    for (int m = 0; m < gidsCount; m++) {
//                        NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(is);
                        NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;
                        int freq = is.readInt();
                        NodeProxy storedNode;
                        switch (storedSection) {
                            case ATTRIBUTE_SECTION :
                                storedNode = new NodeProxy(storedDocument, nodeId, Node.ATTRIBUTE_NODE);
                                break;
                            case TEXT_SECTION :
                                storedNode = new NodeProxy(storedDocument, nodeId, Node.TEXT_NODE);
                                break;
                            case QNAME_SECTION :
                                storedNode = new NodeProxy(storedDocument, nodeId,
                                        qname.getNameType() == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE);
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
                                        if (parent != null && !parent.getNodeId().equals(storedNode.getNodeId()))
                                            parent = null;
                                    } else
                                        parent = contextSet.get(storedNode);
                                    break;
                                case QNAME_SECTION:
                                case TEXT_SECTION :
                                    parent = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                    break;
                                default :
                                    throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + "'");
                            }
                            if (parent != null) {
                                Match match = new FTMatch(-1, nodeId, token, freq);
                                readOccurrences(freq, is, match, token.length());
                                if (axis == NodeSet.ANCESTOR) {
                                    parent.addMatch(match);
                                    int sizeHint = contextSet.getSizeHint(storedDocument);
                                    result.add(parent, sizeHint);
                                } else {
                                    storedNode.addMatch(match);
                                    int sizeHint = contextSet.getSizeHint(storedDocument);
                                    result.add(storedNode, sizeHint);
                                }
                            } else {
							    is.skip(freq);
                            }
						// otherwise, we add all text nodes without check
						} else {
                            Match match = new FTMatch(-1, nodeId, token, freq);
                            readOccurrences(freq, is, match, token.length());
                            storedNode.addMatch(match);
							result.add(storedNode, Constants.NO_SIZE_HINT);							
						}
						context.proceed();
					}
				}
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "'", e);              
			} catch (IOException e) {
                LOG.error(e.getMessage() + " in '" + dbTokens.getFile().getName() + "'", e);                
                //TODO : return ?
			} finally {
				lock.release(Lock.READ_LOCK);
			}
		}
		return result;
	}
    
    private NodeSet getNodesRegexp(XQueryContext context, DocumentSet docs, NodeSet contextSet, int axis, QName qname,
            String expr, int type, boolean matchAll) throws TerminatedException {
        //Return early
        if (expr == null)
            return null;
        if (stoplist.contains(expr))
            return null;
        //TODO : case conversion should be handled by the tokenizer -pb
        expr = expr.toLowerCase();

        // if the regexp starts with a char sequence, we restrict the index scan to entries starting with
        // the same sequence. Otherwise, we have to scan the whole index.
        CharSequence start = "";
        if (matchAll) {
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < expr.length(); i++) {
                if (Character.isLetterOrDigit(expr.charAt(i)))
                    buf.append(expr.charAt(i));
                else
                    break;
            }
            start = buf;
        }
        try {
            TermMatcher comparator = new RegexMatcher(expr, type, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE,
                    matchAll);
            return getNodes(context, docs, contextSet, axis, qname, comparator, start);
        } catch (EXistException e) {
            return null;
        }
    }
    
	/* Return all nodes for wich the matcher matches.
	 * @see org.exist.storage.TextSearchEngine#getNodes(org.exist.xquery.XQueryContext, org.exist.dom.DocumentSet, org.exist.dom.NodeSet, org.exist.storage.TermMatcher, java.lang.CharSequence)
	 */
	public NodeSet getNodes(XQueryContext context, DocumentSet docs, NodeSet contextSet, int axis, QName qname,
			TermMatcher matcher, CharSequence startTerm) throws TerminatedException {
        if (LOG.isTraceEnabled() && qname != null)
            LOG.trace("Index lookup by QName: " + qname);
        final NodeSet result = new NewArrayNodeSet();
        final SearchCallback cb = new SearchCallback(context, matcher, result, contextSet, axis, docs, qname);
		final Lock lock = dbTokens.getLock();		
		for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
            final int collectionId = ((Collection) iter.next()).getId();        
            //Compute a key for the token                
            Value value;
			if (startTerm != null && startTerm.length() > 0) {
                //TODO : case conversion should be handled by the tokenizer -pb
                if (qname == null) {
                    value = new WordRef(collectionId, startTerm.toString().toLowerCase());
                } else {
                    value = new QNameWordRef(collectionId, qname, startTerm.toString().toLowerCase(),
                            broker.getBrokerPool().getSymbols());
                }
            } else {
                if (qname == null) {
                    value = new WordRef(collectionId);
                } else {
                    value = new QNameWordRef(collectionId, qname, broker.getBrokerPool().getSymbols());
                }
            }
			IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, value);
			try {
				lock.acquire(Lock.READ_LOCK);	
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
				lock.release(Lock.READ_LOCK);
			}
		}
		return result;
	}

	public String[] getIndexTerms(DocumentSet docs, TermMatcher matcher) {
        final IndexCallback cb = new IndexCallback(null, matcher);		
		final Lock lock = dbTokens.getLock();		
		for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
            final int collectionId = ((Collection) iter.next()).getId();  
            //Compute a key for the token                                 
            Value value = new WordRef(collectionId);
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, value);
			try {
				lock.acquire(Lock.READ_LOCK);
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
				lock.release(Lock.READ_LOCK);
			}
		}
		return cb.getMatches();
	}    

	public Occurrences[] scanIndexTerms(DocumentSet docs, NodeSet contextSet,
                                        String start, String end)
            throws PermissionDeniedException {
        final IndexScanCallback cb = new IndexScanCallback(docs, contextSet, false);
        final Lock lock = dbTokens.getLock();
		for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {            
            final int collectionId = ((Collection) i.next()).getId();          
            final IndexQuery query;
            if (start == null) {
            	Value startRef = new WordRef(collectionId);            	
            	query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startRef);
            } else if (end == null) {
            	Value startRef = new WordRef(collectionId, start.toLowerCase());
            	query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startRef);
            } else {
                Value startRef = new WordRef(collectionId,  start.toLowerCase());
                Value endRef = new WordRef(collectionId, end.toLowerCase());
    			query = new IndexQuery(IndexQuery.BW, startRef, endRef);
            }
			try {
				lock.acquire(Lock.READ_LOCK);
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
				lock.release(Lock.READ_LOCK);
			}
		}		
		Occurrences[] result = new Occurrences[cb.map.size()];		
		return (Occurrences[]) cb.map.values().toArray(result);
	}

    public Occurrences[] scanIndexTerms(DocumentSet docs, NodeSet contextSet, QName[] qnames,
                                        String start, String end)
            throws PermissionDeniedException {
        final Lock lock = dbTokens.getLock();
        final IndexScanCallback cb = new IndexScanCallback(docs, contextSet, true);
        for (int q = 0; q < qnames.length; q++) {
            for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
                final int collectionId = ((Collection) i.next()).getId();
                final IndexQuery query;
                if (start == null) {
                    Value startRef = new QNameWordRef(collectionId, qnames[q],
                            broker.getBrokerPool().getSymbols());
                    query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startRef);
                } else if (end == null) {
                    Value startRef = new QNameWordRef(collectionId, qnames[q],
                        start.toLowerCase(), broker.getBrokerPool().getSymbols());
                    query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startRef);
                } else {
                    Value startRef = new QNameWordRef(collectionId, qnames[q], start.toLowerCase(),
                        broker.getBrokerPool().getSymbols());
                    Value endRef = new QNameWordRef(collectionId, qnames[q], end.toLowerCase(),
                        broker.getBrokerPool().getSymbols());
                    query = new IndexQuery(IndexQuery.BW, startRef, endRef);
                }
                try {
                    lock.acquire(Lock.READ_LOCK);
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
                    lock.release(Lock.READ_LOCK);
                }
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
        for (int n = 0; n < freq; n++) {
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
        short type = Signatures.getType(data[OFFSET_NODE_TYPE]);
        switch (type) {
            case Node.ELEMENT_NODE :
                int childrenCount = ByteConversion.byteToInt(data, OFFSET_ELEMENT_CHILDREN_COUNT);
                for (int i = 0; i < childrenCount; i++)
                	//recursive call on children
                    collect(words, domIterator);
                break;
            case Node.TEXT_NODE :
                int dlnLen = ByteConversion.byteToShort(data, OFFSET_TEXT_DLN_LENGTH);
            	int nodeIdLen = broker.getBrokerPool().getNodeFactory().lengthInBytes(dlnLen, data, OFFSET_DLN);
                try {
                	int readOffset = nodeIdLen + OFFSET_DLN;
                    String s = new String(data, readOffset, data.length - readOffset, "UTF-8");
                    tokenizer.setText(s);
                    TextToken token;
                    while (null != (token = tokenizer.nextToken())) {
                        String word = token.getText();
                        if (stoplist.contains(word))
                            continue;
                        words.add(word.toLowerCase());
                    }
                } catch (UnsupportedEncodingException e) {
                    LOG.error(e.getMessage(), e);
                }
                break;
            case Node.ATTRIBUTE_NODE :
                byte idSizeType = (byte) (data[OFFSET_NODE_TYPE] & 0x3);
                boolean hasNamespace = (data[OFFSET_NODE_TYPE] & 0x10) == 0x10;
                dlnLen = ByteConversion.byteToShort(data, OFFSET_ATTRIBUTE_DLN_LENGTH);
                nodeIdLen = broker.getBrokerPool().getNodeFactory().lengthInBytes(dlnLen, data, OFFSET_DLN);
                int readOffset = Signatures.getLength(idSizeType) + nodeIdLen + OFFSET_DLN;
                if (hasNamespace) {
                	//TODO : check the order in wich both info are read (and discarded)
					readOffset += SymbolTable.LENGTH_LOCAL_NAME; // skip namespace id
					final short prefixLen = ByteConversion.byteToShort(data, readOffset);
					readOffset += prefixLen + SymbolTable.LENGTH_NS_URI; // skip prefix
				}
                try {
                	String val = new String(data, readOffset, data.length - readOffset, "UTF-8");
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
                }                
                break;
           default :
               //Other types are ignored : some may be useful though -pb
               //TOUNDERSTAND : it looks like other types (got : Node.PROCESSING_INSTRUCTION_NODE)
               //are stored in the index ??? -pb
        }
    }
    
    public void closeAndRemove() {
    	config.setProperty(getConfigKeyForFile(), null);    	
    	dbTokens.closeAndRemove();
    }
    
    public boolean close() throws DBException {
    	config.setProperty(getConfigKeyForFile(), null);    	
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

        private class QNameTerm implements Comparable {

            QName qname;
            String term;

            public QNameTerm(QName qname, String term) {
                this.qname = qname;
                this.term = term;
            }

            public int compareTo(Object o) {
                QNameTerm other = (QNameTerm) o;
                int cmp = qname.compareTo(other.qname);
                if (cmp == 0)
                    return term.compareTo(other.term);
                else
                    return cmp;
            }
        }

        private DocumentImpl doc = null;
        // To distinguish between attribute values and text, we use
        // two maps: words[0] collects text, words[1] stores attribute
        // values.        
        //TODO : very tricky. Why not 2 inverted indexes ??? -pb
		private Map words[] = new Map[3];
		private int TEXT_NODES = 0;
		private int ATTRIBUTE_NODES = 1;
        private int BY_QNAME = 2;

        public InvertedIndex() {
			words[TEXT_NODES] = new HashMap(512);
			words[ATTRIBUTE_NODES] = new HashMap(256);
			//seems to be linked with QName indexes
            words[BY_QNAME] = new TreeMap();
        }
        
        public void setDocument(DocumentImpl document) {
            if (this.doc != null && this.doc.getDocId() != document.getDocId())
                flush();
            this.doc = document;
        }        

		public void addText(TextToken token, NodeId nodeId, boolean remove) {
            if (!remove) {
                //Is this token already pending ?
                OccurrenceList list = (OccurrenceList) words[TEXT_NODES].get(token);
                //Create a GIDs list
                if (list == null) {
                    list = new OccurrenceList();
                    list.add(nodeId, token.startOffset());
                    words[TEXT_NODES].put(token.getText(), list);
                } else {
                    //Add node's GID to the list
                    list.add(nodeId, token.startOffset());
                }
            } else {
                if (!words[TEXT_NODES].containsKey(token))
                    words[TEXT_NODES].put(token, null);
            }
        }

        public void addText(TextToken token, ElementImpl ancestor, boolean remove) {
            QNameTerm term = new QNameTerm(ancestor.getQName(), token.getText());
            if (!remove) {
                //Is this token already pending ?
                OccurrenceList list = (OccurrenceList) words[BY_QNAME].get(term);
                //Create a GIDs list
                if (list == null) {
                    list = new OccurrenceList();
                    list.add(ancestor.getNodeId(), token.startOffset());
                    words[BY_QNAME].put(term, list);
                } else {
                    //Add node's GID to the list
                    list.add(ancestor.getNodeId(), token.startOffset());
                }
            } else {
                if (!words[BY_QNAME].containsKey(term))
                    words[BY_QNAME].put(term, null);
            }
        }

        //TODO : unify functionalities with addText -pb
		public void addAttribute(TextToken token, NodeId nodeId, boolean remove) {
            //Is this token already pending ?
            if (!remove) {
                OccurrenceList list = (OccurrenceList) words[ATTRIBUTE_NODES].get(token);
                //Create a GIDs list
                if (list == null) {
                    list = new OccurrenceList();
                    list.add(nodeId, token.startOffset());
                    words[ATTRIBUTE_NODES].put(token.getText(), list);
                } else {
                    //Add node's GID to the list
                    list.add(nodeId, token.startOffset());
                }
            } else {
                if (!words[ATTRIBUTE_NODES].containsKey(token))
                    words[ATTRIBUTE_NODES].put(token, null);
            }
        }

        public void addAttribute(TextToken token, AttrImpl attr, boolean remove) {
            QNameTerm term = new QNameTerm(attr.getQName(), token.getText());
            if (!remove) {
                //Is this token already pending ?
                OccurrenceList list = (OccurrenceList) words[BY_QNAME].get(term);
                //Create a GIDs list
                if (list == null) {
                    list = new OccurrenceList();
                    list.add(attr.getNodeId(), token.startOffset());
                    words[BY_QNAME].put(term, list);
                } else {
                    //Add node's GID to the list
                    list.add(attr.getNodeId(), token.startOffset());
                }
            } else {
                if (!words[BY_QNAME].containsKey(term))
                    words[BY_QNAME].put(term, null);
            }
        }

        public void flush() {
            //return early
            if (this.doc == null)
                return;            
            final int wordsCount = words[TEXT_NODES].size() + words[ATTRIBUTE_NODES].size() + words[BY_QNAME].size();
            if (wordsCount == 0)
                return;
            final ProgressIndicator progress = new ProgressIndicator(wordsCount, 100);
            final int collectionId = this.doc.getCollection().getId();
            int count = 0;
            for (byte currentSection = 0; currentSection <= QNAME_SECTION; currentSection++) {
            	//Not very necessary, but anyway...
                switch (currentSection) {
	                case TEXT_SECTION :
	                case ATTRIBUTE_SECTION :
                    case QNAME_SECTION :
                        break;
	                default :
	                    throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + 
	                    "' (inverted index)");
	            }                    
                for (Iterator i = words[currentSection].entrySet().iterator(); i.hasNext(); count++) {
                    Map.Entry entry = (Map.Entry) i.next();
                    Object token = entry.getKey();
                    OccurrenceList occurences = (OccurrenceList) entry.getValue();
                    if (occurences == null)
                        continue; // may happen if the index is in an invalid state due to earlier errors
                    //Don't forget this one
                    occurences.sort();
                    os.clear();
                    os.writeInt(this.doc.getDocId());
                    os.writeByte(currentSection);
//                    os.writeByte(currentSection == QNAME_SECTION ? TEXT_SECTION : currentSection);
                    os.writeInt(occurences.getTermCount());
                    //Mark position
                    int lenOffset = os.position();
                    //Dummy value : actual one will be written below
                    os.writeFixedInt(0);
                    NodeId previous = null;
                    for (int m = 0; m < occurences.getSize(); ) {
                        try {
                            previous = occurences.getNode(m).write(previous, os);
//                            occurences.nodes[m].write(os);
                        } catch (IOException e) {
                            LOG.error("IOException while writing fulltext index: " + e.getMessage(), e);
                        }
                        int freq = occurences.getOccurrences(m);
                        os.writeInt(freq);
                        for (int n = 0; n < freq; n++) {
                            os.writeInt(occurences.getOffset(m + n));
                        }
                        m += freq;                        
                    }
                    //Write (variable) length of node IDs + frequency + offsets 
                    os.writeFixedInt(lenOffset, os.position() - lenOffset - LENGTH_NODE_IDS_FREQ_OFFSETS);
                    flushWord(currentSection, collectionId, token, os.data());
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

        private void flushWord(int currentSection, int collectionId, Object token, ByteArray data) {
            //return early
            //TODO : is this ever called ? -pb
            if (data.size() == 0)
                return;
            final Lock lock = dbTokens.getLock();
            try {
                lock.acquire(Lock.WRITE_LOCK); 
                Value key;
                if (currentSection == QNAME_SECTION) {
                    QNameTerm term = (QNameTerm) token;
                    key = new QNameWordRef(collectionId, term.qname, term.term, broker.getBrokerPool().getSymbols());
                } else {
                    key = new WordRef(collectionId, token.toString());
                }
                dbTokens.append(key, data);
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "' (inverted index)", e);
            } catch (ReadOnlyException e) {
                LOG.warn("Read-only error on '" + dbTokens.getFile().getName() + "' (inverted index)", e);
            } catch (IOException e) {
                LOG.error(e.getMessage() + "' in '" + dbTokens.getFile().getName() + "' (inverted index)", e);   
            } finally {
                lock.release(Lock.WRITE_LOCK);
                os.clear();
            }
        }

        public void dropIndex(DocumentImpl document) {
            //Return early
            if (document == null)
                return;
            final int collectionId = document.getCollection().getId();
            final Lock lock = dbTokens.getLock();
            for (byte currentSection = 0; currentSection <= QNAME_SECTION; currentSection++) {
                //Not very necessary, but anyway...
                switch (currentSection) {
                    case TEXT_SECTION :
                    case ATTRIBUTE_SECTION :
                    case QNAME_SECTION :
                        break;
                    default :
                        throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() +
                                "' (inverted index)");
                }
                LOG.debug("Removing " + words[currentSection].size() + " tokens");
                for (Iterator i = words[currentSection].entrySet().iterator(); i.hasNext();) {
                    //Compute a key for the token
                    Map.Entry entry = (Map.Entry) i.next();
                    Object token = entry.getKey();
                    Value key;
                    if (currentSection == QNAME_SECTION) {
                        QNameTerm term = (QNameTerm) token;
                        key = new QNameWordRef(collectionId, term.qname, term.term, broker.getBrokerPool().getSymbols());
                    } else {
                        key = new WordRef(collectionId, token.toString());
                    }
                    os.clear();
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
                            //Read (variable) length of node IDs + frequency + offsets
                            int length = is.readFixedInt();
                            if (storedDocId != document.getDocId()) {
                                // data are related to another document:
                                // copy them to any existing data
                                os.writeInt(storedDocId);
                                os.writeByte(section);
                                os.writeInt(gidsCount);
                                os.writeFixedInt(length);
                                is.copyRaw(os, length);
                            } else {
                                // data are related to our document:
                                // skip them
                                changed = true;
                                is.skipBytes(length);
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
                                    //TODO : throw an exception ?
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
                        lock.release(Lock.WRITE_LOCK);
                        os.clear();
                    }
                }
                words[currentSection].clear();
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
            final int collectionId = this.doc.getCollection().getId();
            final Lock lock = dbTokens.getLock();
            for (byte currentSection = 0; currentSection <= QNAME_SECTION; currentSection++) {
            	//Not very necessary, but anyway...
                switch (currentSection) {
	                case TEXT_SECTION :
	                case ATTRIBUTE_SECTION :
                    case QNAME_SECTION :
                        break;
	                default :
	                    throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + 
	                            "' (inverted index)");
	            }
                for (Iterator i = words[currentSection].entrySet().iterator(); i.hasNext();) {
                    //Compute a key for the token
                    Map.Entry entry = (Map.Entry) i.next();
                    OccurrenceList storedOccurencesList = (OccurrenceList) entry.getValue();
                    Object token = entry.getKey();
                    Value key;
                    if (currentSection == QNAME_SECTION) {
                        QNameTerm term = (QNameTerm) token;
                        key = new QNameWordRef(collectionId, term.qname, term.term,
                                broker.getBrokerPool().getSymbols());
                    } else {
                        key = new WordRef(collectionId, token.toString());
                    }
                    OccurrenceList newOccurencesList = new OccurrenceList();
                    os.clear();
                    try {
                        lock.acquire(Lock.WRITE_LOCK);
                        Value value = dbTokens.get(key);
                        if (value == null)
                            continue;
                        //Add its data to the new list
                        VariableByteArrayInput is = new VariableByteArrayInput(value.getData());
                        while (is.available() > 0) {
                            int storedDocId = is.readInt();
                            byte storedSection = is.readByte();
                            int termCount = is.readInt();
                            //Read (variable) length of node IDs + frequency + offsets
                            int length = is.readFixedInt();
                            if (storedSection != currentSection || storedDocId != this.doc.getDocId()) {
                                // data are related to another section or document:
                                // append them to any existing data
                                os.writeInt(storedDocId);
                                os.writeByte(storedSection);
                                os.writeInt(termCount);
                                os.writeFixedInt(length);
                                is.copyRaw(os, length);
                            } else {
                                // data are related to our section and document:
                                // feed the new list with the GIDs
                                NodeId previous = null;
                                for (int m = 0; m < termCount; m++) {
                                    NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                                    previous = nodeId;
                                    int freq = is.readInt();
                                    // add the node to the new list if it is not
                                    // in the list of removed nodes
                                    if (!storedOccurencesList.contains(nodeId)) {
                                        for (int n = 0; n < freq; n++) {
                                            newOccurencesList.add(nodeId, is.readInt());
                                        }
                                    } else {
                                        is.skip(freq);
                                    }
                                }
                            }
                        }
                        //append the data from the new list
                        if(newOccurencesList.getSize() > 0) {
                            //Don't forget this one
                            newOccurencesList.sort();
                            os.writeInt(this.doc.getDocId());
                            os.writeByte(currentSection);
                            os.writeInt(newOccurencesList.getTermCount());
                            //Mark position
                            int lenOffset = os.position();
                            //Dummy value : actual one will be written below
                            os.writeFixedInt(0);
                            NodeId previous = null;
                            for (int m = 0; m < newOccurencesList.getSize(); ) {
                                previous = newOccurencesList.getNode(m).write(previous, os);
                                int freq = newOccurencesList.getOccurrences(m);
                                os.writeInt(freq);
                                for (int n = 0; n < freq; n++) {
                                    os.writeInt(newOccurencesList.getOffset(m + n));
                                }
                                m += freq;
                            }
                            //Write (variable) length of node IDs + frequency + offsets
                            os.writeFixedInt(lenOffset, os.position() - lenOffset - LENGTH_NODE_IDS_FREQ_OFFSETS);
                        }
                        //Store the data
                        if(os.data().size() == 0)
                            dbTokens.remove(key);
                        else if (dbTokens.update(value.getAddress(), key, os.data()) == BFile.UNKNOWN_ADDRESS) {
                            LOG.error("Could not update index data for token '" +  token + "' in '" + dbTokens.getFile().getName() +
                                    "' (inverted index)");
                            //TODO : throw an exception ?
                        }
					} catch (LockException e) {
                        LOG.warn("Failed to acquire lock for '" + dbTokens.getFile().getName() + "' (inverted index)", e);
                    } catch (ReadOnlyException e) {
                        LOG.warn("Read-only error on '" + dbTokens.getFile().getName() + "' (inverted index)", e);
                    } catch (IOException e) {
                        LOG.error(e.getMessage() + "' in '" + dbTokens.getFile().getName() + "' (inverted index)", e);
                    } finally {
					    lock.release(Lock.WRITE_LOCK);
                        os.clear();
                    }
				}
				words[currentSection].clear();
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
                final String word = new String(key.getData(), Collection.LENGTH_COLLECTION_ID, 
                		key.getLength() - Collection.LENGTH_COLLECTION_ID, "UTF-8");
                if (matcher.matches(word))
                    matches.add(word);
                return true;
			} catch (UnsupportedEncodingException e) {
                LOG.error(e.getMessage(), e);
                return true;
			} 
		}
	}
	
	private final class SearchCallback implements BTreeCallback {

		DocumentSet docs;
		TermMatcher matcher;
		NodeSet result;
		NodeSet contextSet;
        int axis;
        XQueryContext context;
		XMLString word = new XMLString(64);
        QName qname;

        public SearchCallback(XQueryContext context, TermMatcher comparator, NodeSet result,
				NodeSet contextSet, int axis, DocumentSet docs, QName qname) {
			this.matcher = comparator;
			this.result = result;
			this.docs = docs;
			this.contextSet = contextSet;
			this.context = context;
            this.qname = qname;
            this.axis = axis;
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
            if (qname == null)
            	WordRef.decode(key, word);
            else
            	QNameWordRef.decode(key, word);
            if (matcher.matches(word)) {
				try {
					while (is.available() > 0) {                        
					    if(context != null)
					        context.proceed();                        
                        int storedDocId = is.readInt();
                        byte storedSection = is.readByte();
                        int termCount = is.readInt();
                        //Read (variable) length of node IDs + frequency + offsets
                        int length = is.readFixedInt();
                        DocumentImpl storedDocument = docs.getDoc(storedDocId);                        
                        //Exit if the document is not concerned
						if (storedDocument == null) {
							is.skipBytes(length);
							continue;
						}
                        NodeId previous = null;
                        for (int m = 0; m < termCount; m++) {
                            NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                            previous = nodeId;
                            int freq = is.readInt();
                            NodeProxy storedNode;
                            switch (storedSection) {
                                case TEXT_SECTION :
                                    storedNode = new NodeProxy(storedDocument, nodeId, Node.TEXT_NODE);
                                    break;
                                case ATTRIBUTE_SECTION :
                                    storedNode = new NodeProxy(storedDocument, nodeId, Node.ATTRIBUTE_NODE);
                                    break;
                                case QNAME_SECTION :
                                    storedNode = new NodeProxy(storedDocument, nodeId,
                                        qname.getNameType() == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE);
                                    break;
                                default :
                                    throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + "'");
                            }
							if (contextSet != null) {
                                NodeProxy parentNode;  
                                switch (storedSection) {
                                    case TEXT_SECTION :
                                    case QNAME_SECTION:
                                        parentNode = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                        break;
                                    case ATTRIBUTE_SECTION :
                                        if (contextSet instanceof VirtualNodeSet) {
                                        parentNode = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                        if (parentNode != null && parentNode.getNodeId().equals(nodeId))
                                            parentNode = null;
                                        } else {
                                            parentNode = contextSet.get(storedNode);
                                        }
                                        break;
                                    default :
                                        throw new IllegalArgumentException("Invalid section type in '" + dbTokens.getFile().getName() + "'");
                                }
								if (parentNode != null) {
                                    Match match = new FTMatch(-1, nodeId, word.toString(), freq);
                                    readOccurrences(freq, is, match, word.length());
                                    int sizeHint = contextSet.getSizeHint(storedDocument);
                                    if (axis == NodeSet.ANCESTOR) {
                                        parentNode.addMatch(match);
                                        result.add(parentNode, sizeHint);
                                    } else {
                                        storedNode.addMatch(match);
                                        result.add(storedNode, sizeHint);
                                    }
                                } else
                                    is.skip(freq);
							} else {
                                Match match = new FTMatch(-1, nodeId, word.toString(), freq);
							    readOccurrences(freq, is, match, word.length());
                                storedNode.addMatch(match);
							    result.add(storedNode, Constants.NO_SIZE_HINT);
                            }
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
				((NewArrayNodeSet) result).sort();
            
			return true;
		}
	}
	
	private final class IndexScanCallback implements BTreeCallback{
		
		private DocumentSet docs;
		private NodeSet contextSet;
		private Map map = new TreeMap();
        private XMLString word = new XMLString(64);
		private boolean byQName;

        IndexScanCallback(DocumentSet docs, NodeSet contextSet, boolean byQName) {
			this.docs = docs;
			this.contextSet = contextSet;
            this.byQName = byQName;
        }
		
		/* (non-Javadoc)
		 * @see org.dbxml.core.filer.BTreeCallback#indexInfo(org.dbxml.core.data.Value, long)
		 */
		public boolean indexInfo(Value key, long pointer) throws TerminatedException {
            word.reuse();
            if (byQName)
                QNameWordRef.decode(key, word);
            else
                WordRef.decode(key, word);
            final String term = word.toString();
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
                    //Read (variable) length of node IDs + frequency + offsets
                    int length = is.readFixedInt();
                    DocumentImpl storedDocument = docs.getDoc(storedDocId);
                    //Exit if the document is not concerned
					if (storedDocument == null) {
						is.skipBytes(length);
						continue;
					}
                    NodeId previous = null;
                    for (int m = 0; m < termCount; m++) {
                        NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;
                        int freq = is.readInt();
                        is.skip(freq);
						if (contextSet != null) {
                            boolean include = false;
                            NodeProxy parentNode = contextSet.parentWithChild(storedDocument, nodeId, false, true);
                            switch (storedSection) {
                                case TEXT_SECTION :
                                case QNAME_SECTION :
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
		
		public static int LENGTH_IDX_TYPE = 1; //sizeof byte
		
		public static int OFFSET_IDX_TYPE = 0;		
		public static int OFFSET_COLLECTION_ID = OFFSET_IDX_TYPE + WordRef.LENGTH_IDX_TYPE; //1
		public static int OFFSET_WORD = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //3
		
		public WordRef(int collectionId) {
			len = WordRef.LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID;
			data = new byte[len];
            data[OFFSET_IDX_TYPE] = IDX_GENERIC;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
		}

		public WordRef(int collectionId, String word) {
			len = WordRef.LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID + UTF8.encoded(word);
			data = new byte[len];
            data[OFFSET_IDX_TYPE] = IDX_GENERIC;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
			UTF8.encode(word, data, OFFSET_WORD);
		}
		
        public static XMLString decode(Value key, XMLString word) {
        	int prefixLength = WordRef.LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID;
        	return UTF8.decode(key.getData(), prefixLength, key.getLength() - prefixLength, word);
        }

		public String toString() {
			if (len > OFFSET_WORD)
				return new String(data, OFFSET_WORD, len - OFFSET_WORD);
			else return "no word";
		}
	}

	//TODO : extend WordRef ?
    private final static class QNameWordRef extends Value {

		public static int LENGTH_IDX_TYPE = 1; //sizeof byte
		public static int LENGTH_QNAME_TYPE = 1; //sizeof byte
		
    	public static int OFFSET_IDX_TYPE = 0;
		public static int OFFSET_COLLECTION_ID = OFFSET_IDX_TYPE + QNameWordRef.LENGTH_IDX_TYPE; //1
		public static int OFFSET_QNAME_TYPE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //4
		public static int OFFSET_NS_URI = OFFSET_QNAME_TYPE + LENGTH_QNAME_TYPE; //4
		public static int OFFSET_LOCAL_NAME = OFFSET_NS_URI + SymbolTable.LENGTH_NS_URI; //6
		public static int OFFSET_WORD = OFFSET_LOCAL_NAME + SymbolTable.LENGTH_LOCAL_NAME; //8
		
		public QNameWordRef(int collectionId) {
			len = QNameWordRef.LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID;
			data = new byte[len];
            data[OFFSET_IDX_TYPE] = IDX_QNAME;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
            pos = OFFSET_IDX_TYPE;
        }
		
        public QNameWordRef(int collectionId, QName qname, SymbolTable symbols) {
			len = QNameWordRef.LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID +  
				QNameWordRef.LENGTH_QNAME_TYPE + SymbolTable.LENGTH_NS_URI + SymbolTable.LENGTH_LOCAL_NAME;
			data = new byte[len];
			final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
			final short localNameId = symbols.getSymbol(qname.getLocalName());
			data[OFFSET_IDX_TYPE] = IDX_QNAME;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
			data[OFFSET_QNAME_TYPE] = qname.getNameType();
            ByteConversion.shortToByte(namespaceId, data, OFFSET_NS_URI);
			ByteConversion.shortToByte(localNameId, data, OFFSET_LOCAL_NAME);            
        }

        public QNameWordRef(int collectionId, QName qname, String word, SymbolTable symbols) {
			len = UTF8.encoded(word) + QNameWordRef.LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID +  
			LENGTH_QNAME_TYPE + SymbolTable.LENGTH_NS_URI + SymbolTable.LENGTH_LOCAL_NAME;
			data = new byte[len];
			final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
			final short localNameId = symbols.getSymbol(qname.getLocalName());
            data[OFFSET_IDX_TYPE] = IDX_QNAME;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
			data[OFFSET_QNAME_TYPE] = qname.getNameType();
            ByteConversion.shortToByte(namespaceId, data, OFFSET_NS_URI);
			ByteConversion.shortToByte(localNameId, data, OFFSET_LOCAL_NAME);            
			UTF8.encode(word, data, OFFSET_WORD);
        }
        
        public static XMLString decode(Value key, XMLString word) {
        	int prefixLength = QNameWordRef.LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID +  
        		QNameWordRef.LENGTH_QNAME_TYPE + SymbolTable.LENGTH_NS_URI + SymbolTable.LENGTH_LOCAL_NAME;
        	return UTF8.decode(key.getData(), prefixLength, key.getLength() - prefixLength, word);
        }

        public String toString() {
			if (len > OFFSET_WORD)
				return new String(data, OFFSET_WORD, len - OFFSET_WORD);
			else return "no word";
		}
	}
}
