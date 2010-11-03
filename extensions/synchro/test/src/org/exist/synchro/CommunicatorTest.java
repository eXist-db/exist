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


import org.exist.xmldb.XmldbURI;
import org.jgroups.View;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class CommunicatorTest {
	
	private static Communicator communicator1 = null;
	private static Communicator communicator2 = null;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		communicator1 = new Communicator(null);
		communicator2 = new Communicator(null);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		communicator1.shutdown();
		communicator2.shutdown();
	}

	@Test
	public void simple() throws Exception {
		
		View view=communicator1.channel.getView();
		System.out.println("view channels = " + view);
		
//		communicator1.beforeCreateDocument(XmldbURI.create("/db/test"));
//		communicator1.afterCreateDocument(XmldbURI.create("/db/test"));
//
//		communicator2.createDocument(XmldbURI.create("/db/test1"));
//
//		communicator1.deleteDocument(XmldbURI.create("/db/test"));
	}

}
