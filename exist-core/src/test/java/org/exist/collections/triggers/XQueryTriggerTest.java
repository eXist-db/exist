/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.collections.triggers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URISyntaxException;

import javax.xml.transform.OutputKeys;

import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.Base64Decoder;
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

/** class under test : {@link XQueryTrigger}
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */
public class XQueryTriggerTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

	private final static String TEST_COLLECTION = "testXQueryTrigger";

    /** XQuery module implementing the trigger under test */
    private final static String MODULE_NAME = "XQueryTriggerLogger.xqm";

    private final static String COLLECTION_CONFIG =
    	"<exist:collection xmlns:exist='http://exist-db.org/collection-config/1.0'>" +
	    "  <exist:triggers>" +
		"     <exist:trigger class='org.exist.collections.triggers.XQueryTrigger'>" +
		"	     <exist:parameter " +
		"			name='url' " +
		"			value='" +XmldbURI.LOCAL_DB +  "/" + TEST_COLLECTION + "/" + MODULE_NAME + "' " +
		"        />" +
		"     </exist:trigger>" +
		"  </exist:triggers>" +
        "</exist:collection>";    

    private final static String EMPTY_COLLECTION_CONFIG =
    	"<exist:collection xmlns:exist='http://exist-db.org/collection-config/1.0'/>";
    
    private final static String DOCUMENT_NAME = "test.xml";
    
    private final static String DOCUMENT_CONTENT = 
		  "<test>"
		+ "<item id='1'><price>5.6</price><stock>22</stock></item>"
		+ "<item id='2'><price>7.4</price><stock>43</stock></item>"
		+ "<item id='3'><price>18.4</price><stock>5</stock></item>"
		+ "<item id='4'><price>65.54</price><stock>16</stock></item>"
		+ "</test>";    

    /** XUpdate document update specification */
    private final static String DOCUMENT_UPDATE =
        "<xu:modifications xmlns:xu='http://www.xmldb.org/xupdate' version='1.0'>" +
        "<!-- special offer -->" +
        "<xu:update select='/test/item[@id = \"3\"]/price'>" +       
        	"15.2"+
        "</xu:update>" +
      "</xu:modifications>";
    
