package org.exist.security;

import java.util.LinkedList;
import org.exist.jetty.JettyStart;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

@RunWith(Parameterized.class)
public class XMLDBSecurityTest {

    private static String DB_DRIVER = "org.exist.xmldb.DatabaseImpl";

    private String baseUri;

    private static JettyStart server;

    public XMLDBSecurityTest(final String baseUri) {
        this.baseUri = baseUri;
    }

    @Parameterized.Parameters
    public static LinkedList<String[]> instances() {
        final LinkedList<String[]> params = new LinkedList<String[]>();
        params.add(new String[] { "xmldb:exist://" });
	    params.add(new String[] { "xmldb:exist://localhost:" + System.getProperty("jetty.port", "8088") + "/xmlrpc" });
        
        return params;
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldCreateCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        final CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");
        cms.createCollection("createdByGuest");
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldAddResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        final Resource resource = test.createResource("createdByGuest", XMLResource.RESOURCE_TYPE);
        resource.setContent("<testMe/>");
        test.storeResource(resource);
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldRemoveCollection() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(baseUri + "/db", "guest", "guest");
        final CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        cms.removeCollection("securityTest1");
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChmodCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(0777);
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChmodResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(resource, 0777);
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChownCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        final Account guest = ums.getAccount("guest");
        // make myself the owner ;-)
        ums.chown(guest, "guest");
    }

