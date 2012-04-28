/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
package org.exist.xmldb.concurrent;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.TextUpdateAction;

/**
 * @author wolf
 */
public class TextUpdateTest extends ConcurrentTestBase {

	private final static String URI = XmldbURI.LOCAL_DB;
	
	private final static String XML =
		"<article/>";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(TextUpdateTest.class);
	}
	
	public TextUpdateTest(String name) {
		super(name, URI, "C1");
	}
	
	protected void setUp() {
		try {
			super.setUp();			
			DBUtils.addXMLResource(getTestCollection(), "R1.xml", XML);	        
			addAction(new TextUpdateAction(URI + "/C1", "R1.xml"), 1000, 0, 0);
		} catch (Exception e) {            
            fail(e.getMessage()); 
        }				
	}
		
}
