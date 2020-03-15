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
package org.exist.xquery;

import org.junit.Test;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import java.net.URISyntaxException;

/**
 * @author Jean-Marc Vanel http://jmvanel.free.fr/
 */
public class ValueIndexByQNameTest extends ValueIndexTest {

    private String config =
    	"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" + 
    	"	<index xmlns:x=\"http://www.foo.com\" xmlns:xx=\"http://test.com\">" + 
    	"		<create qname=\"itemno\" type=\"xs:integer\"/>" +

//    	"		<create-by-qname qname=\"//item/name\" type=\"xs:string\"/>" + 
    	"		<create qname=\"name\" type=\"xs:string\"/>" + 

//    	"		<create path=\"//item/stock\" type=\"xs:integer\"/>" + 
//    	"		<create path=\"//item/price\" type=\"xs:double\"/>" + 
//    	"		<create path=\"//item/price/@specialprice\" type=\"xs:boolean\"/>" + 
//    	"		<create path=\"//item/x:rating\" type=\"xs:double\"/>" +
    	"		<create qname='@xx:test' type='xs:integer' />" +
    	"       <create qname='mixed' type='xs:string' />" +
    	"	</index>" + 
    	"</collection>";

	@Test
	@Override
	public void strings() throws XMLDBException, URISyntaxException {
        configureCollection(config);
        XPathQueryService service = storeXMLFileAndGetQueryService(ITEMS_FILENAME, ITEMS_FILE);

        // queryResource(service, "items.xml", "//item[name > 'Racing Bicycle']", 4 );

         queryResource(service, "items.xml", 
        		"util:qname-index-lookup( xs:QName('name'), 'Racing Bicycle' ) / parent::item" , 1 );       
         queryResource(service, "items.xml",
        	"util:qname-index-lookup( xs:QName('itemno'), 3) / parent::item", 1);    
        queryResource(service, "items.xml", 
        		"declare namespace xx='http://test.com'; " +
        		"util:qname-index-lookup( xs:QName('xx:test'), 123, false() )", 1);

//        queryResource(service, "items.xml", "//item[name &= 'Racing Bicycle']", 1);
//        queryResource(service, "items.xml", "//item[mixed = 'uneven']", 1);
		queryResource(service, "items.xml", 
			"util:qname-index-lookup( xs:QName('mixed'), 'external' )", 1);
//		queryResource(service, "items.xml", "//item[fn:matches(mixed, 'un.*')]", 2);
	}

	protected String getCollectionConfig() {
		return config;
	}
}
