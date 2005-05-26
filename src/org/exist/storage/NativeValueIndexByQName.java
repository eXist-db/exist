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

import org.apache.log4j.Logger;
import org.exist.dom.AttrImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.storage.store.BFile;
import org.exist.util.ByteConversion;
import org.exist.util.LongLinkedList;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
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
 
 * @author Jean-Marc Vanel http://jmvanel.free.fr/
 */
public class NativeValueIndexByQName extends NativeValueIndex {
	
    private final static Logger LOG = Logger.getLogger(NativeValueIndexByQName.class);

	public NativeValueIndexByQName(DBBroker broker, BFile valuesDb) {
		super(broker, valuesDb);
	}
	
	/** @see org.exist.storage.NativeValueIndex#storeAttribute(org.exist.storage.ValueIndexSpec, org.exist.dom.AttrImpl)
	 */
	public void storeAttribute(ValueIndexSpec spec, AttrImpl node) {
		Indexable indexable = computeTemporaryKey(spec.getType(), node.getValue(), node.getQName());
        updatePendingIndexEntry(node, indexable);
	}

	/** @see org.exist.storage.NativeValueIndex#storeElement(int, org.exist.dom.ElementImpl, java.lang.String)
	 */
	public void storeElement(int xpathType, ElementImpl node, String content) {		
		Indexable indexable = computeTemporaryKey( xpathType, content, node.getQName() );
        updatePendingIndexEntry(node, indexable);
	}

	/** adds or updates an entry in the {@link #pending} map
	 * @param node the DOM node
	 * @param indexable a {@link QNameIndexable}
	 */
	private void updatePendingIndexEntry(NodeImpl node, Indexable indexable) {
		if(indexable == null)
            return;		// skip
		LongLinkedList buf;
		if (pending.containsKey(indexable))
            buf = (LongLinkedList) pending.get(indexable);
        else {
            buf = new LongLinkedList();
            pending.put(indexable, buf);
        }
		buf.add(node.getGID());
	}

	/** compute a key for the {@link #pending} map */
    private Indexable computeTemporaryKey(int xpathType, String value, QName qname) {
        final StringValue str = new StringValue(value);
        AtomicValue atomic = null;
		QNameIndexable ret = null;
		
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
				ret = new QNameIndexable((Indexable)atomic, qname );
        } else {
			LOG.warn("The specified type: " + Type.getTypeName(xpathType) +
            " cannot be used as index key. It does not implement interface Indexable.");
			atomic = null;
		}
        return ret;      
    }
	

	/** key for the {@link #pending} map ; the order is lexicographic on 
	 * qname first, indexable second ;
	 * this class also provides through serialize() the persistant storage key :
	 * (collectionId, qname, indexType, indexData)
	 */
	private class QNameIndexable implements Indexable {
		private QName qname;
		private Indexable indexable;
		
		public QNameIndexable(Indexable indexable, QName qname) {
			this.indexable = indexable;
			this.qname = qname;
		}

		/** the one that is called from {@link NativeValueIndex} */
		public byte[] serialize(short collectionId, boolean caseSensitive) {
			// key (collectionId, qname, indexType, indexData)
	        final byte[] data = indexable.serializeValue(4, caseSensitive);
	        ByteConversion.shortToByte(collectionId, data, 0);
			serializeQName(data, 2 );
			return data;
		}
		
		/** serialize the QName field on the persistant storage */
		private void serializeQName(byte[] data, int offset) {
			SymbolTable symbols = broker.getSymbols();
			short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
			short localNameId = symbols.getSymbol(qname.getLocalName());
	        data[offset]   = (byte)namespaceId;
	        data[offset+1] = (byte)localNameId;
		}
		
		/** @return negative value <==> this object is less than other */
		public int compareTo(Object other) {
			int ret = 0;
			if ( other instanceof QNameIndexable ) {
				QNameIndexable otherIndexable = (QNameIndexable)other;
				int qnameComparison = qname.toString().compareTo(otherIndexable.qname.toString());
				if ( qnameComparison != 0 ) {
					ret = qnameComparison;
				} else {
					ret = indexable.compareTo(otherIndexable.indexable);
				}
			}
			return ret;
		}

		/** unused */
		public byte[] serializeValue( int offset, boolean caseSensitive) {
			return null;
		}
	}
}
