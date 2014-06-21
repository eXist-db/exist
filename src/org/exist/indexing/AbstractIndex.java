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
package org.exist.indexing;

import org.exist.Database;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

public abstract class AbstractIndex implements Index {
	
    protected Database db;
    
    //Probably not useful for every kind of index. Anyway...
    private String dataDir = null; 
    protected String name = null;    

    public void configure(Database db, String dataDir, Element config) throws DatabaseConfigurationException {
        this.db = db;
        this.dataDir = dataDir; 
        if (config != null && config.hasAttribute("id"))
            {name = config.getAttribute("id");}
    }

    public String getIndexName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Database getDatabase() {
        return db;
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
