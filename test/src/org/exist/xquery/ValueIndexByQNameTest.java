/* Created on 30 mai 2005
$Id$ */
package org.exist.xquery;

import org.xmldb.api.modules.XPathQueryService;

/**
 * @author Jean-Marc Vanel http://jmvanel.free.fr/
 */
public class ValueIndexByQNameTest extends ValueIndexTest {

    private String config =
    	"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" + 
    	"	<index xmlns:x=\"http://www.foo.com\" xmlns:xx=\"http://test.com\">" + 
    	"		<fulltext default=\"none\">" + 
    	"			<include path=\"//item/name\"/>" + 
    	"			<include path=\"//item/mixed\"/>" + 
    	"		</fulltext>" + 
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

	public void testStrings() throws Exception {
        configureCollection(config);
        XPathQueryService service = storeXMLFileAndGetQueryService("items.xml", "test/src/org/exist/xquery/items.xml");

        // queryResource(service, "items.xml", "//item[name > 'Racing Bicycle']", 4 );

         queryResource(service, "items.xml", 
        		"util:qname-index-lookup( xs:QName('name'), 'Racing Bicycle' ) / parent::item" , 1 );       
         queryResource(service, "items.xml",
        	"util:qname-index-lookup( xs:QName('itemno'), 3) / parent::item", 1);    
        queryResource(service, "items.xml", 
        		"declare namespace xx='http://test.com'; " +
        		"util:qname-index-lookup( xs:QName('xx:test'), 123) ", 1);

//        queryResource(service, "items.xml", "//item[name &= 'Racing Bicycle']", 1);
//        queryResource(service, "items.xml", "//item[mixed = 'uneven']", 1);
		queryResource(service, "items.xml", 
			"util:qname-index-lookup( xs:QName('mixed'), 'external' )", 1);
//		queryResource(service, "items.xml", "//item[fn:matches(mixed, 'un.*')]", 2);
	}

	protected String getCollectionConfig() {
		return config;
	}
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(ValueIndexByQNameTest.class);
    }
}
