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

import org.xmldb.api.base.Collection;

/**
 * Test concurrent XUpdates on the same document.
 * 
 * @author wolf
 */
public class ConcurrentXUpdateTest extends ConcurrentTestBase {

	private final static String URI = "xmldb:exist:///db";
	
	private Collection root;
	private String[] wordList;
	
	private volatile boolean failed = false;
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(ConcurrentXUpdateTest.class);
	}

	public ConcurrentXUpdateTest(String name) {
		super(name, URI, "C1");
	}
	
	public void testConcurrent() throws Exception {
		Thread t1 = new Runner(new RemoveAppendAction(URI + "/C1", "R1.xml", wordList), 50);
		Thread t2 = new Runner(new RemoveAppendAction(URI + "/C1", "R1.xml", wordList), 50);
		
		t1.start();
		t2.start();
		
		try {
			t1.join();
			t2.join();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		assertFalse(failed);
	}
}
