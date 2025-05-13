/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing;

import org.exist.storage.BrokerPool;
import org.exist.storage.btree.BTree;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import java.nio.file.Path;

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
    private Path dataDir = null;
    protected String name = null;    

    @Override
    public void configure(final BrokerPool pool, final Path dataDir, final Element config)
            throws DatabaseConfigurationException {
        this.pool = pool;
        this.dataDir = dataDir; 
        if (config != null && config.hasAttribute("id")) {
            name = config.getAttribute("id");
        }
    }

    @Override
    public String getIndexId() {
    	return getID();
    }

    @Override
    public String getIndexName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public BrokerPool getBrokerPool() {
        return pool;
    }

    //TODO : declare in interface ?
    public Path getDataDir() {
        return dataDir;
    }

    @Override
    public BTree getStorage() {
        return null;
    }
}
