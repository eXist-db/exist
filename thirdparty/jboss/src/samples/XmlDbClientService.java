package samples;

import org.exist.jboss.XmlDbService;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Resource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.jboss.system.ServiceMBeanSupport;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Arrays;

/**
 * This class represent a server component living on the JBoss server e.g a Servler
 * an EJB, another JMX MBean or whatever. It might also be possible to access the database
 * remotely but this has not been tested at all
 *
 * @author Per Nyfelt
 */
public class XmlDbClientService extends ServiceMBeanSupport implements XmlDbClientServiceMBean {

    private static final String INVENTORY_NAME = "inventory";
    private Collection inventory;

    public XmlDbClientService() {
    }

    protected void startService() throws Exception {
        super.startService();
    }

    protected void stopService() throws Exception {
        super.stopService();
    }

    public String useXmlDbService() {
        try {
            verifyInventory();

            String result = "ChildCollection: " + Arrays.asList(inventory.listChildCollections());
            result += "Resources: " + Arrays.asList(inventory.listResources());
            if (inventory == null) {
                String msg = "Whoa!!! No Inventory Collection found, this should have been created by the inventory service";
                System.out.println(msg);
                return msg;
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return e.toString();
        }
    }

    public String addXMLforResourceName(String xml, String resourceName) {
        try {
            verifyInventory();

            Resource res = inventory.getResource(resourceName);
            if (res == null) {
                log.info("creating resource");
                res = inventory.createResource(resourceName, XMLResource.RESOURCE_TYPE);
            }
            log.info("Storing xml content");
            res.setContent(xml);
            log.info("Storing resource to eXist");
            inventory.storeResource(res);
            inventory.close();
            return resourceName + " stored successfully!";
        } catch (Exception e) {
            e.printStackTrace();
            return e.toString();
        }
    }

    public String fetchXMLforResurceName(String resourceName) {
        try {
            verifyInventory();

            Resource res = inventory.getResource(resourceName);
            if (res == null) {
                return "resource " + resourceName + " was not found";
            }
            log.info("Getting xml content");
            return (String)res.getContent();
        } catch (Exception e) {
            e.printStackTrace();
            return e.toString();
        }
    }

    private void verifyInventory() throws NamingException, XMLDBException {
        Context ctx = new InitialContext();
        XmlDbService xmlDbService = (XmlDbService) ctx.lookup(XmlDbService.class.getName());
        Collection baseCol = xmlDbService.getBaseCollection();
        log.info("Got base Collection " + baseCol);

        inventory = baseCol.getChildCollection(INVENTORY_NAME);
        if (inventory == null) {
            log.info("Creating a new Collection for " + INVENTORY_NAME);
            CollectionManagementService mgtService = XmlDbService.getCollectionManagementService(baseCol);
            log.info("got CollectionManagementService");
            inventory = mgtService.createCollection(INVENTORY_NAME);

            log.info("Collection " + INVENTORY_NAME + " created.");
        } else {
            log.info("Found existing inventory collection ");
        }
    }
}
