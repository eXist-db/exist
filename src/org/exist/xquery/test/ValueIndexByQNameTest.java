/* Created on 30 mai 2005
$Id$ */
package org.exist.xquery.test;

import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XPathQueryService;

/**
 * @author Jean-Marc Vanel http://jmvanel.free.fr/
 */
public class ValueIndexByQNameTest extends ValueIndexTest {

    private String CONFIG =
    	"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" + 
    	"	<index xmlns:x=\"http://www.foo.com\" xmlns:xx=\"http://test.com\">" + 
    	"		<fulltext default=\"none\">" + 
    	"			<include path=\"//item/name\"/>" + 
    	"			<include path=\"//item/mixed\"/>" + 
    	"		</fulltext>" + 
//    	"		<create path=\"//item/itemno\" type=\"xs:integer\"/>" + 

//    	"		<create-by-qname qname=\"//item/name\" type=\"xs:string\"/>" + 
    	"		<create qname=\"name\" type=\"xs:string\"/>" + 

//    	"		<create path=\"//item/stock\" type=\"xs:integer\"/>" + 
//    	"		<create path=\"//item/price\" type=\"xs:double\"/>" + 
//    	"		<create path=\"//item/price/@specialprice\" type=\"xs:boolean\"/>" + 
//    	"		<create path=\"//item/x:rating\" type=\"xs:double\"/>" +
//    	"		<create path=\"//item/@xx:test\" type=\"xs:integer\"/>" +
//    	"       <create path=\"//item/mixed\" type=\"xs:string\"/>" +
    	"	</index>" + 
    	"</collection>";
    
	/** ? @see org.exist.xquery.test.ValueIndexTest#testStrings()
	 */
	public void testStrings() throws Exception {
        configureCollection();
        XPathQueryService service = storeXMLFileAndGetQueryService("items.xml", "src/org/exist/xquery/test/items.xml");
        
        // queryResource(service, "items.xml", "//item[name = 'Racing Bicycle']", 1);
        queryResource(service, "items.xml", 
            	"util:qname-index-lookup( xs:QName('name'), 'Racing Bicycle' ) " , 1 );
    	// "util:qname-index-lookup( xs:QName('name'), 'Racing Bicycle' ) / parent::item" , 1 );

//        queryResource(service, "items.xml", "//item[name > 'Racing Bicycle']", 4);
//        queryResource(service, "items.xml", "//item[itemno = 3]", 1);
//        ResourceSet result = queryResource(service, "items.xml", "for $i in //item[stock <= 10] return $i/itemno", 5);
//        for (long i = 0; i < result.getSize(); i++) {
//            Resource res = result.getResource(i);
//            System.out.println(res.getContent());
//        }
//        
//        queryResource(service, "items.xml", "//item[stock > 20]", 1);
//        queryResource(service, "items.xml", "declare namespace x=\"http://www.foo.com\"; //item[x:rating > 8.0]", 2);
//        queryResource(service, "items.xml", "declare namespace xx=\"http://test.com\"; //item[@xx:test = 123]", 1);
//        queryResource(service, "items.xml", "//item[name &= 'Racing Bicycle']", 1);
//        queryResource(service, "items.xml", "//item[mixed = 'uneven']", 1);
//		queryResource(service, "items.xml", "//item[mixed = 'external']", 1);
//		queryResource(service, "items.xml", "//item[fn:matches(mixed, 'un.*')]", 2);
	}

	/** ? @see org.exist.xquery.test.ValueIndexTest#getCollectionConfig()
	 */
	protected String getCollectionConfig() {
		return CONFIG;
	}

}