    @Test (expected=XMLDBException.class)
    // only the owner or dba can chown a collection or resource
    public void worldChownResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        final Account test2 = ums.getAccount("guest");
        ums.chown(resource, test2, "guest");
    }

    @Test
    public void groupCreateSubColl() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        final CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");
        final Collection newCol = cms.createCollection("createdByTest2");
        assertNotNull(newCol);
    }

    @Test
    public void groupCreateResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        Resource resource = test.createResource("createdByTest2.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<testMe/>");
        test.storeResource(resource);

        resource = test.getResource("createdByTest2.xml");
        assertNotNull(resource);
        assertEquals("<testMe/>", resource.getContent().toString());
    }

    @Test(expected=XMLDBException.class)
    public void groupRemoveCollection_canNotWriteParent() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(baseUri + "/db", "test2", "test2");
        final CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        cms.removeCollection("securityTest1");
    }

    @Test
    public void groupRemoveCollection_canWriteParent() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
        final CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        cms.removeCollection("securityTest1");
    }

    @Test(expected=XMLDBException.class)
    public void groupChmodCollection_asNotOwnerAndNotDBA() throws XMLDBException {

        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // grant myself all rights ;-)
        ums.chmod(07777);
    }

    @Test
    public void groupChmodCollection_asOwner() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(07777);

        assertEquals("rwsrwsrwt", ums.getPermissions(test).toString());
    }

    @Test(expected=XMLDBException.class)
    public void groupChmodResource_asNotOwnerAndNotDBA() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(resource, 0777);
    }

    @Test
    public void groupChmodResource_asOwner() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(resource, 0777);
    }

    @Test(expected=XMLDBException.class)
    // only the owner or admin can chown a collection or resource
    public void groupChownCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        final Account test2 = ums.getAccount("test2");
        ums.chown(test2, "users");
        final Permission perms = ums.getPermissions(test);
        assertEquals("test2", perms.getOwner().getName());
    }

    @Test(expected=XMLDBException.class)
    // only the owner or admin can chown a collection or resource
    public void groupChownResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        final Account test2 = ums.getAccount("test2");
        ums.chown(resource, test2, "users");
    }

    @Test
    public void onlyExecuteRequiredToOpenCollectionContent() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x------");
        test.close();

        DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
    }

    @Test(expected=XMLDBException.class)
    public void cannotOpenCollectionWithoutExecute() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("rw-rw-rw-");
        test.close();

        DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
    }

    @Test
    public void canOpenCollectionWithExecute() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x--x--x");
        test.close();

        DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
    }

    @Test(expected=XMLDBException.class)
    public void cannotOpenRootCollectionWithoutExecute() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("rw-rw-rw-");
        test.close();

        DatabaseManager.getCollection(baseUri + "/db", "test1", "test1");
    }

    @Test
    public void canOpenRootCollectionWithExecute() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x--x--x");
        test.close();

        DatabaseManager.getCollection(baseUri + "/db", "test1", "test1");
    }

    @Test
    public void onlyReadAndExecuteRequiredToListCollectionResources() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("r-x------");

        test.listResources();
    }

    @Test(expected=XMLDBException.class)
    public void cannotListCollectionResourcesWithoutRead() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("-wx-wx-wx");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        test.listResources();
    }

    @Test
    public void onlyReadAndExecuteRequiredToListCollectionSubCollections() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("r-x------");

        test.listChildCollections();
    }

    @Test(expected=XMLDBException.class)
    public void cannotListCollectionSubCollectionsWithoutRead() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("-wx-wx-wx");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        test.listChildCollections();
    }

    @Test
    public void canReadXmlResourceWithOnlyExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.getResource("test.xml");
        assertEquals("<test/>", resource.getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotReadXmlResourceWithoutExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("rw-------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.getResource("test.xml");
        assertEquals("<test/>", resource.getContent());
    }

    @Test
    public void canReadBinaryResourceWithOnlyExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.getResource("test.bin");
        assertArrayEquals("binary-test".getBytes(), (byte[])resource.getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotReadBinaryResourceWithoutExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("rw-------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.getResource("test.bin");
        assertArrayEquals("binary-test".getBytes(), (byte[])resource.getContent());
    }

    @Test
    public void canReadXmlResourceWithOnlyReadPermission() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        Resource resource = test.getResource("test.xml");
        ums.chmod(resource, "r--------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        resource = test.getResource("test.xml");
        assertEquals("<test/>", resource.getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotReadXmlResourceWithoutReadPermission() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        Resource resource = test.getResource("test.xml");
        ums.chmod(resource, "-wx------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        resource = test.getResource("test.xml");
        assertEquals("<test/>", resource.getContent());
    }

    @Test
    public void canReadBinaryResourceWithOnlyReadPermission() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        Resource resource = test.getResource("test.bin");
        ums.chmod(resource, "r--------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        resource = test.getResource("test.bin");
        assertArrayEquals("binary-test".getBytes(), (byte[])resource.getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotReadBinaryResourceWithoutReadPermission() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        Resource resource = test.getResource("test.bin");
        ums.chmod(resource, "-wx------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        resource = test.getResource("test.bin");
        assertArrayEquals("binary-test".getBytes(), (byte[])resource.getContent());
    }

    @Test
    public void canCreateXmlResourceWithOnlyExecuteAndWritePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("-wx------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.createResource("other.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<other/>");
        test.storeResource(resource);
    }

    @Test
    public void canCreateBinaryResourceWithOnlyExecuteAndWritePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("-wx------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.createResource("other.bin", BinaryResource.RESOURCE_TYPE);
        resource.setContent("binary".getBytes());
        test.storeResource(resource);
    }

    @Test
    public void canUpdateXmlResourceWithOnlyExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        Resource resource = test.getResource("test.xml");
        assertEquals("<test/>", resource.getContent());

        //update the resource
        resource.setContent("<testing/>");
        test.storeResource(resource);

        resource = test.getResource("test.xml");
        assertEquals("<testing/>", resource.getContent());
    }

    @Test
    public void canUpdateBinaryResourceWithOnlyExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x------");
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        Resource resource = test.getResource("test.bin");
        assertArrayEquals("binary-test".getBytes(), (byte[])resource.getContent());

        //update the resource
        resource.setContent("testing".getBytes());
        test.storeResource(resource);

        resource = test.getResource("test.bin");
        assertArrayEquals("testing".getBytes(), (byte[])resource.getContent());
    }

    @Test
    public void canExecuteXQueryWithOnlyExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        final String xquery = "<xquery>{ 1 + 1 }</xquery>";
        Resource xqueryResource = test.createResource("test.xquery", BinaryResource.RESOURCE_TYPE);
        xqueryResource.setContent(xquery);
        test.storeResource(xqueryResource);

        ums.chmod("--x------");
        ums.chmod(xqueryResource, "rwx------"); //set execute bit on xquery (its off by default!)
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        xqueryResource = test.getResource("test.xquery");
        assertEquals(xquery, new String((byte[])xqueryResource.getContent()));

        //execute the stored XQuery
        final XPathQueryServiceImpl queryService = (XPathQueryServiceImpl)test.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest1/test.xquery");
        assertEquals("<xquery>2</xquery>", result.getResource(0).getContent());
    }

    /**
     * Note the eventual goal is for XQuery to be executeable in eXist
     * with just the EXECUTE flag set, this however will require some
     * serious refactoring. See my (Adam) posts to exist-open thread entitled
     * '[HEADS-UP] Merge in of Security Branch', most significant
     * messages from 08/02/2012
     */
    @Test
    public void canExecuteXQueryWithOnlyExecuteAndReadPermission() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        final String xquery = "<xquery>{ 1 + 2 }</xquery>";
        Resource xqueryResource = test.createResource("test.xquery", BinaryResource.RESOURCE_TYPE);
        xqueryResource.setContent(xquery);
        test.storeResource(xqueryResource);

        ums.chmod(xqueryResource, "r-x------"); //execute only on xquery
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        xqueryResource = test.getResource("test.xquery");
        assertEquals(xquery, new String((byte[])xqueryResource.getContent()));

        //execute the stored XQuery
        final XPathQueryServiceImpl queryService = (XPathQueryServiceImpl)test.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest1/test.xquery");
        assertEquals("<xquery>3</xquery>", result.getResource(0).getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotExecuteXQueryWithoutExecutePermission() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        final String xquery = "<xquery>{ 1 + 2 }</xquery>";
        Resource xqueryResource = test.createResource("test.xquery", BinaryResource.RESOURCE_TYPE);
        xqueryResource.setContent(xquery);
        test.storeResource(xqueryResource);

        ums.chmod(xqueryResource, "rw-------"); //execute only on xquery
        test.close();

        test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        xqueryResource = test.getResource("test.xquery");
        assertEquals(xquery, new String((byte[])xqueryResource.getContent()));

        //execute the stored XQuery
        final XPathQueryServiceImpl queryService = (XPathQueryServiceImpl)test.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest1/test.xquery");
        assertEquals("<xquery>3</xquery>", result.getResource(0).getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotOpenCollection() throws XMLDBException {
        //check that a user not in the users group (i.e. test3) cannot open the collection /db/securityTest1
        DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test3", "test3");
    }

    @Test
    public void canOpenCollection() throws XMLDBException {
        //check that a user in the users group (i.e. test2) can open the collection /db/securityTest1
        DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");

        //check that any user can open the collection /db/securityTest3
        DatabaseManager.getCollection(baseUri + "/db/securityTest3", "test1", "test1");
        DatabaseManager.getCollection(baseUri + "/db/securityTest3", "test2", "test2");
        DatabaseManager.getCollection(baseUri + "/db/securityTest3", "test3", "test3");
    }

    @Test
    public void copyCollectionWithResource() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest3", "test1", "test1");
        CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //create resourcse owned by "test1", and group "users" in /db/securityTest3/source
        Resource resSource = source.createResource("source1.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);

        resSource = source.createResource("source2.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);

        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(baseUri + "/db/securityTest3", "test3", "test3");
        cms = (CollectionManagementServiceImpl) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");

        final Collection copyOfSource = test.getChildCollection("copy-of-source");

        assertNotNull(copyOfSource);

        assertEquals(2, copyOfSource.listResources().length);
    }


    @Test
    public void copyDocument_doesNotPreservePermissions() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest3", "test1", "test1");
        CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl) test.getService("CollectionManagementService", "1.0");

        //create resource owned by "test1", and group "users" in /db/securityTest3
        final Resource resSource = test.createResource("source.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        test.storeResource(resSource);

        //as the 'test3' user copy the resource
        test = DatabaseManager.getCollection(baseUri + "/db/securityTest3", "test3", "test3");
        cms = (CollectionManagementServiceImpl) test.getService("CollectionManagementService", "1.0");
        cms.copyResource("/db/securityTest3/source.xml", "/db/securityTest3", "copy-of-source.xml");

        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        final Permission permissions = ums.getPermissions(test.getResource("copy-of-source.xml"));

        //resource should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source.xml
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());
    }

    @Test
    public void copyCollection_doesNotPreservePermissions() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest3", "test1", "test1");
        CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(baseUri + "/db/securityTest3", "test3", "test3");
        cms = (CollectionManagementServiceImpl) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");


        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        final Permission permissions = ums.getPermissions(test.getChildCollection("copy-of-source"));

        //collection should be owned by test3:guest, i.e. permissions were not preserved from the test1 users collection /db/securityTest3/source
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());
    }

    @Test
    public void copyCollectionWithResource_doesNotPreservePermissions() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest3", "test1", "test1");
        CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //create resource owned by "test1", and group "users" in /db/securityTest3/source
        final Resource resSource = source.createResource("source.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);

        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(baseUri + "/db/securityTest3", "test3", "test3");
        cms = (CollectionManagementServiceImpl) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");


        UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        final Collection copyOfSource = test.getChildCollection("copy-of-source");
        Permission permissions = ums.getPermissions(copyOfSource);

        //collection should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());

        ums = (UserManagementService) copyOfSource.getService("UserManagementService", "1.0");
        permissions = ums.getPermissions(copyOfSource);

        //resource in collection should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source.xml
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());
    }

    /**
     * 1) Sets '/db' to rwxr-xr-x (0755)
     * 2) Adds the Group 'users'
     * 3) Adds the User 'test1' with password 'test1' and set's their primary group to 'users'
     * 4) Adds the User 'test2' with password 'test2' and set's their primary group to 'users'
     * 5) Adds the User 'test3' with password 'test3' and set's their primary group to 'guest'
     * 6) Creates the Collection '/db/securityTest1' owned by 'test1':'users' with permissions rwxrwx--- (0770)
     * 7) Creates the XML resource '/db/securityTest1/test.xml' owned by 'test1':'users' with permissions rwxrwx--- (0770)
     * 8) Creates the Binary resource '/db/securityTest1/test.bin' owned by 'test1':'users' with permissions rwxrwx--- (0770)
     * 9) Creates the Collection '/db/securityTest2' owned by 'test1':'users' with permissions rwxrwxr-x (0775)
     * 10) Creates the Collection '/db/securityTest3' owned by 'test3':'guest' with permissions rwxrwxrwx (0777)
     */
    @Before
    public void setup() {
        try {
            final Collection root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
            UserManagementService ums = (UserManagementService) root.getService("UserManagementService", "1.0");

            ums.chmod("rwxr-xr-x"); //ensure /db is always 755

            //remove accounts 'test1', 'test2' and 'test3'
            removeAccounts(ums, new String[]{"test1", "test2", "test3"});

            //remove group 'users'
            removeGroups(ums, new String[]{"users"});

            final Group group = new GroupAider("exist", "users");
            ums.addGroup(group);

            UserAider user = new UserAider("test1", group);
            user.setPassword("test1");
            ums.addAccount(user);

            user = new UserAider("test2", group);
            user.setPassword("test2");
            ums.addAccount(user);

            user = new UserAider("test3", ums.getGroup("guest"));
            user.setPassword("test3");
            ums.addAccount(user);

            // create a collection /db/securityTest1 as user "test1"
            CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
            Collection test = cms.createCollection("securityTest1");
            ums = (UserManagementService) test.getService("UserManagementService", "1.0");
            //change ownership to test1
            final Account test1 = ums.getAccount("test1");
            ums.chown(test1, "users");
            // full permissions for user and group, none for world
            ums.chmod(0770);

            test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

            Resource resource = test.createResource("test.xml", XMLResource.RESOURCE_TYPE);
            resource.setContent("<test/>");
            test.storeResource(resource);
            ums.chmod(resource, 0770);

            resource = test.createResource("test.bin", BinaryResource.RESOURCE_TYPE);
            resource.setContent("binary-test".getBytes());
            test.storeResource(resource);
            ums.chmod(resource, 0770);

            // create a collection /db/securityTest2 as user "test1"
            cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
            Collection testCol2 = cms.createCollection("securityTest2");
            ums = (UserManagementService) testCol2.getService("UserManagementService", "1.0");
            //change ownership to test1
            ums.chown(test1, "users");
            // full permissions for user and group, none for world
            ums.chmod(0775);

            // create a collection /db/securityTest3 as user "test3"
            cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
            Collection testCol3 = cms.createCollection("securityTest3");
            ums = (UserManagementService) testCol3.getService("UserManagementService", "1.0");
            //change ownership to test3
            final Account test3 = ums.getAccount("test3");
            ums.chown(test3, "users");
            // full permissions for all
            ums.chmod(0777);
        } catch(final XMLDBException xmldbe) {
            xmldbe.printStackTrace();
            fail(xmldbe.getMessage());
        }
    }

    @After
    public void cleanup() throws XMLDBException {
        try {
            final Collection root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
            final CollectionManagementService cms = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");

            final Collection secTest1 = root.getChildCollection("securityTest1");
            if(secTest1 != null) {
                secTest1.close();
                cms.removeCollection("securityTest1");
            }

            final Collection secTest2 = root.getChildCollection("securityTest2");
            if(secTest2 != null) {
                secTest2.close();
                cms.removeCollection("securityTest2");
            }

            final Collection secTest3 = root.getChildCollection("securityTest3");
            if(secTest3 != null) {
                secTest3.close();
                cms.removeCollection("securityTest3");
            }

            final UserManagementService ums = (UserManagementService) root.getService("UserManagementService", "1.0");

            //remove accounts 'test1', 'test2' and 'test3'
            removeAccounts(ums, new String[]{"test1", "test2", "test3"});

            //remove group 'users'
            removeGroups(ums, new String[]{"users"});

        } catch(final XMLDBException xmldbe) {
            fail(xmldbe.getMessage());
        }
    }

    private void removeAccounts(final UserManagementService ums, final String[] accountNames) throws XMLDBException {
        final Account[] accounts = ums.getAccounts();
        for(final Account account: accounts) {
            for(final String accountName: accountNames) {
                if(account.getName().equals(accountName)) {
                    ums.removeAccount(account);
                    break;
                }
            }
        }
    }

    private void removeGroups(final UserManagementService ums, final String[] groupNames) throws XMLDBException {
        final String[] groups = ums.getGroups();
        for(final String group: groups) {
            for(final String groupName : groupNames) {
                if(group.equals(groupName)) {
                    ums.removeGroup(ums.getGroup(group));
                    break;
                }
            }
        }
    }

    @BeforeClass
    public static void startServer() {
        try {
            System.out.println("Starting standalone server...");
            server = new JettyStart();
            server.run();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void stopServer() {
        System.out.println("Shutdown standalone server...");
        server.shutdown();
        server = null;
    }
}
