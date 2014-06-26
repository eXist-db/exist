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
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.indexing;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

public abstract class AbstractIndex implements Index {
	
    /**
     * Holds an id which uniquely identifies this index. This is usually the class name. 
     */
    protected static String ID = "Give me an ID !";
    
    public static String getID() {
    	return ID;
    }

    protected BrokerPool pool;    
    //Probably not useful for every kind of index. Anyway...
    private String dataDir = null; 
    protected String name = null;    

    public void configure(BrokerPool pool, String dataDir, Element config)
            throws DatabaseConfigurationException {
        this.pool = pool;
        this.dataDir = dataDir; 
        if (config != null && config.hasAttribute("id"))
            {name = config.getAttribute("id");}
    }

    public String getIndexId() {
    	return getID();
    }

    public String getIndexName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BrokerPool getBrokerPool() {
        return pool;
    }

    //TODO : declare in interface ?
    public String getDataDir() {
        return dataDir;
    } 

    public abstract void open() throws DatabaseConfigurationException;

    public abstract void close() throws DBException;

    public abstract void sync() throws DBException;	

    public abstract void remove() throws DBException;

    public abstract IndexWorker getWorker(DBBroker broker);

    public abstract boolean checkIndex(DBBroker broker);

    @Override
    public BTree getStorage() {
        return null;
    }
}
