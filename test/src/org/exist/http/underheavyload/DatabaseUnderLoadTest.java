/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2009 The eXist Project
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
package org.exist.http.underheavyload;


import org.junit.Test;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DatabaseUnderLoadTest {

	ClientsManager manager;
	
	org.exist.start.Main database;
	
	@Test
	public void testHeavyLoad() {
		database = new org.exist.start.Main("jetty");
		database.run(new String[]{"jetty"});

		manager = new ClientsManager(5, "http://localhost:" + System.getProperty("jetty.port") + "/exist/admin");
		manager.start();
		
		try {
			Thread.sleep(60*60*1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//TODO: catch errors somehow

       	manager.shutdown();
       	database.shutdown();
	}
}
