package org.exist.jboss;

import org.jboss.naming.NonSerializableFactory;
import org.jboss.system.ServiceMBeanSupport;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Arrays;

/**
 * This serice depends on eXists and exposes the XML:DB api
 * We bind the service to JNDI for convenience reasons only
 * we could just as well go through the jmx spine instead.
 *
 * @author Per Nyfelt
 */
public class XmlDbService extends ServiceMBeanSupport implements XmlDbServiceMBean {

    private String baseCollectionURI;
    private String driver;
    private Collection baseCollection;

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getBaseCollectionURI() {
        return baseCollectionURI;
    }

    public void setBaseCollectionURI(String baseCollectionURI) {
        this.baseCollectionURI = baseCollectionURI;
    }

    public Collection getBaseCollection() throws XMLDBException {
        baseCollection = DatabaseManager.getCollection(baseCollectionURI);
        return baseCollection;
    }


    protected void startService() throws Exception {

        Context context = new InitialContext();
        Class c = Class.forName(driver);
        Database database = (Database) c.newInstance();
        DatabaseManager.registerDatabase(database);
        database.setProperty("create-database", "true");

        baseCollection = getBaseCollection();
        baseCollection.setProperty("encoding", "ISO-8859-1");

        log.debug("Got base Collection");

        NonSerializableFactory.rebind(context, this.getClass().getName(), this);
        String[] collections = baseCollection.listChildCollections();
        log.debug("ChildCollections " + Arrays.asList(collections));
    }

    protected void stopService() throws Exception {
        NonSerializableFactory.unbind(this.getClass().getName());
        if (baseCollection != null) {
            baseCollection.close();
            log.debug("Closed base (db) collection");
        }
    }

    public static CollectionManagementService getCollectionManagementService(Collection parentCollection) throws XMLDBException {
        CollectionManagementService service = (CollectionManagementService) parentCollection.getService("CollectionManagementService", "1.0");
        return service;
    }
}
