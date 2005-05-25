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
import org.exist.storage.store.BFile;
import org.exist.util.LongLinkedList;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Jean-Marc Vanel http://jmvanel.free.fr/
 */
public class NativeValueIndexByQName extends NativeValueIndex {
	
    private final static Logger LOG = Logger.getLogger(NativeValueIndexByQName.class);


	/**
	 * @param broker
	 * @param valuesDb
	 */
	public NativeValueIndexByQName(DBBroker broker, BFile valuesDb) {
		super(broker, valuesDb);
	}
	
	/** ? @see org.exist.storage.NativeValueIndex#storeAttribute(org.exist.storage.ValueIndexSpec, org.exist.dom.AttrImpl)
	 */
	public void storeAttribute(ValueIndexSpec spec, AttrImpl node) {
		QName qname = new QName( node.getNodeName(), node.getNamespaceURI() );
		Indexable indexable = computeTemporaryKey(spec.getType(), node.getValue(), qname);
        updatePendingIndexEntry(node, indexable);
	}

	/** ? @see org.exist.storage.NativeValueIndex#storeElement(int, org.exist.dom.ElementImpl, java.lang.String)
	 */
	public void storeElement(int xpathType, ElementImpl node, String content) {		
		QName qname = new QName( node.getNodeName(), node.getNamespaceURI() );
		Indexable indexable = computeTemporaryKey( xpathType, content, qname );
        updatePendingIndexEntry(node, indexable);
	}

	/**
	 * @param node
	 * @param indexable
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

	/** compute a key for the "pending" map */
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
	
	private class QNameIndexable implements Indexable {
		private Indexable indexable;
		private QName qname;
		
		public QNameIndexable(Indexable indexable, QName qname) {
			this.indexable = indexable;
			this.qname = qname;
		}

		public byte[] serialize(short collectionId) {
			// TODO Auto-generated method stub
			return null;
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
		
	}
}
