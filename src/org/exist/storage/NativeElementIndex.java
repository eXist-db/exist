/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id:
 */
package org.exist.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Category;
import org.dbxml.core.DBException;
import org.dbxml.core.data.Value;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.util.Configuration;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.ProgressIndicator;
import org.exist.util.ReadOnlyException;
import org.exist.util.VariableByteOutputStream;

/**
 *  ElementIndex collects all element occurrences. It uses the name of the
 *  element and the current doc_id as keys and stores all occurrences of this
 *  element in a blob. This means that the blob just contains an array of gid's
 *  which may be compressed if useCompression is true. Storing all occurrences
 *  in one large blob is much faster than storing each of them in a single table
 *  row.
 *
 *@author     Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 */
public class NativeElementIndex extends ElementIndex {

	private static Category LOG =
		Category.getInstance(NativeElementIndex.class.getName());

	public final static int PARTITION_SIZE = 102400;

	protected BFile dbElement;

	public NativeElementIndex(
		DBBroker broker,
		Configuration config,
		BFile dbElement) {
		super(broker, config);
		this.dbElement = dbElement;
	}

	public void addRow(String elementName, NodeProxy proxy) {
		ArrayList buf;
		if (elementIds.containsKey(elementName))
			buf = (ArrayList) elementIds.get(elementName);
		else {
			buf = new ArrayList(50);
			elementIds.put(elementName, buf);
		}
		buf.add(proxy);
	}

	public void reindex(DocumentImpl oldDoc) {
		if (elementIds.size() == 0)
			return;
		Lock lock = dbElement.getLock();
		Map.Entry entry;
		String elementName;
		NodeSet oldList;
		ArrayList newList, idList;
		NodeProxy p;
        NodeProxy nodeList[];
        VariableByteOutputStream os = new VariableByteOutputStream();
        int count = 1, len;
        byte[] data;
        Value ref;
        Value val;
        long prevId, cid, addr;
        short collectionId = oldDoc.getCollection().getId();
		try {
			for (Iterator i = elementIds.entrySet().iterator(); i.hasNext();) {
				entry = (Map.Entry) i.next();
				idList = (ArrayList) entry.getValue();
				elementName = (String) entry.getKey();
				DocumentSet docs = new DocumentSet();
				docs.add(oldDoc);
				oldList = broker.findElementsByTagName(docs, elementName);
				newList = new ArrayList(oldList.getLength());
				for (Iterator j = oldList.iterator(); j.hasNext();) {
					p = (NodeProxy) j.next();
					if (oldDoc.getTreeLevel(p.gid) < oldDoc.reindexRequired())
						newList.add(p);
				}
				newList.addAll(idList);
				nodeList = new NodeProxy[newList.size()];
				nodeList = (NodeProxy[]) newList.toArray(nodeList);
				len = nodeList.length;
				Arrays.sort(nodeList);
				os.writeInt(doc.getDocId());
				os.writeInt(len);
				prevId = 0;
				for (int j = 0; j < len; j++) {
					cid = nodeList[j].gid - prevId;
					prevId = nodeList[j].gid;
					os.writeLong(cid);
					addr = nodeList[j].internalAddress;
					os.writeInt(DOMFile.pageFromPointer(addr));
					os.writeInt(DOMFile.tidFromPointer(addr));
				}
				data = os.toByteArray();
				os.clear();
				short sym = NativeBroker.getSymbols().getSymbol(elementName);
				ref = new NativeBroker.ElementValue(collectionId, sym);
				try {
					lock.acquire(this, Lock.WRITE_LOCK);
					lock.enter(this);
					if (!dbElement.put(ref, data))
						LOG.warn(
							"could not save index for element " + elementName);
						continue;
				} catch (LockException e) {
					LOG.error("could not acquire lock on elements", e);
				} finally {
					lock.release(this);
				}
				count++;
			}
		} catch (ReadOnlyException e) {
			LOG.warn("database is read only");
		}
        elementIds = new TreeMap();
	}
    
	public void flush() {
		if (elementIds.size() == 0)
			return;
		final ProgressIndicator progress =
			new ProgressIndicator(elementIds.size());

		NodeProxy proxy;
		String elementName;
		ArrayList idList;
		int count = 1, len;
		byte[] data;
		String name;
		Value ref;
		Value val;
		Map.Entry entry;
		NodeProxy nodeList[];
		VariableByteOutputStream os = new VariableByteOutputStream();
		// get collection id for this collection
		final String docName = doc.getFileName();
		long prevId;
		long cid;
		long addr;
		short collectionId = doc.getCollection().getId();
		Lock lock = dbElement.getLock();
		try {
			for (Iterator i = elementIds.entrySet().iterator(); i.hasNext();) {
				entry = (Map.Entry) i.next();
				elementName = (String) entry.getKey();
				idList = (ArrayList) entry.getValue();
				i.remove();
				nodeList = new NodeProxy[idList.size()];
				nodeList = (NodeProxy[]) idList.toArray(nodeList);
				len = nodeList.length;
				Arrays.sort(nodeList);
				os.writeInt(doc.getDocId());
				os.writeInt(len);
				prevId = 0;
				for (int j = 0; j < len; j++) {
					cid = nodeList[j].gid - prevId;
					prevId = nodeList[j].gid;
					os.writeLong(cid);
					addr = nodeList[j].internalAddress;
					os.writeInt(DOMFile.pageFromPointer(addr));
					os.writeInt(DOMFile.tidFromPointer(addr));
				}
				data = os.toByteArray();
				os.clear();
				short sym = NativeBroker.getSymbols().getSymbol(elementName);
				ref = new NativeBroker.ElementValue(collectionId, sym);
				try {
					lock.acquire(this, Lock.WRITE_LOCK);
					lock.enter(this);
					if (!dbElement.append(ref, data)) {
						LOG.warn(
							"could not save index for element " + elementName);
						continue;
					}
				} catch (LockException e) {
					LOG.error("could not acquire lock on elements", e);
				} finally {
					lock.release(this);
				}
				progress.setValue(count);
				setChanged();
				notifyObservers(progress);
				count++;
			}
		} catch (ReadOnlyException e) {
			LOG.warn("database is read-only");
			return;
		}
		elementIds.clear();
		//elementIds = new TreeMap();
	}

	private Value findPartition(short collectionId, short symbol, int len) {
		NativeBroker.ElementValue ref =
			new NativeBroker.ElementValue(collectionId, symbol, (short) 0);
		return ref;
		//        IndexQuery query =
		//                new IndexQuery(null, IndexQuery.TRUNC_RIGHT, ref);
		//        synchronized (dbElement) {
		//            try {
		//                ArrayList partitions = dbElement.findKeys(query);
		//                Value next;
		//                int size;
		//                for(Iterator i = partitions.iterator(); i.hasNext(); ) {
		//                    next = (Value) i.next();
		//                    size = dbElement.getValueSize( next );
		//                    if( size + len < PARTITION_SIZE ) {
		//                        return next;
		//		    }
		//                }
		//            } catch(IOException e) {
		//                LOG.warn(e);
		//            } catch(BTreeException e) {
		//                LOG.warn(e);
		//            }
		//            return null;
		//        }
	}

	/**  Description of the Method */
	public void sync() {
		Lock lock = dbElement.getLock();
		try {
			lock.acquire(this, Lock.WRITE_LOCK);
			lock.enter(this);
			try {
				dbElement.flush();
			} catch (DBException dbe) {
				LOG.warn(dbe);
			}
		} catch (LockException e) {
			LOG.warn("could not acquire lock for elements", e);
		} finally {
			lock.release(this);
		}
	}
}
