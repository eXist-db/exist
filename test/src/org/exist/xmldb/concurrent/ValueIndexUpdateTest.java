/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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

import org.exist.collections.CollectionConfiguration;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.ValueAppendAction;

/**
 * @author wolf
 */
public class ValueIndexUpdateTest extends ConcurrentTestBase {

    private final static String URI = XmldbURI.LOCAL_DB;
    
    private final static String XCONF =
        "<exist:collection xmlns:exist=\"http://exist-db.org/collection-config/1.0\">" +
	        "<exist:index doctype=\"items\" xmlns:x=\"http://www.foo.com\">" +
		        "<exist:fulltext default=\"none\"/>" +
		        "<exist:create path=\"//item/@id\" type=\"xs:integer\"/>" +
		        "<exist:create path=\"//item/name\" type=\"xs:string\"/>" +
		        "<exist:create path=\"//item/value\" type=\"xs:double\"/>" +
	        "</exist:index>" +
        "</exist:collection>";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(ValueIndexUpdateTest.class);
	}
	
    public ValueIndexUpdateTest(String name) {
        super(name, URI, "C1");
    }
    
    protected void setUp() {
    	try {
			super.setUp();			
			DBUtils.addXMLResource(getTestCollection(), CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE, XCONF);
			DBUtils.addXMLResource(getTestCollection(), "R1.xml", "<items/>");			
			addAction(new ValueAppendAction(URI + "/C1", "R1.xml"), 50, 0, 500);
    	} catch (Exception e) {            
            fail(e.getMessage()); 
        }				
	}
}
