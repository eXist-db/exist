
/* eXist xml document repository and xpath implementation
 * Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Observable;
import java.util.TreeMap;

import org.apache.log4j.Category;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.util.Configuration;
import org.exist.util.ProgressIndicator;
import org.exist.util.VariableByteOutputStream;

/**
* ElementIndex collects all element occurrences. It uses the name of the
* element and the current doc_id as keys and stores all occurrences
* of this element in a blob. This means that the blob just contains
* an array of gid's which may be compressed if useCompression is true.
* Storing all occurrences in one large blob is much faster than storing
* each of them in a single table row.
*/
public class ElementIndex extends Observable {

    protected DBConnectionPool pool;
    protected DBBroker broker;
    protected PreparedStatement m_insert, m_get, m_update;
    protected Statement stmt;
    protected TreeMap elementIds = new TreeMap();
    protected DocumentImpl doc;
    protected boolean useCompression = false;
    protected Configuration config;
    private static Category LOG = Category.getInstance(ElementIndex.class.getName());

    public ElementIndex(DBBroker broker, Configuration config) {
        this.broker = broker;
	this.config = config;
    }

    public ElementIndex(DBBroker broker, Configuration config, 
			DBConnectionPool pool, boolean compress) {
        this(broker, config, pool);
        useCompression = compress;
    }

    public ElementIndex(DBBroker broker, Configuration config, DBConnectionPool pool) {
        this.pool = pool;
        this.broker = broker;
	this.config = config;
        Connection con = pool.get();
        try {
            m_insert =
                con.prepareStatement("insert into b_element (doc_id, element_id, data)" +
                                     " values (?, ?, ?)");
            m_get =
                con.prepareStatement("select element_id from b_element where doc_id=? and " +
                                     "element_id=?");
            m_update =
                con.prepareStatement("update b_element set data=concat(data, ?) " +
                                     "where doc_id=? and element_id=?");
            stmt = con.createStatement();
        } catch(SQLException e) {
            LOG.debug(e);
        }
        pool.release(con);
    }

    public void setDocument(DocumentImpl doc) {
        this.doc = doc;
    }
        
    public void addRow(int element_id, NodeProxy proxy) {
        Integer id = new Integer(element_id);
        ArrayList list;
        if(elementIds.containsKey(id))
            list = (ArrayList)elementIds.get(id);
        else {
            list = new ArrayList();
            elementIds.put(id, list);
        }
        list.add(proxy);
    }

    protected void lockTables() {
        try {
            stmt.executeUpdate("lock tables b_element write");
        } catch(SQLException e) {
            LOG.warn("could not lock tables: " + e);
        }
    }

    protected void unlockTables() {
        try {
            stmt.executeUpdate("unlock tables");
        } catch(SQLException e) {
            LOG.warn("could not unlock tables: " + e);
        }
    }

    public void flush() {
        if(elementIds.size() == 0)
            return;
        if(broker.getDatabaseType() == RelationalBroker.MYSQL)
            lockTables();
        else
            try {
                m_insert.getConnection().commit();
            } catch(SQLException e) {
                LOG.debug(e);
            }
        ProgressIndicator progress = new ProgressIndicator(elementIds.size());
        int count = 1;
        for(Iterator i = elementIds.keySet().iterator(); i.hasNext(); count++) {
            Integer id = (Integer)i.next();
            ArrayList list = (ArrayList)elementIds.get(id);
            byte[] data;
            VariableByteOutputStream ostream =
                new VariableByteOutputStream();
            long gid;
            ostream.writeInt(list.size());
            for(Iterator j = list.iterator(); j.hasNext(); ) {
                gid = ((NodeProxy)j.next()).getGID();
                ostream.writeLong(gid);
            }
            data = ostream.toByteArray();

            if(data.length == 0)
                return;
            try {
                m_insert.setInt(1, doc.getDocId());
                m_insert.setInt(2, id.intValue());
                m_insert.setBytes(3, data);
                m_insert.executeUpdate();
            } catch(SQLException e) {
                LOG.warn(e);
            } catch(IndexOutOfBoundsException b) {
                // happens sometimes, don't know why.
                LOG.warn(b);
            }
            //progress.set(count);
            progress.setValue(count);
            setChanged();
            notifyObservers(progress);
        }
        elementIds.clear();
        if(broker.getDatabaseType() == RelationalBroker.MYSQL)
            unlockTables();
        else
            try {
                m_insert.getConnection().commit();
            } catch(SQLException e) {
                LOG.debug(e);
            }
    }

    protected final static void quickSort(long[] list, int low, int high) {
        if(low >= high) return;
        int left_index = low;
        int right_index = high;
        long pivot = list[(low + high) / 2];
        do {
            while(left_index <= high && list[left_index] < pivot)
                left_index++;
            while(right_index >= low && list[right_index] > pivot)
                right_index--;
            if(left_index <= right_index) {
                if(list[left_index] == list[right_index]) {
                    left_index++; right_index--;
                } else {
                    long temp = list[right_index];
                    list[right_index--] = list[left_index];
                    list[left_index++] = temp;
                }
            }
        } while(left_index <= right_index);
        quickSort(list, low, right_index);
        quickSort(list, left_index, high);
    }
}
