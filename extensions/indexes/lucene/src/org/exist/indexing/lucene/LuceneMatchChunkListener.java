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
package org.exist.indexing.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.PhraseQuery;
import org.exist.Namespaces;
import org.exist.dom.*;
import org.exist.indexing.AbstractMatchListener;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.util.serializer.AttrList;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.Map.Entry;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.util.AttributeSource.State;

public class LuceneMatchChunkListener extends AbstractMatchListener {

    private static final Logger LOG = Logger.getLogger(LuceneMatchChunkListener.class);
    
    public static final byte DO_NOT_CHUNK = 1;
    public static final byte CHUNK = 1;
    public static final byte CHUNK_TILL_WS = 2;
    public static final byte DO_NOT_CHUNK_NODE = 3;
    
    private byte mode = CHUNK;

    private Match match;

    private Map<Object, Query> termMap;

    private Map<NodeId, Offset> nodesWithMatch;

    private LuceneIndex index;

    private LuceneConfig config;

    private DBBroker broker;
    
    OffsetList offsets = null;
    
    public final static QName CUTOFF_ELEMENT = new QName("cutoff", Namespaces.EXIST_NS, "exist");
    
    private int chunkOffset = 0;
    
    private boolean cutted = false;

    public LuceneMatchChunkListener(LuceneIndex index, int chunkOffset, byte mode) {
        this.index = index;
        this.chunkOffset = chunkOffset;
        this.mode = mode;
    }