//    private final static String MODIFIED_DOCUMENT_CONTENT = 
//    	DOCUMENT_CONTENT.replaceAll("<price>18.4</price>", "<price>15.2</price>");
   
    private final static String BINARY_DOCUMENT_NAME = "1x1.gif";
    private final static String BINARY_DOCUMENT_CONTENT = "R0lGODlhAQABAIABAAD/AP///yH+EUNyZWF0ZWQgd2l0aCBHSU1QACwAAAAAAQABAAACAkQBADs=";    
    
    /** "log" document that will be updated by the trigger */
    private final static String LOG_NAME = "XQueryTriggerLog.xml";
    
    /** initial content of the "log" document */
    private final static String EMPTY_LOG = "<events/>";
    
    /** XQuery module implementing the trigger under test; 
     * the log() XQuery function will add an <event> element inside <events> element */
    private final static String MODULE =
    	"module namespace trigger='http://exist-db.org/xquery/trigger'; " +
    	"import module namespace xmldb='http://exist-db.org/xquery/xmldb'; " +
    	"import module namespace util='http://exist-db.org/xquery/util'; " +
        "" +
    	"declare function trigger:logEvent($type as xs:string, $event as xs:string, $objectType as xs:string, $uri as xs:anyURI) {" +
        "let $log := util:log(\"INFO\", concat($type, ' ', $event, ' ', $objectType, ' ', $uri))" +
    	"let $isLoggedIn := xmldb:login('" + XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION + "', 'admin', '') return " +
    	  "xmldb:update(" +
    	    "'" + XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION + "', " +
            "<xu:modifications xmlns:xu='http://www.xmldb.org/xupdate' version='1.0'>" +
              "<xu:append select='/events'>" +
                "<xu:element name='event'>" +
                  "<xu:attribute name='time'>{current-dateTime()}</xu:attribute>" +
                  "<xu:attribute name='type'>{$type}</xu:attribute>" +
                  "<xu:attribute name='event'>{$event}</xu:attribute>" +
                  "<xu:attribute name='object-type'>{$objectType}</xu:attribute>" +
                  "<xu:element name='uri'>{$uri}</xu:element>" +
                "</xu:element>" +            
              "</xu:append>" +
            "</xu:modifications>" +
          ")" +
        "};" +
        "" +
        "declare function trigger:before-create-collection($uri as xs:anyURI) {" +
        	"trigger:logEvent('before', 'create', 'collection', $uri)" +
        "};" +
        "" +
        "declare function trigger:after-create-collection($uri as xs:anyURI) {" +
        	"trigger:logEvent('after', 'create', 'collection', $uri)" +
        "};" +
        "" +
        "declare function trigger:before-update-collection($uri as xs:anyURI) {" +
        	"trigger:logEvent('before', 'update', 'collection', $uri)" +
        "};" +
        "" +
        "declare function trigger:after-update-collection($uri as xs:anyURI) {" +
        	"trigger:logEvent('after', 'update', 'collection', $uri)" +
        "};" +
        "" +
        "declare function trigger:before-copy-collection($uri as xs:anyURI, $new-uri as xs:anyURI) {" +
        	"trigger:logEvent('before', 'copy', 'collection', $uri)" +
        "};" +
        "" +
        "declare function trigger:after-copy-collection($new-uri as xs:anyURI, $uri as xs:anyURI) {" +
        	"trigger:logEvent('after', 'copy', 'collection', $new-uri)" +
        "};" +
        "" +
        "declare function trigger:before-move-collection($uri as xs:anyURI, $new-uri as xs:anyURI) {" +
        	"trigger:logEvent('before', 'move', 'collection', $uri)" +
        "};" +
        "" +
        "declare function trigger:after-move-collection($new-uri as xs:anyURI, $uri as xs:anyURI) {" +
        	"trigger:logEvent('after', 'move', 'collection', $new-uri)" +
        "};" +
        "" +
        "declare function trigger:before-delete-collection($uri as xs:anyURI) {" +
        	"trigger:logEvent('before', 'delete', 'collection', $uri)" +
        "};" +
        "" +
        "declare function trigger:after-delete-collection($uri as xs:anyURI) {" +
        	"trigger:logEvent('after', 'delete', 'collection', $uri)" +
        "};" +
        "" + //DOCUMENT EVENTS
        "declare function trigger:before-create-document($uri as xs:anyURI) {" +
        	"trigger:logEvent('before', 'create', 'document', $uri)" +
        "};" +
        "" +
        "declare function trigger:after-create-document($uri as xs:anyURI) {" +
        	"trigger:logEvent('after', 'create', 'document', $uri)" +
        "};" +
        "" +
        "declare function trigger:before-update-document($uri as xs:anyURI) {" +
        	"trigger:logEvent('before', 'update', 'document', $uri)" +
        "};" +
        "" +
        "declare function trigger:after-update-document($uri as xs:anyURI) {" +
        	"trigger:logEvent('after', 'update', 'document', $uri)" +
        "};" +
        "" +
        "declare function trigger:before-copy-document($uri as xs:anyURI, $new-uri as xs:anyURI) {" +
        	"trigger:logEvent('before', 'copy', 'document', $uri)" +
        "};" +
        "" +
        "declare function trigger:after-copy-document($new-uri as xs:anyURI, $uri as xs:anyURI) {" +
        	"trigger:logEvent('after', 'copy', 'document', $new-uri)" +
        "};" +
        "" +
        "declare function trigger:before-move-document($uri as xs:anyURI, $new-uri as xs:anyURI) {" +
        	"trigger:logEvent('before', 'move', 'document', $uri)" +
        "};" +
        "" +
        "declare function trigger:after-move-document($new-uri as xs:anyURI, $uri as xs:anyURI) {" +
        	"trigger:logEvent('after', 'move', 'document', $new-uri)" +
        "};" +
        "" +
        "declare function trigger:before-delete-document($uri as xs:anyURI) {" +
        	"trigger:logEvent('before', 'delete', 'document', $uri)" +
        "};" +
        "" +
        "declare function trigger:after-delete-document($uri as xs:anyURI) {" +
        	"trigger:logEvent('after', 'delete', 'document', $uri)" +
        "};" +
        "";

    private static Collection testCollection;

      /** XQuery module implementing the invalid trigger under test */
    private final static String INVALID_MODULE =
    	"module namespace log='log'; " +
    	"import module namespace xmldb='http://exist-db.org/xquery/xmldb'; " +
    	"declare variable $log:type external;" +
    	"declare variable $log:collection external;" +
    	"declare variable $log:uri external;" +
    	"declare variable $log:event external;" +
    	"declare function log:log($id as xs:string?) {" +
    	"   undeclared-function-causes-trigger-error()" +
        "};";
    
    private final static String EVENTS = "/events/event";

    private final static String BEFORE = EVENTS+"[@type = 'before']";
    private final static String AFTER = EVENTS+"[@type = 'after']";

    private final static String CREATE = "[@event = 'create']";
    private final static String UPDATE = "[@event = 'update']";
    private final static String COPY   = "[@event = 'copy']";
    private final static String MOVE   = "[@event = 'move']";
    private final static String DELETE = "[@event = 'delete']";

    private final static String COLLECTION = "[@object-type = 'collection']";
    private final static String DOCUMENT = "[@object-type = 'document']";
    
    private final static String testCollectionURI = "[uri/text() = '/db/testXQueryTrigger/test']";
    private final static String testDstCollectionURI = "[uri/text() = '/db/testXQueryTrigger/test-dst']";
    private final static String testDstTestCollectionURI = "[uri/text() = '/db/testXQueryTrigger/test-dst/test']";

    private final static String documentURI = "[uri/text() = '/db/testXQueryTrigger/test.xml']";
    private final static String binaryURI = "[uri/text() = '/db/testXQueryTrigger/1x1.gif']";

    /** create "log" document that will be updated by the trigger,
     * and store the XQuery module implementing the trigger under test */
    @Before
    public void setup() throws XMLDBException {
        final CollectionManagementService service = (CollectionManagementService) existEmbeddedServer.getRoot()
                .getService("CollectionManagementService", "1.0");
        testCollection = service.createCollection(TEST_COLLECTION);
        assertNotNull(testCollection);

        final XMLResource doc = (XMLResource) testCollection.createResource(LOG_NAME, "XMLResource" );
        doc.setContent(EMPTY_LOG);
        testCollection.storeResource(doc);

        final BinaryResource module = (BinaryResource) testCollection.createResource(MODULE_NAME, "BinaryResource" );
        ((EXistResource)module).setMimeType("application/xquery");
        module.setContent(MODULE.getBytes());
        testCollection.storeResource(module);
    }

    @After
    public void cleanup() throws XMLDBException {
        final CollectionManagementService service = (CollectionManagementService) existEmbeddedServer.getRoot()
                .getService("CollectionManagementService", "1.0");
        service.removeCollection(TEST_COLLECTION);

        testCollection = null;
    }

    /** test a trigger fired by storing a new Document  */
    @Test
    public void documentCreate() throws XMLDBException {
        // configure the Collection with the trigger under test
        final IndexQueryService idxConf = (IndexQueryService)
        testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);

        // this will fire the trigger
        final XMLResource doc = (XMLResource) testCollection.createResource(DOCUMENT_NAME, "XMLResource" );
        doc.setContent(DOCUMENT_CONTENT);
        testCollection.storeResource(doc);

        // remove the trigger for the Collection under test
        idxConf.configureCollection(EMPTY_COLLECTION_CONFIG);

        final XPathQueryService service = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");

        ResourceSet result = service.query(BEFORE+CREATE+DOCUMENT+documentURI);
        assertEquals(1, result.getSize());

        result = service.query(AFTER+CREATE+DOCUMENT+documentURI);
        assertEquals(1, result.getSize());

        result = service.query(EVENTS);
        assertEquals(2, result.getSize());

        //TODO: document itself
