package org.exist.test;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.junit.rules.ExternalResource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.exist.xmldb.XQueryService;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Exist embedded XML:DB Server Rule for JUnit
 */
public class ExistXmldbEmbeddedServer extends ExternalResource {

    private final static String ADMIN_DB_USER = "admin";
    private final static String ADMIN_DB_PWD = "";

    private final static String GUEST_DB_USER = "guest";
    private final static String GUEST_DB_PWD = "guest";
    private final boolean asGuest;

    private Database database = null;
    private Collection root = null;
    private XQueryService xpathQueryService = null;

    public ExistXmldbEmbeddedServer() {
        this(false);
    }

    /**
     * @param asGuest Use the guest account, default is the admin account
     */
    public ExistXmldbEmbeddedServer(final boolean asGuest) {
        this.asGuest = asGuest;
    }

    @Override
    protected void before() throws Throwable {
        startDb();
        super.before();
    }

    private void startDb() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        if(database == null) {
            // initialize driver
            final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            if(asGuest) {
                root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, GUEST_DB_USER, GUEST_DB_PWD);
            } else {
                root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, ADMIN_DB_USER, ADMIN_DB_PWD);
            }
            xpathQueryService = (XQueryService) root.getService("XQueryService", "1.0");
        } else {
            throw new IllegalStateException("ExistXmldbEmbeddedServer already running");
        }
    }

    public ResourceSet executeQuery(final String query) throws XMLDBException {
        final CompiledExpression compiledQuery = xpathQueryService.compile(query);
        final ResourceSet result = xpathQueryService.execute(compiledQuery);
        return result;
    }

    public ResourceSet executeQuery(final String query, final Map<String, Object> externalVariables) throws XMLDBException {
        for(final Map.Entry<String, Object> externalVariable : externalVariables.entrySet()) {
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
        } catch(final XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        super.after();
    }

    private void stopDb() throws XMLDBException {
        if(database != null) {
            DatabaseManager.deregisterDatabase(database);
            final DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            dim.shutdown();


            // clear instance variables
            xpathQueryService = null;
            root = null;
            database = null;
        } else {
            throw new IllegalStateException("ExistXmldbEmbeddedServer already stopped");
        }
    }
}
