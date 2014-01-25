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

    private static final String DB_DRIVER = "org.exist.xmldb.DatabaseImpl";

    private final String baseUri;

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

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldCreateCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        final CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");
        cms.createCollection("createdByGuest");
    }

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldAddResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        final Resource resource = test.createResource("createdByGuest", XMLResource.RESOURCE_TYPE);
        resource.setContent("<testMe/>");
        test.storeResource(resource);
    }

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldRemoveCollection() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(baseUri + "/db", "guest", "guest");
        final CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        cms.removeCollection("securityTest1");
    }

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChmodCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(0777);
    }

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChmodResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(resource, 0777);
    }

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChownCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        final Account guest = ums.getAccount("guest");
        // make myself the owner ;-)
        ums.chown(guest, "guest");
    }

    @Test(expected=XMLDBException.class)
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
        Resource xqueryResource = test.createResource("test.xquery", "BinaryResource");
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
        Resource xqueryResource = test.createResource("test.xquery", "BinaryResource");
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
        Resource xqueryResource = test.createResource("test.xquery", "BinaryResource");
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

    @Test
    public void setUidXQueryCanWriteRestrictedCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        final long timestamp = System.currentTimeMillis();
        final String content = "<setuid>" + timestamp + "</setuid>";

        //create an XQuery /db/securityTest1/setuid.xquery
        final String xquery = "xmldb:store('/db/securityTest1/forSetUidWrite', 'setuid.xml', " + content + ")";
        Resource xqueryResource = test.createResource("setuid.xquery", "BinaryResource");
        xqueryResource.setContent(xquery);
        test.storeResource(xqueryResource);

        //set the xquery to be owned by 'test1' and set it 'setuid', and set it 'rx' by 'users' group so 'test2' can execute it!
        UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        xqueryResource = test.getResource("setuid.xquery");
        ums.chmod(xqueryResource, 04750);

        //create a collection for the XQuery to write into
        final CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");
        final Collection colForSetUid = cms.createCollection("forSetUidWrite");

        //only allow the user 'test1' to write into the collection
        ums = (UserManagementService)colForSetUid.getService("UserManagementService", "1.0");
        ums.chmod(0700);

        //execute the XQuery as the 'test2' user... it should become 'setuid' of 'test1' and succeed.
        final Collection test2 = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        final XPathQueryServiceImpl queryService = (XPathQueryServiceImpl)test2.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest1/setuid.xquery");
        assertEquals("/db/securityTest1/forSetUidWrite/setuid.xml", result.getResource(0).getContent());

        //check the written content
        final Resource writtenXmlResource = colForSetUid.getResource("setuid.xml");
        assertEquals(content, writtenXmlResource.getContent());
    }

    @Test(expected=XMLDBException.class)
    public void nonSetUidXQueryCannotWriteRestrictedCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");

        final long timestamp = System.currentTimeMillis();
        final String content = "<not_setuid>" + timestamp + "</not_setuid>";

        //create an XQuery /db/securityTest1/not_setuid.xquery
        final String xquery = "xmldb:store('/db/securityTest1/forSetUidWrite', 'not_setuid.xml', " + content + ")";
        Resource xqueryResource = test.createResource("not_setuid.xquery", "BinaryResource");
        xqueryResource.setContent(xquery);
        test.storeResource(xqueryResource);

        //set the xquery to be owned by 'test1' and do NOT set it 'setuid', and do set it 'rx' by 'users' group so 'test2' can execute it!
        UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        xqueryResource = test.getResource("not_setuid.xquery");
        ums.chmod(xqueryResource, 00750); //NOT SETUID

        //create a collection for the XQuery to write into
        final CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");
        final Collection colForSetUid = cms.createCollection("forSetUidWrite");

        //only allow the user 'test1' to write into the collection
        ums = (UserManagementService)colForSetUid.getService("UserManagementService", "1.0");
        ums.chmod(0700);

        //execute the XQuery as the 'test2' user... it should become 'setuid' of 'test1' and succeed.
        final Collection test2 = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        final XPathQueryServiceImpl queryService = (XPathQueryServiceImpl)test2.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest1/not_setuid.xquery");
        assertFalse("/db/securityTest1/forSetUidWrite/not_setuid.xml".equals(result.getResource(0).getContent()));
    }

    @Test
    public void setGidXQueryCanWriteRestrictedCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test1", "test1");

        final long timestamp = System.currentTimeMillis();
        final String content = "<setgid>" + timestamp + "</setgid>";

        //create an XQuery /db/securityTest1/setuid.xquery
        final String xquery = "xmldb:store('/db/securityTest2/forSetGidWrite', 'setgid.xml', " + content + ")";
        Resource xqueryResource = test.createResource("setgid.xquery", "BinaryResource");
        xqueryResource.setContent(xquery);
        test.storeResource(xqueryResource);

        //set the xquery to be owned by 'test1':'users' and set it 'setgid', and set it 'rx' by ohers, so 'test3' can execute it!
        UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        xqueryResource = test.getResource("setgid.xquery");
        ums.chown(xqueryResource, ums.getAccount("test1"), "users");
        ums.chmod(xqueryResource, 02705); //setgid

        //create a collection for the XQuery to write into
        final CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");
        final Collection colForSetUid = cms.createCollection("forSetGidWrite");

        //only allow the group 'users' to write into the collection
        ums = (UserManagementService)colForSetUid.getService("UserManagementService", "1.0");
        ums.chmod(0570);

        //execute the XQuery as the 'test3' user... it should become 'setgid' of 'users' and succeed.
        final Collection test3 = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test3", "test3");
        final XPathQueryServiceImpl queryService = (XPathQueryServiceImpl)test3.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest2/setgid.xquery");
        assertEquals("/db/securityTest2/forSetGidWrite/setgid.xml", result.getResource(0).getContent());

        //check the written content
        final Resource writtenXmlResource = colForSetUid.getResource("setgid.xml");
        assertEquals(content, writtenXmlResource.getContent());
    }
    
    @Test(expected=XMLDBException.class)
    public void nonSetGidXQueryCannotWriteRestrictedCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test1", "test1");

        final long timestamp = System.currentTimeMillis();
        final String content = "<not_setgid>" + timestamp + "</not_setgid>";

        //create an XQuery /db/securityTest1/not_setgid.xquery
        final String xquery = "xmldb:store('/db/securityTest2/forSetGidWrite', 'not_setgid.xml', " + content + ")";
        Resource xqueryResource = test.createResource("not_setgid.xquery", "BinaryResource");
        xqueryResource.setContent(xquery);
        test.storeResource(xqueryResource);

        //set the xquery to be owned by 'test1':'users' and set it 'setgid', and set it 'rx' by ohers, so 'test3' can execute it!
        UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        xqueryResource = test.getResource("not_setgid.xquery");
        ums.chmod(xqueryResource, 00705); //NOT setgid

        //create a collection for the XQuery to write into
        final CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");
        final Collection colForSetUid = cms.createCollection("forSetGidWrite");

        //only allow the group 'users' to write into the collection
        ums = (UserManagementService)colForSetUid.getService("UserManagementService", "1.0");
        ums.chmod(0070);

        //execute the XQuery as the 'test3' user... it should become 'setgid' of 'users' and succeed.
        final Collection test3 = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test3", "test3");
        final XPathQueryServiceImpl queryService = (XPathQueryServiceImpl)test3.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest2/not_setgid.xquery");
        assertFalse("/db/securityTest2/forSetGidWrite/not_setgid.xml".equals(result.getResource(0).getContent()));
    }

    @Test
    public void noSetUid_createSubCollection_subCollectionGroupIsUsersPrimaryGroup() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test1", "test1");
        CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");

        //create /db/securityTest2/parentCollection with owner "test3:users" and mode "rwxrwxr-x"
        Collection parentCollection = cms.createCollection("parentCollection");
        UserManagementService ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chown(ums.getAccount("test3"), "users");
        ums.chmod("rwxrwxr-x");

        //now create the sub-collection /db/securityTest2/parentCollection/subCollection1
        //as user3, it should have it's group set to the primary group of user3 i.e. 'guest'
        //as it is NOT setUid
        parentCollection = DatabaseManager.getCollection(baseUri + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        cms = (CollectionManagementService)parentCollection.getService("CollectionManagementService", "1.0");
        final Collection subCollection = cms.createCollection("subCollection1");

        final Permission permissions = ums.getPermissions(subCollection);
        assertEquals("guest", permissions.getGroup().getName());
    }

    @Test
    public void setUid_createSubCollection_subCollectionGroupInheritedFromParent() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test1", "test1");
        CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");

        //create /db/securityTest2/parentCollection with owner "test3:users" and mode "rwxrwsr-x"
        Collection parentCollection = cms.createCollection("parentCollection");
        UserManagementService ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chown(ums.getAccount("test3"), "users");
        ums.chmod("rwxrwsr-x");

        //now create the sub-collection /db/securityTest2/parentCollection/subCollection1
        //it should inherit the group ownership 'users' from the parent which is setUid
        parentCollection = DatabaseManager.getCollection(baseUri + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        cms = (CollectionManagementService)parentCollection.getService("CollectionManagementService", "1.0");
        final Collection subCollection = cms.createCollection("subCollection1");

        final Permission permissions = ums.getPermissions(subCollection);
        assertEquals("users", permissions.getGroup().getName());
    }

    @Test
    public void noSetUid_createResource_resourceGroupIsUsersPrimaryGroup() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test1", "test1");
        CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");

        //create /db/securityTest2/parentCollection with owner "test3:users" and mode "rwxrwxr-x"
        Collection parentCollection = cms.createCollection("parentCollection");
        UserManagementService ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chown(ums.getAccount("test3"), "users");
        ums.chmod("rwxrwxr-x");

        //now create the sub-resource /db/securityTest2/parentCollection/test.xml
        //as user3, it should have it's group set to the primary group of user3 i.e. 'guest'
        //as it is NOT setUid
        parentCollection = DatabaseManager.getCollection(baseUri + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        Resource resource = parentCollection.createResource("test.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<test/>");
        parentCollection.storeResource(resource);

        final Permission permissions = ums.getPermissions(resource);
        assertEquals("guest", permissions.getGroup().getName());
    }

    @Test
    public void setUid_createResource_resourceGroupInheritedFromParent() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test1", "test1");
        CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");

        //create /db/securityTest2/parentCollection with owner "test3:users" and mode "rwxrwsr-x"
        Collection parentCollection = cms.createCollection("parentCollection");
        UserManagementService ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chown(ums.getAccount("test3"), "users");
        ums.chmod("rwxrwsr-x");

        //now create the sub-resource /db/securityTest2/parentCollection/test.xml
        //it should inherit the group ownership 'users' from the parent which is setUid
        parentCollection = DatabaseManager.getCollection(baseUri + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        Resource resource = parentCollection.createResource("test.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<test/>");
        parentCollection.storeResource(resource);

        final Permission permissions = ums.getPermissions(resource);
        assertEquals("users", permissions.getGroup().getName());
    }

    @Test
    public void noSetUid_copyCollection_collectionGroupIsUsersPrimaryGroup() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test1", "test1");
        CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl)test.getService("CollectionManagementService", "1.0");
        UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        //create the /db/securityTest2/src collection
        Collection srcCollection = cms.createCollection("src");
        ums.chown(ums.getAccount("test1"), "users");

        //create /db/securityTest2/parentCollection with owner "test3:users" and mode "rwxrwxr-x"
        Collection parentCollection = cms.createCollection("parentCollection");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chown(ums.getAccount("test3"), "users");
        ums.chmod("rwxrwxr-x");

        //now copy /db/securityTest2/src to /db/securityTest2/parentCollection/src
        //as user3, it should have it's group set to the primary group of user3 i.e. 'guest'
        //as the collection is NOT setUid
        test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test3", "test3");
        cms = (CollectionManagementServiceImpl)test.getService("CollectionManagementService", "1.0");
        cms.copy("src", "/db/securityTest2/parentCollection", "src");
        parentCollection = DatabaseManager.getCollection(baseUri + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");

        srcCollection = test.getChildCollection("src");
        final Collection destCollection = parentCollection.getChildCollection("src");

        final Permission permissions = ums.getPermissions(destCollection);
        assertEquals("guest", permissions.getGroup().getName());

        //TODO place a document in /db/securityTest2/src before it's copied and mae sure its perms are correct after the copy
    }

    @Test
    public void setUid_copyCollection_collectionGroupInheritedFromParent() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test1", "test1");
        CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl)test.getService("CollectionManagementService", "1.0");
        UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        //create the /db/securityTest2/src collection
        Collection srcCollection = cms.createCollection("src");
        ums.chown(ums.getAccount("test1"), "users");

        //create /db/securityTest2/parentCollection with owner "test3:users" and mode "rwxrwsr-x"
        Collection parentCollection = cms.createCollection("parentCollection");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chown(ums.getAccount("test3"), "users");
        ums.chmod("rwxrwsr-x");

        //now copy /db/securityTest2/src to /db/securityTest2/parentCollection/src
        //as user3, it should inherit the group ownership 'users' from the parent collection which is setUid
        test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test3", "test3");
        cms = (CollectionManagementServiceImpl)test.getService("CollectionManagementService", "1.0");
        cms.copy("src", "/db/securityTest2/parentCollection", "src");
        parentCollection = DatabaseManager.getCollection(baseUri + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");

        srcCollection = test.getChildCollection("src");
        final Collection destCollection = parentCollection.getChildCollection("src");

        final Permission permissions = ums.getPermissions(destCollection);
        assertEquals("users", permissions.getGroup().getName());

        //TODO place a document in /db/securityTest2/src before it's copied and mae sure its perms are correct after the copy
    }


    @Test
    public void noSetUid_copyResource_resourceGroupIsUsersPrimaryGroup() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test1", "test1");
        CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl)test.getService("CollectionManagementService", "1.0");
        UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        //create the /db/securityTest2/test.xml resource
        Resource resource = test.createResource("test.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<test/>");
        test.storeResource(resource);

        //create /db/securityTest2/parentCollection with owner "test3:users" and mode "rwxrwxr-x"
        Collection parentCollection = cms.createCollection("parentCollection");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chown(ums.getAccount("test3"), "users");
        ums.chmod("rwxrwxr-x");

        //now copy /db/securityTest2/test.xml to /db/securityTest2/parentCollection/test.xml
        //as user3, it should have it's group set to the primary group of user3 i.e. 'guest'
        //as the collection is NOT setUid
        test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test3", "test3");
        cms = (CollectionManagementServiceImpl)test.getService("CollectionManagementService", "1.0");
        cms.copyResource("test.xml", "/db/securityTest2/parentCollection", "test.xml");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");

        parentCollection = test.getChildCollection("parentCollection");
        resource = parentCollection.getResource("test.xml");

        final Permission permissions = ums.getPermissions(resource);
        assertEquals("guest", permissions.getGroup().getName());
    }

    @Test
    public void setUid_copyResource_resourceGroupInheritedFromParent() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test1", "test1");
        CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl)test.getService("CollectionManagementService", "1.0");
        UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        //create the /db/securityTest2/test.xml resource
        Resource resource = test.createResource("test.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<test/>");
        test.storeResource(resource);

        //create /db/securityTest2/parentCollection with owner "test3:users" and mode "rwxrwsr-x"
        Collection parentCollection = cms.createCollection("parentCollection");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chown(ums.getAccount("test3"), "users");
        ums.chmod("rwxrwsr-x");

        //now copy /db/securityTest2/test.xml to /db/securityTest2/parentCollection/test.xml
        //as user3, it should inherit the group ownership 'users' from the parent collection which is setUid
        test = DatabaseManager.getCollection(baseUri + "/db/securityTest2", "test3", "test3");
        cms = (CollectionManagementServiceImpl)test.getService("CollectionManagementService", "1.0");
        cms.copyResource("test.xml", "/db/securityTest2/parentCollection", "test.xml");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");

        parentCollection = test.getChildCollection("parentCollection");
        resource = parentCollection.getResource("test.xml");

        final Permission permissions = ums.getPermissions(resource);
        assertEquals("users", permissions.getGroup().getName());
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
            Collection test2 = cms.createCollection("securityTest2");
            ums = (UserManagementService) test2.getService("UserManagementService", "1.0");
            //change ownership to test1
            ums.chown(test1, "users");
            // full permissions for user and group, none for world
            ums.chmod(0775);
        } catch(final XMLDBException xmldbe) {
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
