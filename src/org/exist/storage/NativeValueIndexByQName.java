/*  *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 * Created on 25 mai 2005
$Id$ */

package org.exist.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.dom.SymbolTable;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.lock.Lock;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;
import org.exist.xquery.Constants;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/** The new index by QName that will make queries like
<pre>
/ root [ key = 123 ]
</pre>
very quick. 
It is used by an Xquery extension function with this signature :
<pre>
qname-index-lookup( $qname as xs:string, 
                    $key as xs:string ) as node*
</pre>

that can be used this way :

<pre>
$key := qname-index-lookup( "key", "123")
$user := $key / parent::root
</pre>

The way of indexing is the same as current range indices {@link NativeValueIndex}, 
except that for each QName like <key> mentioned above, the QName will be stored .
Related test: @link org.exist.xquery.test.ValueIndexByQNameTest

 * @author Jean-Marc Vanel http://jmvanel.free.fr/
 */
public class NativeValueIndexByQName extends NativeValueIndex implements ContentLoadingObserver {

	private final static Logger LOG = Logger.getLogger(NativeValueIndexByQName.class);
	
    public static final String FILE_NAME = "values-by-qname.dbx";
    public static final String  FILE_KEY_IN_CONFIG = "db-connection2.values";	
	
	public static int OFFSET_COLLECTION_ID = 0;
	//Notice that the conventional design is to serialize OFFSET_SYMBOL *then* OFFSET_NSSYMBOL
	//TODO : investigate
	public static int OFFSET_NS_URI = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //2
	public static int OFFSET_LOCAL_NAME = OFFSET_NS_URI + SymbolTable.LENGTH_NS_URI; //4
	public static int OFFSET_VALUE = OFFSET_LOCAL_NAME + SymbolTable.LENGTH_LOCAL_NAME; //6

    public NativeValueIndexByQName(DBBroker broker, byte id, String dataDir, Configuration config) throws DBException {
        super(broker, id, dataDir, config);	       
    }
    
    public String getFileName() {
    	return FILE_NAME;      
    }
    
    public String getConfigKeyForFile() {
    	return FILE_KEY_IN_CONFIG;
    } 
    
    public NativeValueIndex getInstance() {
    	return this;
    }
    	    
    public void storeAttribute(RangeIndexSpec spec, AttrImpl node, NodePath currentPath, boolean index) {    
        DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
        //Here's the point : 2 configuration objects ! 
        QNameRangeIndexSpec qnIdx = doc.getCollection().getIndexByQNameConfiguration(broker, node.getQName());
        if (qnIdx != null) {
            this.setDocument(doc);
    		ValueIndexKeyFactory keyFactory = computeTemporaryKey(spec.getType(), node.getValue(), node.getQName());
            updatePendingIndexEntry(node, keyFactory);
        }       
    }
    
	/** @see org.exist.storage.NativeValueIndex#storeElement(int, org.exist.dom.ElementImpl, java.lang.String)
	 */
	public void storeElement(int xpathType, ElementImpl node, String content) {		
		ValueIndexKeyFactory keyFactory = computeTemporaryKey( xpathType, content, node.getQName() );
        updatePendingIndexEntry(node, keyFactory);
	}
    
    /** updates the index type of given node according to the Index By QName config. */
    public void startElement(ElementImpl node, NodePath currentPath, boolean index) {  
        DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
        QNameRangeIndexSpec qnIdx = doc.getCollection().getIndexByQNameConfiguration(broker, node.getQName());
        if (qnIdx != null) {
            int newIndexType = RangeIndexSpec.QNAME_INDEX;
            ElementImpl elementImpl = (ElementImpl) node;
            elementImpl.setIndexType(newIndexType | elementImpl.getIndexType());
        }        
    }
    
    public void endElement(ElementImpl node, NodePath currentPath, String content) {
        localMarkElement(node, currentPath, content);   
    }
    
    public void removeElement( ElementImpl node, NodePath currentPath, String content ) {
        localMarkElement(node, currentPath, content);   
    }    

	/** adds or updates an entry in the {@link #pending} map
	 * @param node the DOM node
	 * @param keyFactory a {@link QNameValueIndexKeyFactory}
	 */
	private void updatePendingIndexEntry(StoredNode node, ValueIndexKeyFactory keyFactory) {
		if(keyFactory == null)
            return;		// skip
		ArrayList buf;
		if (pending.containsKey(keyFactory))
            buf = (ArrayList) pending.get(keyFactory);
        else {
            buf = new ArrayList(8);
            pending.put(keyFactory, buf);
        }
		buf.add(node.getNodeId());
	}

	/** compute a key for the {@link #pending} map */
    private ValueIndexKeyFactory computeTemporaryKey(int xpathType, String value, QName qname) {
        final StringValue str = new StringValue(value);
        AtomicValue atomic = null;
		QNameValueIndexKeyFactory ret = null;
		
        if(Type.subTypeOf(xpathType, Type.STRING))
            atomic = str;
        else {
            try {
                atomic = str.convertTo(xpathType);
            } catch (XPathException e) {
                LOG.warn("Node value: '" + value + "' cannot be converted to type " + 
                        Type.getTypeName(xpathType));
            }
        }
		
        if( atomic instanceof Indexable ) {
			if ( atomic != null )
				ret = new QNameValueIndexKeyFactory((Indexable)atomic, qname );
        } else {
            LOG.warn("The specified type: '" + Type.getTypeName(xpathType) +
            		"' and value '" + value + "'" +
                    " cannot be used as index key. It is null or does not implement interface Indexable.");
			atomic = null;
		}
        return ret;      
    }
	

