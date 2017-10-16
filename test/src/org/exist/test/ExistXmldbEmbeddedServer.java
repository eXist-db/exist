package org.exist.test;

import org.exist.TestUtils;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.EXistCollection;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xmldb.XmldbURI;
import org.junit.rules.ExternalResource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import javax.xml.transform.OutputKeys;
import java.util.Map;

import static org.exist.repo.AutoDeploymentTrigger.AUTODEPLOY_PROPERTY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Exist embedded XML:DB Server Rule for JUnit
 */
public class ExistXmldbEmbeddedServer extends ExternalResource {

    private final boolean asGuest;
    private final boolean disableAutoDeploy;

    private String prevAutoDeploy = "off";
    private Database database = null;
    private Collection root = null;
    private EXistXQueryService xpathQueryService = null;

    public ExistXmldbEmbeddedServer() {
        this(false, false);
    }

    /**
     * @param asGuest Use the guest account, default is the admin account
     */
    public ExistXmldbEmbeddedServer(final boolean asGuest) {
        this(asGuest, false);
    }

    /**
     * @param asGuest Use the guest account, default is the admin account
     * @param disableAutoDeploy Whether auto-deployment of XARs should be disabled
     */
    public ExistXmldbEmbeddedServer(final boolean asGuest, final boolean disableAutoDeploy) {
        this.asGuest = asGuest;
        this.disableAutoDeploy = disableAutoDeploy;
    }

    @Override
    protected void before() throws Throwable {
        startDb();
        super.before();
    }

    private void startDb() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        if (database == null) {

            if(disableAutoDeploy) {
                this.prevAutoDeploy = System.getProperty(AUTODEPLOY_PROPERTY, "off");
                System.setProperty(AUTODEPLOY_PROPERTY, "off");
            }

            // initialize driver
            final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            if (asGuest) {
                root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.GUEST_DB_USER, TestUtils.GUEST_DB_PWD);
            } else {
                root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
            }
            xpathQueryService = (EXistXQueryService) root.getService("XQueryService", "1.0");
        } else {
            throw new IllegalStateException("ExistXmldbEmbeddedServer already running");
        }
    }

    public ResourceSet executeQuery(final String query) throws XMLDBException {
        final CompiledExpression compiledQuery = xpathQueryService.compile(query);
        final ResourceSet result = xpathQueryService.execute(compiledQuery);
        return result;
    }

    public ResourceSet executeQuery(final String query, final Map<String, Object> externalVariables)
            throws XMLDBException {
        for (final Map.Entry<String, Object> externalVariable : externalVariables.entrySet()) {
            xpathQueryService.declareVariable(externalVariable.getKey(), externalVariable.getValue());
        }
        final CompiledExpression compiledQuery = xpathQueryService.compile(query);
        final ResourceSet result = xpathQueryService.execute(compiledQuery);
        xpathQueryService.clearVariables();
        return result;
    }

    public String executeOneValue(final String query) throws XMLDBException {
        final ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());
        return results.getResource(0).getContent().toString();
    }

    public Collection createCollection(final Collection collection, final String collectionName) throws XMLDBException {
        final CollectionManagementService collectionManagementService =
                (CollectionManagementService) collection.getService("CollectionManagementService", "1.0");
        Collection newCollection = collection.getChildCollection(collectionName);
        if (newCollection == null) {
            collectionManagementService.createCollection(collectionName);
        }

        final XmldbURI uri = XmldbURI.LOCAL_DB_URI.resolveCollectionPath(((EXistCollection) collection).getPathURI().append(collectionName));
        if (asGuest) {
            newCollection = DatabaseManager.getCollection(uri.toString(), TestUtils.GUEST_DB_USER, TestUtils.GUEST_DB_PWD);
        } else {
            newCollection = DatabaseManager.getCollection(uri.toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        }

        return newCollection;
    }

    public static void storeResource(final Collection collection, final String documentName, final byte[] content)
            throws XMLDBException {
        final MimeType mime = MimeTable.getInstance().getContentTypeFor(documentName);
        final String type = mime.isXMLType() ? XMLResource.RESOURCE_TYPE : BinaryResource.RESOURCE_TYPE;
        final Resource resource = collection.createResource(documentName, type);
        resource.setContent(content);
        collection.storeResource(resource);
    }

    public static String getXMLResource(final Collection collection, final String resource) throws XMLDBException {
        collection.setProperty(OutputKeys.INDENT, "yes");
        collection.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
        collection.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
        final XMLResource res = (XMLResource) collection.getResource(resource);
        return res.getContent().toString();
    }

    public Collection getRoot() {
        return root;
    }

    public void restart() throws ClassNotFoundException, InstantiationException, XMLDBException, IllegalAccessException {
        stopDb();
        startDb();
    }

    @Override
    protected void after() {
        try {
            stopDb();
        } catch (final XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        super.after();
    }

    private void stopDb() throws XMLDBException {
        if (database != null) {
            root.close();

            DatabaseManager.deregisterDatabase(database);
            final DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            dim.shutdown();


            // clear instance variables
            xpathQueryService = null;
            root = null;
            database = null;

            if(disableAutoDeploy) {
                //set the autodeploy trigger enablement back to how it was before this test class
                System.setProperty(AUTODEPLOY_PROPERTY, this.prevAutoDeploy);
            }

        } else {
            throw new IllegalStateException("ExistXmldbEmbeddedServer already stopped");
        }
    }
}