    public boolean hasMatches(NodeProxy proxy) {
        Match nextMatch = proxy.getMatches();
        while (nextMatch != null) {
            if (nextMatch.getIndexId() == LuceneIndex.ID) {
                return true;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        return false;
    }
    
    public void setChunkOffset(int value) {
    	chunkOffset = value;
    }

    public void reset(DBBroker broker, NodeProxy proxy, LuceneConfig config) throws SAXException {
        this.broker = broker;
        
        this.match = proxy.getMatches();
        
        //DocumentImpl doc = proxy.getDocument();
        
        //this.match = proxy.getMatches();
        setNextInChain(null);

        this.config = config;

        getTerms();
        nodesWithMatch = new TreeMap<NodeId, Offset>();
        
        scanMatches( proxy, getPath(proxy) );
        
        firstElement = null;
        cutted = false;
    }

    public void reset(DBBroker broker, NodeProxy proxy) throws SAXException {

        IndexSpec indexConf = proxy.getDocument().getCollection().getIndexConfiguration(broker);
        if (indexConf == null)
        	throw new SAXException("no Lucene config");
        
        config = (LuceneConfig) indexConf.getCustomIndexSpec(LuceneIndex.ID);
        
        reset(broker, proxy, config);
    }
    
    StoredNode firstElement = null;
    
    Stack<NodeId> stack = new Stack<NodeId>();

    @Override
    public void startElement(QName qname, AttrList attribs) throws SAXException {
//        Match nextMatch = match;
//        // check if there are any matches in the current element
//        // if yes, push a NodeOffset object to the stack to track
//        // the node contents
//        while (nextMatch != null) {
//            if (nextMatch.getNodeId().equals(getCurrentNode().getNodeId())) {
//                scanMatches(new NodeProxy(getCurrentNode()));
//                break;
//            }
//            nextMatch = nextMatch.getNextMatch();
//        }
    	
    	NodeId nodeId = getCurrentNode().getNodeId();
//    	System.out.println("\nnode = "+nodeId);
    	
    	for (NodeId nId : nodesWithMatch.keySet()) {
    		int relation = nId.computeRelation(nodeId);
//    		System.out.println("match: "+nId + " " + relation);

    		if (relation > 0) {
    			stack.push(nodeId);
    			
    			addNS();
    			super.startElement(qname, attribs);
    			cutted = false;
    			
    			return;
    		}
    	}
    	
//    	System.out.println("others");
    	for (int i = 0; i < offsets.len; i++) {
    		NodeId nId = offsets.ids[i];
    		
    		int relation = nId.computeRelation(nodeId);
//    		System.out.println("offsets: "+nId + " " + relation);
    		
    		if (relation > 0) {
    			
    			stack.push(nodeId);
    			
    			addNS();
    			super.startElement(qname, attribs);
    			cutted = false;
    			
    			return;
    		}
    	}
    	
    	cutNode();
    	
        //super.startElement(qname, attribs);
    }

    private void addNS() throws SAXException {
    	if (firstElement == null) {
    		//attribs.addAttribute(new QName("score", Namespaces.EXIST_NS, "exist"), "");
    		
    		super.startPrefixMapping("exist", Namespaces.EXIST_NS);
    		
    		firstElement = getCurrentNode();
    	}
    }
    
    @Override
    public void endElement(QName qname) throws SAXException {
    	if (firstElement == getCurrentNode()) {
    		super.endPrefixMapping("exist");
    		firstElement = null;
    	}

		NodeId nodeId = getCurrentNode().getNodeId();
    	
    	if (!stack.isEmpty() && stack.peek().equals(nodeId)) {
    		stack.pop();

    		super.endElement(qname);
    		cutted = false;
    	}
    }

    @Override
    public void characters(CharSequence seq) throws SAXException {
    	if (mode == CHUNK || mode == CHUNK_TILL_WS) {
    		charactersChunking(seq);
    	} else if (mode == DO_NOT_CHUNK_NODE) {
    		charactersDoNotChunkNode(seq);
    	}
    }

    public void charactersDoNotChunkNode(CharSequence seq) throws SAXException {
        NodeId nodeId = getCurrentNode().getNodeId();
        Offset offset = nodesWithMatch.get(nodeId);
        if (offset == null) {
        	
            int index = offsets.getIndex(nodeId);
            if (index == -1) {
	        	//System.out.println("empty text on "+nodeId);
	            return; //super.characters(seq);
            }
        }

        String s = seq.toString();
        int pos = 0;
        while (offset != null) {
            if (offset.startOffset > pos) {
                if (offset.startOffset > seq.length())
                    throw new SAXException("start offset out of bounds");
                super.characters(s.substring(pos, offset.startOffset));
            }
            int end = offset.endOffset;
            if (end > s.length())
                end = s.length();
            super.startElement(MATCH_ELEMENT, null);
            super.characters(s.substring(offset.startOffset, end));
            super.endElement(MATCH_ELEMENT);
            pos = end;
            offset = offset.next;
        }
        if (pos < seq.length())
            super.characters(s.substring(pos));
    }
    
    public void charactersChunking(CharSequence seq) throws SAXException {
        NodeId nodeId = getCurrentNode().getNodeId();
        
        Offset offset = nodesWithMatch.get(nodeId);

        int index = offsets.getIndex(nodeId);
        
//        boolean wasMatch = false;
        
        boolean outStart = true;
        boolean outEnd = true;
        
        if (index != -1) { 
	        for (int i = index; i < offsets.len; i++) {
	        	if (!offsets.ids[i].equals(nodeId))
	        		break;
	        	
	        	int s; int e;
	        	if (offsets.starts[i] == offsets.offsets[i] || outStart) {
	        		s = offsets.starts[i] - offsets.offsets[i];
	        		e = 0;
	        		
	        	} else {
	        		s = offsets.starts[i] - offsets.offsets[i] - 1;
	        		e = 0;
	        	}
	        	
	        	e += offsets.ends[i] - offsets.offsets[i];// + 1;
	        	if (e > seq.length())
	        		e = seq.length();
	
	        	while (offset != null) {
	        		if (e <= offset.startOffset) {
	        			break;
	        		}
	        		
	                int end = offset.endOffset;
	                if (end > seq.length())
	                    end = seq.length();
	
	                super.startElement(MATCH_ELEMENT, null);
	                super.characters(seq.subSequence(offset.startOffset, end));
	                super.endElement(MATCH_ELEMENT);
//	                wasMatch = true;
	                cutted = false;
	                
	                outStart = false;
	
	                offset = offset.next;
	        	}
	        	
	        	if (outStart && s != 0)
	        		cut();

	        	if (i == offsets.len - 1) {
	        		;
	        	} else {
	        		e++;
		        	if (e > seq.length())
		        		e = seq.length();
	        	}

	        	super.characters(aroundWS(seq.subSequence(s, e), outStart));
	        	
	        	cutted = false;

        		outStart = false;
	        	
	        	if (e == seq.length())
	        		outEnd = false;
	        }
        }

    	while (offset != null) {
            int end = offset.endOffset;
            if (end > seq.length())
                end = seq.length();

            super.startElement(MATCH_ELEMENT, null);
            super.characters(seq.subSequence(offset.startOffset, end));
            super.endElement(MATCH_ELEMENT);
//            wasMatch = true;
            cutted = false;
            
            if (end == seq.length())
        		outEnd = false;

            offset = offset.next;
    	}
    	
    	if (outEnd)
//    	if (wasMatch && outEnd)
    		cut();
    }
    
    private CharSequence aroundWS(CharSequence chars, boolean left) {
    	
    	if (mode == CHUNK_TILL_WS) {
	    	if (left) {
	    		for (int i = 0; i < chars.length(); i++) {
	    			char ch = chars.charAt(i);
	    			if (ch == ' ' || ch == '\t') {
	    				return chars.subSequence(i, chars.length());
	    			}
	    		}
	    		return chars;
	    	} else {
	    		for (int i = chars.length() - 1; i >= 0 ; i--) {
	    			
	    			char ch = chars.charAt(i);
	    			
	    			if (ch == ' ' || ch == '\t') {
	    				return chars.subSequence(0, i + 1);
	    			}
	    		}
	    		return chars;
	    	}
    	} else {
    		return chars;
    	}
	}
    
    private void cut() throws SAXException {
    	if (!cutted) {
	        super.startElement(CUTOFF_ELEMENT, null);
	        super.endElement(CUTOFF_ELEMENT);
	        cutted = true;
    	}
    }

    private void cutNode() throws SAXException {
    	if (mode == DO_NOT_CHUNK_NODE && !cutted) {
	        super.startElement(CUTOFF_ELEMENT, null);
	        super.endElement(CUTOFF_ELEMENT);
	        cutted = true;
    	}
    }

    private NodeId nodeId(EmbeddedXMLStreamReader reader) {
    	return (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
    }

    // Collect the text content of all descendants of p. 
	// Remember the start offsets of the text nodes for later use.
    private void scanMatches(NodeProxy p, NodePath path) throws SAXException {
    	if (config == null)
    		throw new SAXException("no lucene configuration");
    	
    	Iterator<LuceneConfigText> confIter = config.getConfig(path);
    	if (confIter == null)
    		throw new SAXException("no configuration for path = '"+path+"'");
    	
		LuceneConfigText idxConf = confIter.next();
        
        TextExtractor extractor = new DefaultTextExtractor();
        extractor.configure(config, idxConf, 0);
        
        offsets = new OffsetList();
        
        int level = 0;
        int textOffset = 0;
        try {
            EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(p, false);
            while (reader.hasNext()) {
                int ev = reader.next();
                switch (ev) {
				case XMLStreamConstants.END_ELEMENT:
				    if (--level < 0)
				    	break;
				    textOffset += extractor.endElement(reader.getQName());
				    
				    //offsets.addEnd(textOffset, nodeId(reader));
				    
				    break;
				case XMLStreamConstants.START_ELEMENT:
				    ++level;
				    textOffset += extractor.startElement(reader.getQName());
				    break;
				case XMLStreamConstants.CHARACTERS:
				    int start = textOffset += extractor.beforeCharacters();
				    
				    textOffset += extractor.characters(reader.getXMLText());

				    offsets.add(start, textOffset, nodeId(reader));
				    
				    break;
                }
            }
        } catch (IOException e) {
            LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
        } catch (XMLStreamException e) {
            LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
        }
        
        // Retrieve the Analyzer for the NodeProxy that was used for
        // indexing and querying.
        Analyzer analyzer = null;
        if (idxConf != null) {
	        analyzer = idxConf.getAnalyzer();
        }
        if (analyzer == null) {
		    // Otherwise use system default Lucene analyzer (from conf.xml)
		    // to tokenize the text and find matching query terms.
		    analyzer = index.getDefaultAnalyzer();
        }

        if (LOG.isDebugEnabled())
        	LOG.debug("Analyzer: " + analyzer + " for path: " + path);
        
        String str = extractor.getText().toString();
        
        //Token token;
        try {

            TokenStream tokenStream = analyzer.tokenStream(null, new StringReader(str));
            tokenStream.reset();
            MarkableTokenFilter stream = new MarkableTokenFilter(tokenStream);
            while (stream.incrementToken()) {
                String text = stream.getAttribute(CharTermAttribute.class).toString();
                Query query = termMap.get(text);
                if (query != null) {
                    // Phrase queries need to be handled differently to filter
                    // out wrong matches: only the phrase should be marked, not
                	// single words which may also occur elsewhere in the document
                    if (query instanceof PhraseQuery) {
                        PhraseQuery phraseQuery = (PhraseQuery) query;
                        Term[] terms = phraseQuery.getTerms();
                        if (text.equals(terms[0].text())) {
                            // Scan the following text and collect tokens to see
                        	// if they are part of the phrase.
                            stream.mark();
                            int t = 1;
                            List<State> stateList = new ArrayList<State>(terms.length);
                            stateList.add(stream.captureState());
                            
                            while (stream.incrementToken() && t < terms.length) {
                                text = stream.getAttribute(CharTermAttribute.class).toString();
                                if (text.equals(terms[t].text())) {
                                    stateList.add(stream.captureState());
                                    if (++t == terms.length) {
                                        break;
                                    }
                                } else {
                                	// Don't reset the token stream since we will 
                                	// miss matches. /ljo
                                    //stream.reset();
                                    break;
                                }
                            }
                            
                            if (stateList.size() == terms.length) {
                                // we indeed have a phrase match. record the offsets of its terms.
                                int lastIdx = -1;
                                for (int i = 0; i < terms.length; i++) {
                                    stream.restoreState(stateList.get(i));
                                    
                                    OffsetAttribute offsetAttr = stream.getAttribute(OffsetAttribute.class);
                                    
                                    offsets.exclude(offsetAttr.startOffset(), offsetAttr.endOffset());
                                    
                                    int idx = offsets.getLastIndex(offsetAttr.startOffset());
                                    
                                    NodeId nodeId = offsets.ids[idx];
                                    Offset offset = nodesWithMatch.get(nodeId);
                                    if (offset != null)
                                        if (lastIdx == idx)
                                            offset.setEndOffset(offsetAttr.endOffset() - offsets.offsets[idx]);
                                        else
                                            offset.add(
                                        		offsetAttr.startOffset(),
                                        		offsetAttr.endOffset(),
                                        		offsetAttr.startOffset() - offsets.offsets[idx],
                                        		offsetAttr.endOffset() - offsets.offsets[idx]
                            				);
                                    else
                                        nodesWithMatch.put(
                                    		nodeId, 
                                    		new Offset(
                                				offsetAttr.startOffset(),
                                				offsetAttr.endOffset(),
                                				offsetAttr.startOffset() - offsets.offsets[idx],
                                				offsetAttr.endOffset() - offsets.offsets[idx]
                    						)
                                		);
                                    lastIdx = idx;
                                }
                            }
                        } // End of phrase handling
                    } else {
                        
                        OffsetAttribute offsetAttr = stream.getAttribute(OffsetAttribute.class);
                        
                        offsets.exclude(offsetAttr.startOffset(), offsetAttr.endOffset());
                        
                        int idx = offsets.getLastIndex(offsetAttr.startOffset());
                        NodeId nodeId = offsets.ids[idx];
                        
                        int startOffset = offsetAttr.startOffset() - offsets.offsets[idx];
                        int endOffset = offsetAttr.endOffset() - offsets.offsets[idx];
                        
                        Offset offset = nodesWithMatch.get(nodeId);
                        if (offset != null)
                            offset.add(
                        		offsetAttr.startOffset(),
                        		offsetAttr.endOffset(),
                        		startOffset,
                        		endOffset
            				);
                        else {
                            nodesWithMatch.put(
                        		nodeId, 
                        		new Offset(
                    				offsetAttr.startOffset(),
                    				offsetAttr.endOffset(),
                    				startOffset,
                    				endOffset
        						)
                    		);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
        }
        
        if (LuceneIndex.DEBUG) {
		    System.out.println("Debug");
		    
		    for (int i = 0; i < offsets.len; i++) {
		    	System.out.println("" + offsets.ids[i] + " " + offsets.offsets[i] + " [" + offsets.starts[i] + " : " + offsets.ends[i] + "]");
	        }
        }

        offsets.chunking();
        
        if (LuceneIndex.DEBUG) {
	        System.out.println("after chunking");
	        
	        for (int i = 0; i < offsets.len; i++) {
	        	System.out.println("" + offsets.ids[i] + " " + offsets.offsets[i] + " [" + offsets.starts[i] + " : " + offsets.ends[i] + "]");
	        }
        }
        
        offsets.cleanup();
        
        if (LuceneIndex.DEBUG) {
	        System.out.println("after cleanup");
	        
	        for (int i = 0; i < offsets.len; i++) {
	        	System.out.println("" + offsets.ids[i] + " " + offsets.offsets[i] + " [" + offsets.starts[i] + " : " + offsets.ends[i] + "]");
	        }
        }
    }

    private NodePath getPath(NodeProxy proxy) {
        NodePath path = new NodePath();
        StoredNode node = (StoredNode) proxy.getNode();
        walkAncestor(node, path);
        return path;
    }

    private void walkAncestor(StoredNode node, NodePath path) {
        if (node == null)
            return;
        StoredNode parent = node.getParentStoredNode();
        walkAncestor(parent, path);
        path.addComponent(node.getQName());
    }

    /**
     * Get all query terms from the original queries.
     */
    private void getTerms() {
        Set<Query> queries = new HashSet<Query>();
        termMap = new TreeMap<Object, Query>();
        Match nextMatch = this.match;
        while (nextMatch != null) {
            if (nextMatch.getIndexId() == LuceneIndex.ID) {
                Query query = ((LuceneIndexWorker.LuceneMatch) nextMatch).getQuery();
                if (!queries.contains(query)) {
                    queries.add(query);
                    IndexReader reader = null;
                    try {
                        reader = index.getReader();
                        LuceneUtil.extractTerms(query, termMap, reader, false);
                    } catch (IOException e) {
                        LOG.warn("Error while highlighting lucene query matches: " + e.getMessage(), e);
                    } catch (UnsupportedOperationException uoe) {
                        LOG.warn("Error while highlighting lucene query matches: " + uoe.getMessage(), uoe);
                    } finally {
                        index.releaseReader(reader);
                    }
                }
            }
            nextMatch = nextMatch.getNextMatch();
        }
    }

    private class OffsetList {

        int[] offsets = new int[16];

        int[] starts = new int[16];
        int[] ends = new int[16];

        NodeId[] ids = new NodeId[16];

        int len = 0;

        void add(int startOffset, int endOffset, NodeId nodeId) {
            if (len == offsets.length) {
            	resize(len * 2);
            }
            offsets[len] = startOffset;

            starts[len] = startOffset;
            ends[len] = endOffset;

            ids[len++] = nodeId;
        }

		int getIndex(NodeId nodeId) {
            for (int i = 0; i < len; i++) {
                if (ids[i].equals(nodeId))
                    return i;
            }
            return -1;
        }

        int getFirstIndex(int offset) {
            for (int i = 0; i < len; i++) {
                if (offsets[i] <= offset && (i + 1 == len || offsets[i + 1] > offset)) {

                	for (int j = i - 1; j >= -1; j--) {
                		if (j == -1)
                			return 0;
                		
                		if (offsets[i] != offsets[j]) {
                			return j + 1;
                		}
                	}
                	
                    return i;
                }
            }
            return -1;
        }
		
        int getLastIndex(int offset) {
            for (int i = 0; i < len; i++) {
                if (offsets[i] <= offset && (i + 1 == len || offsets[i + 1] > offset)) {
                    return i;
                }
            }
            return -1;
        }

        int getIndex(int pos) {
            for (int i = 0; i < len; i++) {
                if (starts[i] <= pos && ends[i] >= pos) {
                    return i;
                }
            }
            return -1;
        }

        void exclude(int startOffset, int endOffset) {

        	for (int i = 0; i < len; i++) {
				if (startOffset == starts[i] && endOffset == ends[i]) {
    				
					starts[i] = -1;
					ends[i] = -1;
							
					return;
				}
				
        		if (startOffset >= starts[i] && startOffset <= ends[i]) {
        			if (endOffset > ends[i]) {
        				//exclude and check
        				int tmp = ends[i];
        				
        				ends[i] = startOffset;
        				
        				startOffset = tmp;

        			} else {
        				//split
        				injectAfter(i);
        				
        				int pos = i + 1;
        				offsets[pos] = offsets[i];
        				ids[pos] = ids[i];
        				
        				starts[pos] = endOffset + 1; //???
        				ends[pos] = ends[i];

        				//starts[i] = starts[i];
        				ends[i] = startOffset - 1; //???
        			}
        		}
        	}
		}
        
        void chunking() {
            int pos = 0;

            Offset offset = null;
            for (Entry<NodeId, Offset> entry : nodesWithMatch.entrySet()) {
            	offset = entry.getValue();
            	
            	while (offset != null) {
	            	int start = offset.gStartOffset - chunkOffset;
	            	if (start > 0) {
	            		int b = getIndex(pos);
	            		int e = getIndex(start);
	            		
	            		if (b > -1) {
		            		for (int i = b; i <= e; i++) {
		            			if (starts[i] != -1) {
		            				if (ends[i] > start) {
		            					if (starts[i] < start) {
		            						if (pos != 0 && starts[i] <= pos && ends[i] >= pos) {
		            							//starts[i] = start;
		            							
		            						} else {
		            							starts[i] = start;
		            						}
		            					}
		            				} else if (pos != 0 && starts[i] <= pos && ends[i] >= pos) {
		            					ends[i] = pos;
		            				} else {
		            					starts[i] = -1;
		            					ends[i] = -1;
		            				}
		            			}
		            		}
	            		}
	            	}
	            	pos = offset.gEndOffset + chunkOffset + 1;
	            	
	            	offset = offset.next;
            	}
            }
            
    		int b = getIndex(pos);
    		
    		if (b > -1) {
	    		for (int i = b; i < len; i++) {
	    			if (starts[i] != -1) {
	    				if (starts[i] <= pos) {
	    					if (ends[i] >= pos) {
	    						ends[i] = pos;
	    					}
	    				} else {
	    					starts[i] = -1;
	    					ends[i] = -1;
	    				}
	    			}
	    		}
    		}
        }
        
        void cleanup() {
        	int count = 0;

        	for (int i = 0; i < len; i++) {
    			if (starts[i] != -1 && ends[i] != -1)
    				count++;
        	}
        	
        	int[] _offsets = new int[count];
        	
        	int[] _starts = new int[count];
        	int[] _ends = new int[count];

        	NodeId[] _ids = new NodeId[count];
        	
        	count = 0;
        	for (int i = 0; i < len; i++) {
    			if (starts[i] != -1 && ends[i] != -1) {
    				_offsets[count] = offsets[i];
    				_starts[count] = starts[i];
    				_ends[count] = ends[i];
    				_ids[count] = ids[i];
    				
    				count++;
    			}
        	}
			
        	offsets = _offsets;
			starts = _starts;
			ends = _ends;
			ids = _ids;
        	
        	len = count;
        }
        
        void injectAfter(int pos) {
//        	if (len == offsets.length) {
//        		resize(len*2);
//        	}
        	
            offsets = split(offsets, pos);
            
            starts = split(starts, pos);
            ends = split(ends, pos);
            
            ids = split(ids, pos);

            len++;
        }
        
        int[] split(int[] array, int pos) {
            int[] tempOffsets = new int[array.length+1];
            
            System.arraycopy(array, 0, tempOffsets, 0, pos+1);
        	System.arraycopy(array, pos+1, tempOffsets, pos+2, len - pos - 1);
            
            return tempOffsets;
        }
        
        NodeId[] split(NodeId[] array, int pos) {
        	NodeId[] tempOffsets = new NodeId[array.length+1];
            
            System.arraycopy(array, 0, tempOffsets, 0, pos+1);
            System.arraycopy(array, pos+1, tempOffsets, pos+2, len - pos - 1);
            
            return tempOffsets;
        }

        void resize(int upTo) {
            offsets = resize(offsets, upTo);

            starts = resize(starts, upTo);
            ends = resize(ends, upTo);

            ids = resize(ids, upTo);
        }
        
        int[] resize(int[] array, int size) {
            int[] tempOffsets = new int[size];
            System.arraycopy(array, 0, tempOffsets, 0, len);
            return tempOffsets;
        }

        NodeId[] resize(NodeId[] array, int size) {
            NodeId[] tempIds = new NodeId[size];
            System.arraycopy(ids, 0, tempIds, 0, len);
            return tempIds;
        }
    }

    private class Offset {

        int gStartOffset;
        int gEndOffset;

    	int startOffset;
        int endOffset;
        Offset next = null;

        Offset(int gStartOffset, int gEndOffset, int startOffset, int endOffset) {
            this.gStartOffset = gStartOffset;
            this.gEndOffset = gEndOffset;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        void add(int gOffset, int gEndOffset, int offset, int endOffset) {
            if (startOffset == offset)
                // duplicate match starts at same offset. ignore.
                return;
            getLast().next = new Offset(gOffset, gEndOffset, offset, endOffset);
        }

        private Offset getLast() {
            Offset next = this;
            while (next.next != null) {
                next = next.next;
            }
            return next;
        }

        void setEndOffset(int offset) {
            getLast().endOffset = offset;
        }
    }
}