	/** key for the {@link #pending} map ; the order is lexicographic on 
	 * qname first, indexable second ;
	 * this class also provides through serialize() the persistant storage key :
	 * (collectionId, qname, indexType, indexData)
	 */
    // TODO  "ValueIndexKeyFactory" refactoring: remove after refactoring NativeValueIndex
	private class QNameValueIndexKeyFactory implements ValueIndexKeyFactory, Indexable {
		private QName qname;
		private Indexable indexable;
		
		public QNameValueIndexKeyFactory(Indexable indexable, QName qname) {
			this.indexable = indexable;
			this.qname = qname;
		}

		/** called from {@link NativeValueIndex};
		 * provides the persistant storage key :
		 * (collectionId, qname, indexType, indexData) */
		public byte[] serialize(short collectionId, boolean caseSensitive) throws EXistException {
	        final byte[] data = indexable.serializeValue(OFFSET_VALUE, caseSensitive);
	        ByteConversion.shortToByte(collectionId, data, OFFSET_COLLECTION_ID);
			SymbolTable symbols = broker.getSymbols();
			short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
			ByteConversion.shortToByte(namespaceId, data, OFFSET_NS_URI);
			short localNameId = symbols.getSymbol(qname.getLocalName());
			ByteConversion.shortToByte(localNameId, data, OFFSET_LOCAL_NAME);
			return data;
		}

		public byte[] serialize(short collectionId) throws EXistException {
	        final byte[] data = indexable.serializeValue(OFFSET_VALUE);
	        ByteConversion.shortToByte(collectionId, data, OFFSET_COLLECTION_ID);
			SymbolTable symbols = broker.getSymbols();
			short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
			ByteConversion.shortToByte(namespaceId, data, OFFSET_NS_URI);
			short localNameId = symbols.getSymbol(qname.getLocalName());
			ByteConversion.shortToByte(localNameId, data, OFFSET_LOCAL_NAME);
			return data;
		}
		
		/** @return negative value <==> this object is less than other */
		public int compareTo(Object other) {
			int ret = 0;
			if ( other instanceof QNameValueIndexKeyFactory ) {
				QNameValueIndexKeyFactory otherIndexable = (QNameValueIndexKeyFactory)other;
				int qnameComparison = qname.compareTo(otherIndexable.qname);
				if ( qnameComparison != 0 ) {
					ret = qnameComparison;
				} else {
					ret = indexable.compareTo(otherIndexable.indexable);
				}
			}
			return ret;
		}

		/** unused - TODO "ValueIndexKeyFactory" refactoring: remove after refactoring NativeValueIndex */
		public byte[] serializeValue( int offset, boolean caseSensitive) {
			return null;
		}

		/** unused - TODO "ValueIndexKeyFactory" refactoring: remove after refactoring NativeValueIndex */
		public byte[] serializeValue( int offset) {
			return null;
		}
		
        public int getType() {
            return indexable.getType();
        }
	}

	/** called from the special XQuery function util:qname-index-lookup() */
	public Sequence findByQName(QName qname, AtomicValue comparisonCriterium, Sequence contextSequence) throws XPathException {
		NodeSet contextSet = contextSequence.toNodeSet();
		DocumentSet docSet = contextSet.getDocumentSet();
		
		ValueIndexKeyFactory 
		// Indexable 
		indexable = new QNameValueIndexKeyFactory( (Indexable)comparisonCriterium, qname);
		int relation = Constants.EQ;
		return find(relation, docSet, contextSet, indexable);
	}
	
	/** find
	 * @param relation binary operator used for the comparison
	 * @param value right hand comparison value */
    public NodeSet find(int relation, DocumentSet docs, NodeSet contextSet, ValueIndexKeyFactory value) 
            throws TerminatedException {
        int idxOp =  checkRelationOp(relation);
        NodeSet result = new ExtArrayNodeSet();
        Lock lock = dbValues.getLock();
        for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
			Collection collection = (Collection) iter.next();
			short collectionId = collection.getId();
			try {
				Value key = new Value(value.serialize(collectionId, caseSensitive));
				IndexQuery query = new IndexQuery(idxOp, key);
				lock.acquire();
				try {
					SearchCallback callback = new SearchCallback(docs, contextSet, result, false);
					dbValues.query(query, callback );
				} catch (IOException ioe) {
					//TODO : error ?
					LOG.warn(ioe);
				} catch (BTreeException bte) {
					//TODO : error ?
					LOG.warn(bte);
				}
			} catch (EXistException e) {
                LOG.error(e.getMessage(), e);
            } catch (LockException e) {
				LOG.warn(e);
			} finally {
				lock.release();
			}
        }
        return result;
    }    

	private void localMarkElement(ElementImpl node, NodePath currentPath, String content) {
		DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
		QNameRangeIndexSpec qnIdx = doc.getCollection().getIndexByQNameConfiguration(broker, node.getQName());
		if (qnIdx != null) {
			this.setDocument(doc);
			this.storeElement(qnIdx.getType(), (ElementImpl) node, content);
		}		
	}
	
    public void dropIndex(DocumentImpl doc) throws ReadOnlyException {
    	super.dropIndex(doc);
    }
    
    public void closeAndRemove() {
   		super.closeAndRemove();
    }
    
    public boolean close() throws DBException {
   		return super.close();
    }    
}