//	        result = service.query(afterCreate+objDocument+documentURI+"/document/test");
//	        assertEquals(1, result.getSize());
//	        assertXMLEqual(DOCUMENT_CONTENT, ((XMLResource)result.getResource(0)).getContent().toString());
    }

    /** test a trigger fired by a Document Update */
    @Test
    public void documentUpdate() throws XMLDBException {
        final IndexQueryService idxConf = (IndexQueryService)
            testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);

        final XMLResource doc = (XMLResource) testCollection.createResource(DOCUMENT_NAME, "XMLResource" );
        doc.setContent(DOCUMENT_CONTENT);
        testCollection.storeResource(doc);

        //TODO : trigger UPDATE events !
        final XUpdateQueryService update = (XUpdateQueryService) testCollection.getService("XUpdateQueryService", "1.0");
        update.updateResource(DOCUMENT_NAME, DOCUMENT_UPDATE);

        idxConf.configureCollection(EMPTY_COLLECTION_CONFIG);

        final XPathQueryService service = (XPathQueryService) testCollection
            .getService("XPathQueryService", "1.0");
        // this is necessary to compare with MODIFIED_DOCUMENT_CONTENT ; TODO better compare with XML diff tool
        service.setProperty(OutputKeys.INDENT, "no");

        ResourceSet result = service.query(BEFORE+CREATE+DOCUMENT+documentURI);
        assertEquals(1, result.getSize());

        result = service.query(AFTER+CREATE+DOCUMENT+documentURI);
        assertEquals(1, result.getSize());

        result = service.query(BEFORE+UPDATE+DOCUMENT+documentURI);
        assertEquals(1, result.getSize());

        result = service.query(AFTER+UPDATE+DOCUMENT+documentURI);
        assertEquals(1, result.getSize());

        result = service.query(EVENTS);
        assertEquals(4, result.getSize());

	        //TODO: document itself
