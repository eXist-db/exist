/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.synchro;

import java.util.ArrayList;
import java.util.List;

import org.exist.Database;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.dom.DocumentImpl;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
@ConfigurationClass("synchro")
public class Synchro {
	
//	public static void plug() {
//		BrokerPool.plug(Synchro.class);
//	}

	@ConfigurationFieldAsElement("cluster")
	private List<Communicator> clusters = new ArrayList<Communicator>();
	
	private Database db;
	
	public Synchro(Database db) throws DatabaseConfigurationException {
		this.db = db;
		
		db.getIndexManager().registerIndex(new DBWatch(this));
	}
	
	public Database getDatabase() {
		return db;
	}

	public void shutdown() {
		for (Communicator cluster : clusters) {
			cluster.shutdown();
		}
	}

	public Communicator getCluster(DocumentImpl document) {
		XmldbURI uri = document.getURI();
		for (Communicator cluster : clusters) {
			if (uri.startsWith(cluster.collection)) 
				return cluster;
		}
		return null;
	}
}
