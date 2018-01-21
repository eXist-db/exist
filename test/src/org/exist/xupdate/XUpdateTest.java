package org.exist.xupdate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.exist.TestUtils;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.xmldb.DatabaseInstanceManager;

import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.*;
import org.junit.runners.Parameterized.Parameters;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.NodeIterator;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * @author berlinge-to
 */
@RunWith(Parameterized.class)
public class XUpdateTest {

    //TODO should not execute as 'admin' user
    //also additional tests needed to verify update permissions
    
    @Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {"append", "address.xml"},
            {"insertafter", "address.xml"},
            {"insertbefore", "address.xml"},
            {"remove", "address.xml"},
            {"update", "address.xml"},
            {"append_attribute", "address.xml"},
            {"append_child", "address.xml"},
            {"insertafter_big", "address_big.xml"},
            {"conditional", "address.xml"},
            {"variables", "address.xml"},
            {"replace", "address.xml"},
            {"whitespace", "address.xml"},
            {"namespaces", "namespaces.xml"},
            
            /* TODO Added by Geoff Shuetrim (geoff@galexy.net) on 15 July 2006
            to highlight that root element renaming does not currently succeed,
            resulting instead in a null pointer exception because the renaming
            relies upon obtaining the parent element of the element being
            renamed and this is null for the root element. */
            {"rename_root_element", "address.xml"},
            
            /* TODO Added by Geoff Shuetrim (geoff@galexy.net) on 15 July 2006
            to highlight that renaming of an element fails when the renaming also
            involves a change of namespace */
            {"rename_including_namespace", "namespaces.xml"}
        });
    }

    @Parameter
    public String testName;
    
    @Parameter(value = 1)
    public String sourceFile;

    private final static String URI = XmldbURI.LOCAL_DB;
    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    private final static String XUPDATE_COLLECTION = "xupdate_tests";

    private final static String MODIFICATION_DIR_NAME  = "modifications";
    private final static String RESULT_DIR_NAME = "results";
    private final static String SOURCE_DIR_NAME = "input";
    private final static String XUPDATE_FILE = "xu.xml";       // xlm document name in eXist

    private Collection col = null;

    @Test
    public void xupdate() throws Exception {
        
        //skip tests from Geoff Shuetrim (see above!)
        Assume.assumeThat(testName, not(anyOf(equalTo("rename_root_element"), equalTo("rename_including_namespace"))));
        
        addDocument(sourceFile);

        //update input xml file
        final Path modFile = getRelFile(MODIFICATION_DIR_NAME + "/" + testName + ".xml");
        Document xupdateResult = updateDocument(modFile);
        removeWhiteSpace(xupdateResult);

        //Read reference xml file
        DocumentBuilderFactory parserFactory
                = DocumentBuilderFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        DocumentBuilder builder = parserFactory.newDocumentBuilder();
        final Path resultFile = getRelFile(RESULT_DIR_NAME + "/" + testName + ".xml");
        Document referenceXML = builder.parse(resultFile.toFile());
        removeWhiteSpace(referenceXML);

        //compare
        new CompareDocuments().compare(referenceXML, xupdateResult, "", false);

        removeDocument();
    }

    /*
     * helperfunctions
     * 
     */
    public void addDocument(final String sourceFile) throws XMLDBException, IOException, URISyntaxException {
        final Path f = getRelFile(SOURCE_DIR_NAME + "/" + sourceFile);

        final XMLResource document = (XMLResource) col.createResource(XUPDATE_FILE, "XMLResource");
        document.setContent(f);
        col.storeResource(document);
    }

    public Path getRelFile(final String relPath) throws IOException, URISyntaxException {
        final URL url = getClass().getResource(relPath);
        if(url == null) {
            throw new IOException("Can't find file: " + relPath);
        }

        final Path f = Paths.get(url.toURI());
        if (!Files.isReadable(f)) {
            throw new IOException("Can't read file: " + url);
        }

        return f;
    }

    public void removeDocument() throws XMLDBException {
        final Resource document = col.getResource(XUPDATE_FILE);
        col.removeResource(document);
    }

    private Document updateDocument(final Path updateFile) throws XMLDBException, IOException, ParserConfigurationException, SAXException {
        final XUpdateQueryService service = (XUpdateQueryService) col.getService("XUpdateQueryService", "1.0");

        // Read XUpdate-Modifcations
        final String xUpdateModifications = new String(Files.readAllBytes(updateFile), UTF_8);
        //

        service.update(xUpdateModifications);

		//col.setProperty("pretty", "true");
        //col.setProperty("encoding", "UTF-8");
        final XMLResource ret = (XMLResource) col.getResource(XUPDATE_FILE);
        final String xmlString = ((String) ret.getContent());

		// convert xml string to dom
        // todo: make it nicer
        final DocumentBuilderFactory parserFactory = DocumentBuilderFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        try(final InputStream is = new ByteArrayInputStream(xmlString.getBytes())) {
            final InputSource in = new InputSource(is);
            final DocumentBuilder builder = parserFactory.newDocumentBuilder();
            return builder.parse(in);
        }
    }

    private void removeWhiteSpace(final Document document) {
        final DocumentTraversal dt = (DocumentTraversal) document;
        final NodeIterator nodeIterator = dt.createNodeIterator(document, NodeFilter.SHOW_TEXT, null, true);
        Node node = nodeIterator.nextNode();
        while (node != null) {
            if (node.getNodeValue().trim().compareTo("") == 0) {
                node.getParentNode().removeChild(node);
            }
            node = nodeIterator.nextNode();
        }
    }

    @Before
    public void startup() throws ClassNotFoundException, InstantiationException, IllegalAccessException, XMLDBException {
        final Class<?> cl = Class.forName(DRIVER);
        final Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        col = DatabaseManager.getCollection(URI + "/" + XUPDATE_COLLECTION);
        if (col == null) {
            final Collection root = DatabaseManager.getCollection(URI, "admin", "");
            CollectionManagementService mgtService = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            col = mgtService.createCollection(XUPDATE_COLLECTION);
            final UserManagementService ums = (UserManagementService) col.getService("UserManagementService", "1.0");
            // change ownership to guest
            final Account guest = ums.getAccount("guest");
            ums.chown(guest, guest.getPrimaryGroup());
            ums.chmod(Permission.DEFAULT_COLLECTION_PERM);
        }
    }
    
    @After
    public void shutdown() throws XMLDBException {
        TestUtils.cleanupDB();
        final Collection root = DatabaseManager.getCollection(URI, "admin", "");
        final DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        mgr.shutdown();
    }
}