//	        result = service.query("/events/event[@id = 'trigger2']/document/test");
//	        assertEquals(2, result.getSize());
//	        assertXMLEqual(DOCUMENT_CONTENT, result.getResource(0).getContent().toString());
//	        assertXMLEqual(MODIFIED_DOCUMENT_CONTENT, result.getResource(1).getContent().toString());
    }

    /** test a trigger fired by a Document Delete */
    @Test
    public void documentDelete() throws XMLDBException {
        final IndexQueryService idxConf = (IndexQueryService)
            testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);

        final XMLResource doc = (XMLResource) testCollection.createResource(DOCUMENT_NAME, "XMLResource" );
            doc.setContent(DOCUMENT_CONTENT);
        testCollection.storeResource(doc);

        testCollection.removeResource(testCollection.getResource(DOCUMENT_NAME));

        idxConf.configureCollection(EMPTY_COLLECTION_CONFIG);

        final XPathQueryService service = (XPathQueryService) testCollection
            .getService("XPathQueryService", "1.0");

        service.setProperty(OutputKeys.INDENT, "no");

        ResourceSet result = service.query(BEFORE+CREATE+DOCUMENT+documentURI);
        assertEquals(1, result.getSize());

        result = service.query(AFTER+CREATE+DOCUMENT+documentURI);
        assertEquals(1, result.getSize());

        result = service.query(BEFORE+DELETE+DOCUMENT+documentURI);
        assertEquals(1, result.getSize());

        result = service.query(AFTER+DELETE+DOCUMENT+documentURI);
        assertEquals(1, result.getSize());

        result = service.query(EVENTS);
        assertEquals(4, result.getSize());

        //TODO: document itself
//	        result = service.query("/events/event[@id = 'trigger3']/document/test");
//	        assertEquals(1, result.getSize());
//	        assertXMLEqual(MODIFIED_DOCUMENT_CONTENT, result.getResource(0).getContent().toString());
    }

	/** test a trigger fired by creating a new Binary Document  */
    @Test
    public void documentBinaryCreate() throws XMLDBException {
        // configure the Collection with the trigger under test
        final IndexQueryService idxConf = (IndexQueryService)
            testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);

        // this will fire the trigger
        final Resource res = testCollection.createResource(BINARY_DOCUMENT_NAME, "BinaryResource");
        Base64Decoder dec = new Base64Decoder();
        dec.translate(BINARY_DOCUMENT_CONTENT);
        res.setContent(dec.getByteArray());
        testCollection.storeResource(res);

        // remove the trigger for the Collection under test
        idxConf.configureCollection(EMPTY_COLLECTION_CONFIG);

        final XPathQueryService service = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");
        //TODO : understand why it is necessary !
        service.setProperty(OutputKeys.INDENT, "no");

        ResourceSet result = service.query(BEFORE+CREATE+DOCUMENT+binaryURI);
        assertEquals(1, result.getSize());

        result = service.query(AFTER+CREATE+DOCUMENT+binaryURI);
        assertEquals(1, result.getSize());

        result = service.query(EVENTS);
        assertEquals(2, result.getSize());

	        //TODO: document itself
