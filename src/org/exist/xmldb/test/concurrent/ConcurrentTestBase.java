/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
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
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xmldb.test.concurrent;

import java.io.File;

import org.xmldb.api.base.Collection;
import org.xmldb.api.modules.CollectionManagementService;

import junit.framework.TestCase;

/**
 * Abstract base class for concurrent tests.
 * 
 * @author wolf
 */
public abstract class ConcurrentTestBase extends TestCase {

	protected String uri;
	protected Collection root;
	protected String testCol;
	
	protected String[] wordList;
	
	protected volatile boolean failed = false;
	
	/**
	 * @param arg0
	 */
	public ConcurrentTestBase(String name, String uri, String testCollection) {
		super(name);
		this.uri = uri;
		this.testCol = testCollection;
	}

	public abstract void testConcurrent() throws Exception;
	
	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		root = DBUtils.setupDB(uri);
		wordList = DBUtils.wordList(root);
		File f = DBUtils.generateXMLFile("testdoc.xml", 1000, 10, wordList);

		Collection c1 = root.getChildCollection(testCol);
		if(c1 != null) {
			CollectionManagementService mgr = DBUtils.getCollectionManagementService(root);
			mgr.removeCollection(testCol);
		}
		
		c1 = DBUtils.addCollection(root, testCol);
		DBUtils.addXMLResource(c1, "R1.xml", f);
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		DBUtils.removeCollection(root, testCol);
		DBUtils.shutdownDB(uri);
	}
	
	class Runner extends Thread {
		
		private Action action;
		private int repeat;
		
		public Runner(Action action, int repeat) {
			super();
			this.action = action;
			this.repeat = repeat;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		public void run() {
			try
	        {
	            for (int i = 0; i < repeat; i++)
	            {
	                if (failed)
	                {
	                    break;
	                }

	                failed = action.execute();
	            }
	        }
	        catch (Exception e)
	        {
	        	System.err.println("Action failed in Thread " + getName() + ": " + e.getMessage());
	            e.printStackTrace();
	            failed = true;
	        }
		}
	}
}