//	        result = service.query("/events/event[@id = 'trigger1'][@type = 'finish'][collection = '" + DBBroker.ROOT_COLLECTION +  "/" + TEST_COLLECTION + "'][uri = '" + DBBroker.ROOT_COLLECTION +  "/" + TEST_COLLECTION + "/" + BINARY_DOCUMENT_NAME + "'][event = 'CREATE-DOCUMENT']/document");
//	        assertEquals(1, result.getSize());
//	        assertEquals("<document>" + BINARY_DOCUMENT_CONTENT + "</document>", result.getResource(0).getContent().toString());
    }

    /** test a trigger fired by a Binary Document Delete */
    @Test
    public void documentBinaryDelete() throws XMLDBException {
        final IndexQueryService idxConf = (IndexQueryService)
            testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);

        // this will fire the trigger
        final Resource res = testCollection.createResource(BINARY_DOCUMENT_NAME, "BinaryResource");
        final Base64Decoder dec = new Base64Decoder();
        dec.translate(BINARY_DOCUMENT_CONTENT);
        res.setContent(dec.getByteArray());
        testCollection.storeResource(res);

        testCollection.removeResource(testCollection.getResource(BINARY_DOCUMENT_NAME));

        idxConf.configureCollection(EMPTY_COLLECTION_CONFIG);

        final XPathQueryService service = (XPathQueryService) testCollection
            .getService("XPathQueryService", "1.0");

        service.setProperty(OutputKeys.INDENT, "no");

        ResourceSet result = service.query(BEFORE+CREATE+DOCUMENT+binaryURI);
        assertEquals(1, result.getSize());

        result = service.query(AFTER+CREATE+DOCUMENT+binaryURI);
        assertEquals(1, result.getSize());

        result = service.query(BEFORE+DELETE+DOCUMENT+binaryURI);
        assertEquals(1, result.getSize());

        result = service.query(AFTER+DELETE+DOCUMENT+binaryURI);
        assertEquals(1, result.getSize());

        result = service.query(EVENTS);
        assertEquals(4, result.getSize());

	        //TODO: document itself
//	        result = service.query("/events/event[@id = 'trigger3'][@type = 'prepare'][collection = '" + DBBroker.ROOT_COLLECTION +  "/" + TEST_COLLECTION + "'][uri = '" + DBBroker.ROOT_COLLECTION +  "/" + TEST_COLLECTION + "/" + BINARY_DOCUMENT_NAME + "'][event = 'DELETE-DOCUMENT']/document");
//	        assertEquals(1, result.getSize());
//	        assertEquals("<document>" + BINARY_DOCUMENT_CONTENT + "</document>", result.getResource(0).getContent().toString());
    }

    /** test a trigger fired by a Collection manipulations */
    @Test
    public void collectionCreate() throws XMLDBException {
        final IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);

        final CollectionManagementService service = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        final Collection collection = service.createCollection("test");
        assertNotNull(collection);

        // remove the trigger for the Collection under test
        idxConf.configureCollection(EMPTY_COLLECTION_CONFIG);

        final XPathQueryService query = (XPathQueryService) existEmbeddedServer.getRoot().getService("XPathQueryService", "1.0");

        ResourceSet result = query.query(BEFORE+CREATE+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = query.query(AFTER+CREATE+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = query.query(EVENTS);
        assertEquals(2, result.getSize());
    }

    /** test a trigger fired by a Collection manipulations */
    @Test
    public void collectionCopy() throws XMLDBException, URISyntaxException {
        final IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);

        final XmldbURI srcURI = XmldbURI.xmldbUriFor("/db/testXQueryTrigger/test");
        final XmldbURI dstURI = XmldbURI.xmldbUriFor("/db/testXQueryTrigger/test-dst");

        final EXistCollectionManagementService service = (EXistCollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        final Collection src = service.createCollection("test");
        assertNotNull(src);

        final Collection dst = service.createCollection("test-dst");
        assertNotNull(dst);

        service.copy(srcURI, dstURI, null);

        // remove the trigger for the Collection under test
        idxConf.configureCollection(EMPTY_COLLECTION_CONFIG);

        ResourceSet result = existEmbeddedServer.executeQuery(BEFORE+CREATE+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(AFTER+CREATE+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(BEFORE+CREATE+COLLECTION+testDstCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(AFTER+CREATE+COLLECTION+testDstCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(BEFORE+COPY+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(AFTER+COPY+COLLECTION+testDstTestCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(EVENTS);
        assertEquals(6, result.getSize());
    }

    /** test a trigger fired by a Collection manipulations */
    @Test
    public void collectionMove() throws XMLDBException, URISyntaxException {
        final IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);

        final XmldbURI srcURI = XmldbURI.xmldbUriFor("/db/testXQueryTrigger/test");
        final XmldbURI dstURI = XmldbURI.xmldbUriFor("/db/testXQueryTrigger/test-dst");

        final EXistCollectionManagementService service = (EXistCollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        final Collection src = service.createCollection("test");
        assertNotNull(src);

        final Collection dst = service.createCollection("test-dst");
        assertNotNull(dst);

        service.move(srcURI, dstURI, null);

        // remove the trigger for the Collection under test
        idxConf.configureCollection(EMPTY_COLLECTION_CONFIG);

        ResourceSet result = existEmbeddedServer.executeQuery(BEFORE+CREATE+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(AFTER+CREATE+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(BEFORE+CREATE+COLLECTION+testDstCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(AFTER+CREATE+COLLECTION+testDstCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(BEFORE+MOVE+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(AFTER+MOVE+COLLECTION+testDstTestCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(EVENTS);
        assertEquals(6, result.getSize());
    }

    /** test a trigger fired by a Collection manipulations */
    @Test
    public void collectionDelete() throws XMLDBException {
        final IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);

        final CollectionManagementService service = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        final Collection collection = service.createCollection("test");
        assertNotNull(collection);

        service.removeCollection("test");

        // remove the trigger for the Collection under test
        idxConf.configureCollection(EMPTY_COLLECTION_CONFIG);

        ResourceSet result = existEmbeddedServer.executeQuery(BEFORE+CREATE+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(AFTER+CREATE+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(BEFORE+DELETE+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery(AFTER+DELETE+COLLECTION+testCollectionURI);
        assertEquals(1, result.getSize());

        result = existEmbeddedServer.executeQuery("/events/event");
        assertEquals(4, result.getSize());
    }

    @Test
    public void storeDocument_invalidTriggerForPrepare() throws XMLDBException {
        final BinaryResource invalidModule = (BinaryResource) testCollection.createResource(MODULE_NAME, "BinaryResource" );
        ((EXistResource)invalidModule).setMimeType("application/xquery");
        invalidModule.setContent(INVALID_MODULE.getBytes());
        testCollection.storeResource(invalidModule);

        // configure the Collection with the trigger under test
        final IndexQueryService idxConf = (IndexQueryService)testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);

        final int max_store_attempts = 10;
        int count_prepare_exceptions = 0;
        for(int i = 0; i < max_store_attempts; i++) {
            try {
                // this will fire the trigger
                final XMLResource doc = (XMLResource) testCollection.createResource(DOCUMENT_NAME, "XMLResource");
                doc.setContent(DOCUMENT_CONTENT);
                testCollection.storeResource(doc);
            } catch(XMLDBException xdbe) {
               if(xdbe.getCause() instanceof TriggerException) {
                   if(xdbe.getCause().getMessage().equals(XQueryTrigger.PREPARE_EXCEPTION_MESSAGE)) {
                        count_prepare_exceptions++;
                   }
               }
            }
        }

        assertEquals(max_store_attempts, count_prepare_exceptions);
    }
}