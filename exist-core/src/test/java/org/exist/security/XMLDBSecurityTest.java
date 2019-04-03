package org.exist.security;

import java.util.Arrays;

import org.exist.TestUtils;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.test.ExistWebServer;
import org.exist.test.TestConstants;
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.EXistXPathQueryService;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
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

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);
    private static final String PORT_PLACEHOLDER = "${PORT}";

    @Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "local", "xmldb:exist://" },
            { "remote", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc" }
        });
    }
    
    @Parameter
    public String apiName;
    
    @Parameter(value = 1)
    public String baseUri;

    private final String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldCreateCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "guest", "guest");
        final CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");
        cms.createCollection("createdByGuest");
    }

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldAddResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "guest", "guest");
        final Resource resource = test.createResource("createdByGuest", XMLResource.RESOURCE_TYPE);
        resource.setContent("<testMe/>");
        test.storeResource(resource);
    }

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldRemoveCollection() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", "guest", "guest");
        final CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        cms.removeCollection("securityTest1");
    }

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChmodCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "guest", "guest");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(0777);
    }

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChmodResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "guest", "guest");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(resource, 0777);
    }

    @Test(expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChownCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "guest", "guest");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        final Account guest = ums.getAccount("guest");
        // make myself the owner ;-)
        ums.chown(guest, "guest");
    }

    /**
     * only the owner or dba can chown a collection or resource
     */
    @Test (expected=XMLDBException.class)
    public void worldChownResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "guest", "guest");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        final Account test2 = ums.getAccount("guest");
        ums.chown(resource, test2, "guest");
    }

    @Test
    public void groupCreateSubColl() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        final CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");
        final Collection newCol = cms.createCollection("createdByTest2");
        assertNotNull(newCol);
    }

    @Test
    public void groupCreateResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        Resource resource = test.createResource("createdByTest2.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<testMe/>");
        test.storeResource(resource);

        resource = test.getResource("createdByTest2.xml");
        assertNotNull(resource);
        assertEquals("<testMe/>", resource.getContent().toString());
    }

    @Test(expected=XMLDBException.class)
    public void groupRemoveCollection_canNotWriteParent() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", "test2", "test2");
        final CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        cms.removeCollection("securityTest1");
    }

    @Test
    public void groupRemoveCollection_canWriteParent() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", "admin", "");
        final CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        cms.removeCollection("securityTest1");
    }

    @Test(expected=XMLDBException.class)
    public void groupChmodCollection_asNotOwnerAndNotDBA() throws XMLDBException {

        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // grant myself all rights ;-)
        ums.chmod(07777);
    }

    @Test
    public void groupChmodCollection_asOwner() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(07777);

        assertEquals("rwsrwsrwt", ums.getPermissions(test).toString());
    }

    @Test(expected=XMLDBException.class)
    public void groupChmodResource_asNotOwnerAndNotDBA() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(resource, 0777);
    }

    @Test
    public void groupChmodResource_asOwner() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(resource, 0777);
    }

    /**
     * DBA can change the owner uid of a collection
     *
     * As the user 'admin' (who is a DBA) attempt to change the
     * ownership uid of /db/securityTest1
     * to 'test2' user
     */
    @Test
    public void dbaChownUidCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "admin", "");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to change uid ownership of /db/securityTest1 to the test2 user
        final Account test2 = ums.getAccount("test2");
        ums.chown(test2);
    }

    /**
     * DBA can change the owner gid of a collection
     *
     * As the user 'admin' (who is a DBA) attempt to change the
     * ownership gid of /db/securityTest1
     * to 'guest' group
     */
    @Test
    public void dbaChownGidCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "admin", "");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to change uid ownership of /db/securityTest1 to the guest group
        ums.chgrp("guest");
    }

    /**
     * Owner can NOT change the owner uid of a collection
     *
     * As the user 'test1' attempt to change the
     * ownership uid of /db/securityTest1
     * to 'test2' user
     */
    @Test(expected=XMLDBException.class)
    public void ownerChownUidCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to change uid ownership of /db/securityTest1 to the test2 user
        final Account test2 = ums.getAccount("test2");
        ums.chown(test2);
    }

    /**
     * Owner can NOT change the owner gid of a collection
     * to a group of which they are not a member
     *
     * As the user 'test1' attempt to change the
     * ownership gid of /db/securityTest1
     * to 'guest' group
     */
    @Test(expected=XMLDBException.class)
    public void ownerChownGidCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to change gid ownership of /db/securityTest1 to the guest group
        ums.chgrp("guest");
    }

    /**
     * Group member can NOT change the owner uid of a collection
     *
     * As the user 'test2' attempt to change the
     * ownership uid of /db/securityTest1
     * to ourselves
     */
    @Test(expected=XMLDBException.class)
    public void groupMemberChownUidCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to take uid ownership of /db/securityTest1
        final Account test2 = ums.getAccount("test2");
        ums.chown(test2);
    }

    /**
     * Owner can change the owner gid of a collection
     * to a group of which they are a member
     *
     * As the user 'test1' (who is the owner and
     * who is in the group 'extusers')
     * attempt to change ownership gid of /db/securityTest1
     * to the group 'extusers'
     */
    @Test
    public void ownerAndGroupMemberChownGidCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to take gid ownership of /db/securityTest1
        ums.chgrp("extusers");

        final Permission perms = ums.getPermissions(test);
        assertEquals("extusers", perms.getGroup().getName());
    }
    
    /**
     * Group Member can NOT change the owner gid of a resource
     * to a group of which they are a member
     *
     * As the user 'test2' (who is in the group users)
     * attempt to change ownership gid of /db/securityTest1 (which has uid 'test1' and gid 'users')
     * to the group 'test2-only' (of which they are a member)
     */
    @Test(expected=XMLDBException.class)
    public void groupMemberChownGidCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to have user 'test2' take gid ownership of /db/securityTest1 (which is owner by test1:users)
        ums.chgrp("test2-only");
    }

    /**
     * Group Member can NOT change owner gid of a collection
     * to a group of which we are NOT a member
     *
     * As the user 'test2' (who is in the group users)
     * attempt to change ownership gid of /db/securityTest1
     * to the group 'guest' (of which they are NOT a member)
     */
    @Test(expected=XMLDBException.class)
    public void groupNonMemberChownGidCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to take gid ownership of /db/securityTest1
        ums.chgrp("guest");
    }

    /**
     * DBA can change the owner uid of a resource
     *
     * As the user 'admin' (who is a DBA) attempt to change the
     * ownership uid of /db/securityTest1/test.xml
     * to 'test2' user
     */
    @Test
    public void dbaChownUidResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "admin", "");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to change uid ownership of /db/securityTest1/test.xml to the test2 user
        final Account test2 = ums.getAccount("test2");
        ums.chown(resource, test2);
    }

    /**
     * DBA can change the owner gid of a resource
     *
     * As the user 'admin' (who is a DBA) attempt to change the
     * ownership gid of /db/securityTest1/test1.xml
     * to 'guest' group
     */
    @Test
    public void dbaChownGidResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "admin", "");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to change uid ownership of /db/securityTest1/test.xml to the guest group
        ums.chgrp(resource, "guest");
    }

    /**
     * Owner can NOT change the owner uid of a resource
     *
     * As the user 'test1' attempt to change the
     * ownership uid of /db/securityTest1/test.xml
     * to 'test2' user
     */
    @Test(expected=XMLDBException.class)
    public void ownerChownUidResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to change uid ownership of /db/securityTest1/test.xml to the test2 user
        final Account test2 = ums.getAccount("test2");
        ums.chown(resource, test2);
    }

    /**
     * Owner can NOT change the owner gid of a resource
     * to a group of which they are not a member
     *
     * As the user 'test1' attempt to change the
     * ownership gid of /db/securityTest1/test.xml
     * to 'guest' group
     */
    @Test(expected=XMLDBException.class)
    public void ownerChownGidResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to change gid ownership of /db/securityTest1/test.xml to the guest group
        ums.chgrp(resource, "guest");
    }

    
    /**
     * Group member can NOT change the owner uid of a resource
     *
     * As the user 'test2' attempt to change the
     * ownership uid of /db/securityTest1/test.xml
     * to ourselves
     */
    @Test(expected=XMLDBException.class)
    public void groupMemberChownUidResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to take uid ownership of /db/securityTest1/test.xml
        final Account test2 = ums.getAccount("test2");
        ums.chown(resource, test2);
    }

    /**
     * Owner can change the owner gid of a resource
     * to a group of which they are a member
     *
     * As the user 'test1' (who is the owner and
     * who is in the group 'extusers')
     * attempt to change ownership gid of /db/securityTest1/test.xml
     * to the group 'extusers'
     */
    @Test
    public void ownerAndGroupMemberChownGidResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to take gid ownership of /db/securityTest1
        ums.chgrp(resource, "extusers");

        final Permission perms = ums.getPermissions(resource);
        assertEquals("extusers", perms.getGroup().getName());
    }
    
    /**
     * Group Member can NOT change the owner gid of a resource
     * to a group of which they are a member
     *
     * As the user 'test2' (who is in the group users)
     * attempt to change ownership gid of /db/securityTest1/test.xml (which has uid 'test1' and gid 'users')
     * to the group 'test2-only' (of which they are a member)
     */
    @Test(expected=XMLDBException.class)
    public void groupMemberChownGidResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to have user 'test2' take gid ownership of /db/securityTest1/test.xml (which is owned by test1:users)
        ums.chgrp(resource, "test2-only");
    }

    /**
     * Group Member can NOT change owner gid of a resource
     * to a group of which we are NOT a member
     *
     * As the user 'test2' (who is in the group users)
     * attempt to change ownership gid of /db/securityTest1/test.xml
     * to the group 'guest' (of which they are NOT a member)
     */
    @Test(expected=XMLDBException.class)
    public void groupNonMemberChownGidResource() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        final Resource resource = test.getResource("test.xml");
        final UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // attempt to take gid ownership of /db/securityTest1/test.xml
        ums.chgrp(resource, "guest");
    }

    @Test
    public void onlyExecuteRequiredToOpenCollectionContent() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x------");
        test.close();

        DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
    }

    @Test(expected=XMLDBException.class)
    public void cannotOpenCollectionWithoutExecute() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("rw-rw-rw-");
        test.close();

        DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
    }

    @Test
    public void canOpenCollectionWithExecute() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x--x--x");
        test.close();

        DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
    }

    @Test(expected=XMLDBException.class)
    public void cannotOpenRootCollectionWithoutExecute() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db", "admin", "");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("rw-rw-rw-");
        test.close();

        DatabaseManager.getCollection(getBaseUri() + "/db", "test1", "test1");
    }

    @Test
    public void canOpenRootCollectionWithExecute() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db", "admin", "");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x--x--x");
        test.close();

        DatabaseManager.getCollection(getBaseUri() + "/db", "test1", "test1");
    }

    @Test
    public void onlyReadAndExecuteRequiredToListCollectionResources() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("r-x------");

        test.listResources();
    }

    @Test(expected=XMLDBException.class)
    public void cannotListCollectionResourcesWithoutRead() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("-wx-wx-wx");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        test.listResources();
    }

    @Test
    public void onlyReadAndExecuteRequiredToListCollectionSubCollections() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("r-x------");

        test.listChildCollections();
    }

    @Test(expected=XMLDBException.class)
    public void cannotListCollectionSubCollectionsWithoutRead() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("-wx-wx-wx");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        test.listChildCollections();
    }

    @Test
    public void canReadXmlResourceWithOnlyExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.getResource("test.xml");
        assertEquals("<test/>", resource.getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotReadXmlResourceWithoutExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("rw-------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.getResource("test.xml");
        assertEquals("<test/>", resource.getContent());
    }

    @Test
    public void canReadBinaryResourceWithOnlyExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.getResource("test.bin");
        assertArrayEquals("binary-test".getBytes(), (byte[])resource.getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotReadBinaryResourceWithoutExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("rw-------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.getResource("test.bin");
        assertArrayEquals("binary-test".getBytes(), (byte[])resource.getContent());
    }

    @Test
    public void canReadXmlResourceWithOnlyReadPermission() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        Resource resource = test.getResource("test.xml");
        ums.chmod(resource, "r--------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        resource = test.getResource("test.xml");
        assertEquals("<test/>", resource.getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotReadXmlResourceWithoutReadPermission() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        Resource resource = test.getResource("test.xml");
        ums.chmod(resource, "-wx------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        resource = test.getResource("test.xml");
        assertEquals("<test/>", resource.getContent());
    }

    @Test
    public void canReadBinaryResourceWithOnlyReadPermission() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        Resource resource = test.getResource("test.bin");
        ums.chmod(resource, "r--------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        resource = test.getResource("test.bin");
        assertArrayEquals("binary-test".getBytes(), (byte[])resource.getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotReadBinaryResourceWithoutReadPermission() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        Resource resource = test.getResource("test.bin");
        ums.chmod(resource, "-wx------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        resource = test.getResource("test.bin");
        assertArrayEquals("binary-test".getBytes(), (byte[])resource.getContent());
    }

    @Test
    public void canCreateXmlResourceWithOnlyExecuteAndWritePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("-wx------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.createResource("other.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<other/>");
        test.storeResource(resource);
    }

    @Test
    public void canCreateBinaryResourceWithOnlyExecuteAndWritePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("-wx------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        final Resource resource = test.createResource("other.bin", BinaryResource.RESOURCE_TYPE);
        resource.setContent("binary".getBytes());
        test.storeResource(resource);
    }

    @Test
    public void canUpdateXmlResourceWithOnlyExecutePermissionOnParentCollection() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

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
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        ums.chmod("--x------");
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

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
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        final String xquery = "<xquery>{ 1 + 1 }</xquery>";
        Resource xqueryResource = test.createResource("test.xquery", BinaryResource.RESOURCE_TYPE);
        xqueryResource.setContent(xquery);
        test.storeResource(xqueryResource);

        ums.chmod("--x------");
        ums.chmod(xqueryResource, "rwx------"); //set execute bit on xquery (its off by default!)
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        xqueryResource = test.getResource("test.xquery");
        assertEquals(xquery, new String((byte[])xqueryResource.getContent()));

        //execute the stored XQuery
        final EXistXPathQueryService queryService = (EXistXPathQueryService)test.getService("XPathQueryService", "1.0");
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
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        final String xquery = "<xquery>{ 1 + 2 }</xquery>";
        Resource xqueryResource = test.createResource("test.xquery", BinaryResource.RESOURCE_TYPE);
        xqueryResource.setContent(xquery);
        test.storeResource(xqueryResource);

        ums.chmod(xqueryResource, "r-x------"); //execute only on xquery
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        xqueryResource = test.getResource("test.xquery");
        assertEquals(xquery, new String((byte[])xqueryResource.getContent()));

        //execute the stored XQuery
        final EXistXPathQueryService queryService = (EXistXPathQueryService)test.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest1/test.xquery");
        assertEquals("<xquery>3</xquery>", result.getResource(0).getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotExecuteXQueryWithoutExecutePermission() throws XMLDBException{
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        final String xquery = "<xquery>{ 1 + 2 }</xquery>";
        Resource xqueryResource = test.createResource("test.xquery", BinaryResource.RESOURCE_TYPE);
        xqueryResource.setContent(xquery);
        test.storeResource(xqueryResource);

        ums.chmod(xqueryResource, "rw-------"); //execute only on xquery
        test.close();

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        xqueryResource = test.getResource("test.xquery");
        assertEquals(xquery, new String((byte[])xqueryResource.getContent()));

        //execute the stored XQuery
        final EXistXPathQueryService queryService = (EXistXPathQueryService)test.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest1/test.xquery");
        assertEquals("<xquery>3</xquery>", result.getResource(0).getContent());
    }

    @Test(expected=XMLDBException.class)
    public void cannotOpenCollection() throws XMLDBException {
        //check that a user not in the users group (i.e. test3) cannot open the collection /db/securityTest1
        DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test3", "test3");
    }

    @Test
    public void canOpenCollection() throws XMLDBException {
        //check that a user in the users group (i.e. test2) can open the collection /db/securityTest1
        DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");

        //check that any user can open the collection /db/securityTest3
        DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test2", "test2");
        DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
    }

    @Test
    public void copyCollectionWithResources() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //create resource owned by "test1", and group "users" in /db/securityTest3/source
        Resource resSource = source.createResource("source1.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);

        resSource = source.createResource("source2.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);

        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");

        final Collection copyOfSource = test.getChildCollection("copy-of-source");
        assertNotNull(copyOfSource);
        assertEquals(2, copyOfSource.listResources().length);
    }
    
    /**
     * As the 'test1' user, creates the collection and resource:
     *
     *  test1:users /db/securityTest3/source
     *  test1:users /db/securityTest3/source/source1.xml
     *  test1:users /db/securityTest3/source/source2.xml
     *
     * We then also create the Collection
     *  test1:users /db/securityTest3/copy-of-source (0777)
     * so that the destination (for the copy we are about
     * to do) already exists and is writable...
     * 
     * As the 'test3' user, copy the collection:
     *
     *  /db/securityTest3/source
     *      -> /db/securityTest3/copy-of-source
     */
    @Test
    public void copyCollectionWithResources_destExists_destIsWritable() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //create resource owned by "test1", and group "users" in /db/securityTest3/source
        Resource resSource = source.createResource("source1.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);

        resSource = source.createResource("source2.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);
        
        //pre-create the destination and set writable by all
        final Collection dest = cms.createCollection("copy-of-source");
        final UserManagementService ums = (UserManagementService)dest.getService("UserManagementService", "1.0");
        ums.chmod(0777);

        
        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");

        final Collection copyOfSource = test.getChildCollection("copy-of-source");
        assertNotNull(copyOfSource);
        assertEquals(2, copyOfSource.listResources().length);
    }
    
    /**
     * As the 'test1' user, creates the collection and resource:
     *
     *  test1:users /db/securityTest3/source
     *  test1:users /db/securityTest3/source/source1.xml
     *  test1:users /db/securityTest3/source/source2.xml
     *
     * We then also create the Collection
     *  test1:users /db/securityTest3/copy-of-source (0755)
     * so that the destination (for the copy we are about
     * to do) already exists and is NOT writable...
     * 
     * As the 'test3' user, copy the collection:
     *
     *  /db/securityTest3/source
     *      -> /db/securityTest3/copy-of-source
     */
    @Test(expected=XMLDBException.class)
    public void copyCollectionWithResources_destExists_destIsNotWritable() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //create resource owned by "test1", and group "users" in /db/securityTest3/source
        Resource resSource = source.createResource("source1.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);

        resSource = source.createResource("source2.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);
        
        //pre-create the destination with default mode (0755)
        //so that it is not writable by 'test3' user
        final Collection dest = cms.createCollection("copy-of-source");

        
        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");
    }
    
    /**
     * As the 'test1' user, creates the collection and resource:
     *
     *  test1:users /db/securityTest3/source
     *  test1:users /db/securityTest3/source/source1.xml
     *  test1:users /db/securityTest3/source/source2.xml
     *
     * We then also create the Collection
     *  test1:users /db/securityTest3/copy-of-source (0777)
     * so that the destination (for the copy we are about
     * to do) already exists and is writable.
     * We then create the resource
     *  test1:users /db/securityTest/copy-of-source/source1.xml
     * and set it so that it is not accessible by anyone
     * apart from 'test1' user...
     * 
     * As the 'test3' user, copy the collection:
     *
     *  /db/securityTest3/source
     *      -> /db/securityTest3/copy-of-source
     * 
     * The test should prove that during a copy, existing
     * documents in the dest are replaced as long as the
     * dest collection has write permission and that the
     * permissions on the dest resource must also be writable
     */
    @Test(expected=XMLDBException.class)
    public void copyCollectionWithResources_destResourceExists_destResourceIsNotWritable() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //create resource owned by "test1", and group "users" in /db/securityTest3/source
        Resource resSource = source.createResource("source1.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test1/>");
        source.storeResource(resSource);

        resSource = source.createResource("source2.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test2/>");
        source.storeResource(resSource);
        
        //pre-create the destination and set writable by all
        final Collection dest = cms.createCollection("copy-of-source");
        UserManagementService ums = (UserManagementService) dest.getService("UserManagementService", "1.0");
        ums.chmod(0777);
        
        //pre-create a destination resource and set no access to group and others
        Resource resDestSource1 = dest.createResource("source1.xml", XMLResource.RESOURCE_TYPE);
        resDestSource1.setContent("<old/>");
        dest.storeResource(resDestSource1);
        ums.chmod(resDestSource1, 0700);
        
        
        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");
        
        final Collection copyOfSource = test.getChildCollection("copy-of-source");
        assertNotNull(copyOfSource);
        assertEquals(2, copyOfSource.listResources().length);
        
        final Resource resCopyOfSource1 = copyOfSource.getResource("source1.xml");
        assertEquals("<test1/>", resCopyOfSource1.getContent().toString());
        
        final Resource resCopyOfSource2 = copyOfSource.getResource("source2.xml");
        assertEquals("<test2/>", resCopyOfSource2.getContent().toString());
        
        //TODO check perms are/areNot preserved? on the replaced resource
    }
    
    @Test
    public void copyCollectionWithResources_withSubCollectionWithResource() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //create resource owned by "test1", and group "users" in /db/securityTest3/source
        Resource resSource = source.createResource("source1.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);

        resSource = source.createResource("source2.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);
        
        //create sub-collection "sub" owned by "test1", and group "users" in /db/securityTest3/source
        CollectionManagementService cms1 = (EXistCollectionManagementService)source.getService("CollectionManagementService", "1.0");
        Collection sub = cms1.createCollection("sub");
        
        //create resource owned by "test1", and group "users" in /db/securityTest3/source/sub1
        Resource resSub = sub.createResource("sub1.xml", XMLResource.RESOURCE_TYPE);
        resSub.setContent("<test-sub/>");
        sub.storeResource(resSub);

        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");

        final Collection copyOfSource = test.getChildCollection("copy-of-source");
        assertNotNull(copyOfSource);
        assertEquals(2, copyOfSource.listResources().length);
        
        final Collection copyOfSub = copyOfSource.getChildCollection("sub");
        assertNotNull(copyOfSub);
        assertEquals(1, copyOfSub.listResources().length);
    }

    @Test
    public void copyDocument_doesNotPreservePermissions() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create resource owned by "test1", and group "users" in /db/securityTest3
        final Resource resSource = test.createResource("source.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        test.storeResource(resSource);

        //as the 'test3' user copy the resource
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copyResource("/db/securityTest3/source.xml", "/db/securityTest3", "copy-of-source.xml");

        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        final Permission permissions = ums.getPermissions(test.getResource("copy-of-source.xml"));

        //resource should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source.xml
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());
    }
    
    @Test
    public void copyDocument_doesPreservePermissions_whenDestResourceExists() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create resource owned by "test1", and group "users" in /db/securityTest3
        final Resource resSource = test.createResource("source.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        test.storeResource(resSource);
        
        //pre-create the dest resource (before the copy) and set writable by all
        final Resource resDest = test.createResource("copy-of-source.xml", XMLResource.RESOURCE_TYPE);
        resDest.setContent("<old/>");
        test.storeResource(resDest);
        UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        ums.chmod(resDest, 0777);

        
        //as the 'test3' user copy the resource
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copyResource("/db/securityTest3/source.xml", "/db/securityTest3", "copy-of-source.xml");

        //as test3 user!
        ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        final Permission permissions = ums.getPermissions(test.getResource("copy-of-source.xml"));

        //resource should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source.xml
        assertEquals("test1", permissions.getOwner().getName());
        assertEquals("users", permissions.getGroup().getName());
        
        //TODO copy collection should do the same??!?
    }

    @Test
    public void copyCollection_doesNotPreservePermissions() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");


        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        final Permission permissions = ums.getPermissions(test.getChildCollection("copy-of-source"));

        //collection should be owned by test3:guest, i.e. permissions were not preserved from the test1 users collection /db/securityTest3/source
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());
    }
    
    @Test
    public void copyCollection_doesPreservePermissions_whenDestCollectionExists() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");
        
        //pre-create the dest collection and grant access to all (0777)
        Collection dest = cms.createCollection("copy-of-source");
        UserManagementService ums = (UserManagementService)dest.getService("UserManagementService", "1.0");
        ums.chmod(0777);

        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");

        //re-get ums as 'test3' user
        ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        final Permission permissions = ums.getPermissions(test.getChildCollection("copy-of-source"));

        //collection should STILL be owned by test1:users, i.e. permissions were preserved from the test1 users collection /db/securityTest3/copy-of-source
        assertEquals("test1", permissions.getOwner().getName());
        assertEquals("users", permissions.getGroup().getName());
    }

    @Test
    public void copyCollection_doesPreservePermissionsOfSubDocuments() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        cms.copy(XmldbURI.create("/db/securityTest1"), XmldbURI.create("/db/securityTest3"), XmldbURI.create("copy-of-securityTest1"));

        final Collection testCopy = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3/copy-of-securityTest1", "test1", "test1");

        final UserManagementService ums = (UserManagementService) testCopy.getService("UserManagementService", "1.0");
        final Resource resource = testCopy.getResource("test.xml");
        final Permission permissions = ums.getPermissions(resource);

        assertEquals("test1", permissions.getOwner().getName());
        assertEquals("users", permissions.getGroup().getName());
        assertEquals(0770, permissions.getMode());
    }
    
    @Test
    public void copyCollection_doesPreservePermissionsOfSubCollections() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        cms.copy(XmldbURI.create("/db/securityTest1"), XmldbURI.create("/db/securityTest3"), XmldbURI.create("copy-of-securityTest1"));

        final Collection testCopy = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3/copy-of-securityTest1", "test1", "test1");

        final Collection sub1 = testCopy.getChildCollection("sub1");
        final UserManagementService ums = (UserManagementService) sub1.getService("UserManagementService", "1.0");
        final Permission permissions = ums.getPermissions(sub1);

        assertEquals("test1", permissions.getOwner().getName());
        assertEquals("users", permissions.getGroup().getName());
        assertEquals(0777, permissions.getMode());
    }
    
    /**
     * As the 'test1' user, creates the collection and resource:
     *
     *  test1:users /db/securityTest3/source
     *  test1:users /db/securityTest3/source/source.xml
     *
     *
     * As the 'test3' user, copy the collection:
     *
     *  /db/securityTest3/source
     *      -> /db/securityTest3/copy-of-source
     */
    @Test
    public void copyCollectionWithResource_doesNotPreservePermissions() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //create resource owned by "test1", and group "users" in /db/securityTest3/source
        final Resource resSource = source.createResource("source.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);

        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");


        UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        final Collection copyOfSource = test.getChildCollection("copy-of-source");
        Permission permissions = ums.getPermissions(copyOfSource);

        //collection should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());

        ums = (UserManagementService) copyOfSource.getService("UserManagementService", "1.0");
        final Resource resCopyOfSource = copyOfSource.getResource("source.xml");
        permissions = ums.getPermissions(resCopyOfSource);

        //resource in collection should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source.xml
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());
    }
    
    
    /**
     * As the 'test1' user, creates the collection and resource:
     *
     *  test1:users /db/securityTest3/source
     *  test1:users /db/securityTest3/source/source1.xml
     *  test1:users /db/securityTest3/source/source2.xml
     *  test1:users /db/securityTest3/source/sub
     *  test1:users /db/securityTest3/source/sub/sub1.xml
     *
     *
     * As the 'test3' user, copy the collection:
     *
     *  /db/securityTest3/source
     *      -> /db/securityTest3/copy-of-source
     */
    @Test
    public void copyCollectionWithResources_withSubCollectionWithResource_doesNotPreservePermissions() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //create resource owned by "test1", and group "users" in /db/securityTest3/source
        Resource resSource = source.createResource("source1.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);

        resSource = source.createResource("source2.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test/>");
        source.storeResource(resSource);
        
        //create sub-collection "sub" owned by "test1", and group "users" in /db/securityTest3/source
        CollectionManagementService cms1 = (EXistCollectionManagementService)source.getService("CollectionManagementService", "1.0");
        Collection sub = cms1.createCollection("sub");
        
        //create resource owned by "test1", and group "users" in /db/securityTest3/source/sub1
        Resource resSub = sub.createResource("sub1.xml", XMLResource.RESOURCE_TYPE);
        resSub.setContent("<test-sub/>");
        sub.storeResource(resSub);

        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");

        final Collection copyOfSource = test.getChildCollection("copy-of-source");
        assertNotNull(copyOfSource);
        assertEquals(2, copyOfSource.listResources().length);
        
        final Collection copyOfSub = copyOfSource.getChildCollection("sub");
        assertNotNull(copyOfSub);
        assertEquals(1, copyOfSub.listResources().length);
        
        //collection should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source
        UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        Permission permissions = ums.getPermissions(copyOfSource);
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());

        //resource in collection should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source/source1.xml
        ums = (UserManagementService) copyOfSource.getService("UserManagementService", "1.0");
        final Resource resCopyOfSource1 = copyOfSource.getResource("source1.xml");
        permissions = ums.getPermissions(resCopyOfSource1);
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());
        
        //resource in collection should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source/source2.xml
        final Resource resCopyOfSource2 = copyOfSource.getResource("source2.xml");
        permissions = ums.getPermissions(resCopyOfSource2);
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());
        
        //sub-collection should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source/sub
        ums = (UserManagementService)copyOfSub.getService("UserManagementService", "1.0");
        permissions = ums.getPermissions(copyOfSub);
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());
        
        //sub-collection/resource should be owned by test3:guest, i.e. permissions were not preserved from the test1 users doc /db/securityTest3/source/sub/sub1.xml
        final Resource resCopyOfSub1 = copyOfSub.getResource("sub1.xml");
        permissions = ums.getPermissions(resCopyOfSub1);
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());
    }
    
    /**
     * As the 'test1' user, creates the collection and resource:
     *
     *  test1:users /db/securityTest3/source
     *  test1:users /db/securityTest3/source/source1.xml
     *  test1:users /db/securityTest3/source/source2.xml
     *
     * We then also create the Collection
     *  test1:users /db/securityTest3/copy-of-source (0777)
     * so that the destination (for the copy we are about
     * to do) already exists and is writable.
     * We then create the resource
     *  test1:users /db/securityTest/copy-of-source/source1.xml
     * and set it so that it is writable by all (0777)...
     * 
     * As the 'test3' user, copy the collection:
     *
     *  /db/securityTest3/source
     *      -> /db/securityTest3/copy-of-source
     * 
     * The test should prove that during a copy, existing
     * documents in the dest are replaced as long as the
     * dest collection has write permission and that the
     * permissions on the dest resource must also be writable
     * and that the existing permissions on the dest
     * resource will be preserved
     */
    @Test
    public void copyCollectionWithResources_destResourceExists_destResourceIsWritable_preservePermissions() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");

        //create collection owned by "test1", and group "users" in /db/securityTest3
        Collection source = cms.createCollection("source");

        //create resource owned by "test1", and group "users" in /db/securityTest3/source
        Resource resSource = source.createResource("source1.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test1/>");
        source.storeResource(resSource);

        resSource = source.createResource("source2.xml", XMLResource.RESOURCE_TYPE);
        resSource.setContent("<test2/>");
        source.storeResource(resSource);
        
        //pre-create the destination and set writable by all
        final Collection dest = cms.createCollection("copy-of-source");
        UserManagementService ums = (UserManagementService) dest.getService("UserManagementService", "1.0");
        ums.chmod(0777);
        
        //pre-create a destination resource and set access for all
        Resource resDestSource1 = dest.createResource("source1.xml", XMLResource.RESOURCE_TYPE);
        resDestSource1.setContent("<old/>");
        dest.storeResource(resDestSource1);
        ums.chmod(resDestSource1, 0777);
        
        //as the 'test3' user copy the collection
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest3", "test3", "test3");
        cms = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
        cms.copy("/db/securityTest3/source", "/db/securityTest3", "copy-of-source");
        
        final Collection copyOfSource = test.getChildCollection("copy-of-source");
        assertNotNull(copyOfSource);
        assertEquals(2, copyOfSource.listResources().length);
        ums = (UserManagementService) copyOfSource.getService("UserManagementService", "1.0");
        
        //permissions should NOT have changed as the dest already existed!
        Permission permissions = ums.getPermissions(copyOfSource);
        assertEquals("test1", permissions.getOwner().getName());
        assertEquals("users", permissions.getGroup().getName());
        
        final Resource resCopyOfSource1 = copyOfSource.getResource("source1.xml");
        assertEquals("<test1/>", resCopyOfSource1.getContent().toString());
        
        //permissions should NOT have changed as the dest resource already existed!
        permissions = ums.getPermissions(resCopyOfSource1);
        assertEquals("test1", permissions.getOwner().getName());
        assertEquals("users", permissions.getGroup().getName());
        
        final Resource resCopyOfSource2 = copyOfSource.getResource("source2.xml");
        assertEquals("<test2/>", resCopyOfSource2.getContent().toString());
        
        //permissions SHOULD have changed as the dest resource is did NOT exist
        permissions = ums.getPermissions(resCopyOfSource2);
        assertEquals("test3", permissions.getOwner().getName());
        assertEquals("guest", permissions.getGroup().getName());
    }

    @Test
    public void setUidXQueryCanWriteRestrictedCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

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
        final Collection test2 = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        final EXistXPathQueryService queryService = (EXistXPathQueryService)test2.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest1/setuid.xquery");
        assertEquals("/db/securityTest1/forSetUidWrite/setuid.xml", result.getResource(0).getContent());

        //check the written content
        final Resource writtenXmlResource = colForSetUid.getResource("setuid.xml");
        assertEquals(content, writtenXmlResource.getContent());
    }

    @Test(expected=XMLDBException.class)
    public void nonSetUidXQueryCannotWriteRestrictedCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

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
        final Collection test2 = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test2", "test2");
        final EXistXPathQueryService queryService = (EXistXPathQueryService)test2.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest1/not_setuid.xquery");
        assertFalse("/db/securityTest1/forSetUidWrite/not_setuid.xml".equals(result.getResource(0).getContent()));
    }

    @Test
    public void setGidXQueryCanWriteRestrictedCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test1", "test1");

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
        final Collection test3 = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test3", "test3");
        final EXistXPathQueryService queryService = (EXistXPathQueryService)test3.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest2/setgid.xquery");
        assertEquals("/db/securityTest2/forSetGidWrite/setgid.xml", result.getResource(0).getContent());

        //check the written content
        final Resource writtenXmlResource = colForSetUid.getResource("setgid.xml");
        assertEquals(content, writtenXmlResource.getContent());
    }
    
    @Test(expected=XMLDBException.class)
    public void nonSetGidXQueryCannotWriteRestrictedCollection() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test1", "test1");

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
        final Collection test3 = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test3", "test3");
        final EXistXPathQueryService queryService = (EXistXPathQueryService)test3.getService("XPathQueryService", "1.0");
        final ResourceSet result = queryService.executeStoredQuery("/db/securityTest2/not_setgid.xquery");
        assertFalse("/db/securityTest2/forSetGidWrite/not_setgid.xml".equals(result.getResource(0).getContent()));
    }

    @Test
    public void noSetGid_createSubCollection_subCollectionGroupIsUsersPrimaryGroup() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test1", "test1");
        CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");

        //create /db/securityTest2/parentCollection with owner "test1:users" and mode "rwxr--rwx"
        Collection parentCollection = cms.createCollection("parentCollection");
        UserManagementService ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chmod("rwxr--rwx");

        //now create the sub-collection /db/securityTest2/parentCollection/subCollection1
        //as "user3:guest", it should have it's group set to the primary group of user3 i.e. 'guest'
        //as the collection is NOT setUid and it should NOT have the setGid bit set
        parentCollection = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        cms = (CollectionManagementService)parentCollection.getService("CollectionManagementService", "1.0");
        final Collection subCollection = cms.createCollection("subCollection1");

        final Permission permissions = ums.getPermissions(subCollection);
        assertEquals("guest", permissions.getGroup().getName());
        assertFalse(permissions.isSetGid());
    }

    @Test
    public void setGid_createSubCollection_subCollectionGroupInheritedFromParent() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test1", "test1");
        CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");

        //create /db/securityTest2/parentCollection with owner "test1:users" and mode "rwxrwsrwx"
        Collection parentCollection = cms.createCollection("parentCollection");
        UserManagementService ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chmod("rwxrwsrwx");

        //now create the sub-collection /db/securityTest2/parentCollection/subCollection1
        //it should inherit the group ownership 'users' from the parent collection which is setGid
        //and it should inherit the setGid bit
        parentCollection = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        cms = (CollectionManagementService)parentCollection.getService("CollectionManagementService", "1.0");
        final Collection subCollection = cms.createCollection("subCollection1");

        final Permission permissions = ums.getPermissions(subCollection);
        assertEquals("users", permissions.getGroup().getName());
        assertTrue(permissions.isSetGid());
    }

    @Test
    public void noSetGid_createResource_resourceGroupIsUsersPrimaryGroup() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test1", "test1");
        CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");

        //create /db/securityTest2/parentCollection with owner "test1:users" and mode "rwxrwxrwx"
        Collection parentCollection = cms.createCollection("parentCollection");
        UserManagementService ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chmod("rwxrwxrwx");

        //now create the sub-resource /db/securityTest2/parentCollection/test.xml
        //as "user3:guest", it should have it's group set to the primary group of user3 i.e. 'guest'
        //as the collection is NOT setGid, the file should NOT have the setGid bit set
        parentCollection = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        Resource resource = parentCollection.createResource("test.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<test/>");
        parentCollection.storeResource(resource);

        final Permission permissions = ums.getPermissions(resource);
        assertEquals("guest", permissions.getGroup().getName());
        assertFalse(permissions.isSetGid());
    }

    @Test
    public void setGid_createResource_resourceGroupInheritedFromParent() throws XMLDBException {
        final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test1", "test1");
        CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");

        //create /db/securityTest2/parentCollection with owner "test1:users" and mode "rwxrwsrwx"
        Collection parentCollection = cms.createCollection("parentCollection");
        UserManagementService ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chmod("rwxrwsrwx");

        //now as "test3:guest" create the sub-resource /db/securityTest2/parentCollection/test.xml
        //it should inherit the group ownership 'users' from the parent which is setGid
        //but it should not inherit the setGid bit as it is a resource
        parentCollection = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        Resource resource = parentCollection.createResource("test.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<test/>");
        parentCollection.storeResource(resource);

        final Permission permissions = ums.getPermissions(resource);
        assertEquals("users", permissions.getGroup().getName());
        assertFalse(permissions.isSetGid());
    }

    @Test
    public void noSetGid_copyCollection_collectionGroupIsUsersPrimaryGroup() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService)test.getService("CollectionManagementService", "1.0");

        //create the /db/securityTest2/src collection
        Collection srcCollection = cms.createCollection("src");

        //create /db/securityTest2/parentCollection with owner "test1:users" and mode "rwxrwxrwx"
        Collection parentCollection = cms.createCollection("parentCollection");
        UserManagementService ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chmod("rwxrwxrwx");

        //now copy /db/securityTest2/src to /db/securityTest2/parentCollection/src
        //as "user3:guest", it should have it's group set to the primary group of "user3" i.e. 'guest'
        //as the collection is NOT setGid and it should NOT have it's setGid bit set
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test3", "test3");
        cms = (EXistCollectionManagementService)test.getService("CollectionManagementService", "1.0");
        cms.copy("src", "/db/securityTest2/parentCollection", "src");
        parentCollection = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");

        srcCollection = test.getChildCollection("src");
        final Collection destCollection = parentCollection.getChildCollection("src");

        final Permission permissions = ums.getPermissions(destCollection);
        assertEquals("guest", permissions.getGroup().getName());
        assertFalse(permissions.isSetGid());
    }
    
    @Test
    public void setGid_copyCollection_collectionGroupInheritedFromParent() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService)test.getService("CollectionManagementService", "1.0");
        UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        //create the /db/securityTest2/src collection with owner "test1:extusers" and default mode
        Collection srcCollection = cms.createCollection("src");
        ums = (UserManagementService)srcCollection.getService("UserManagementService", "1.0");
        ums.chgrp("extusers");

        //create /db/securityTest2/parentCollection with owner "test1:users" and mode "rwxrwsrwx"
        Collection parentCollection = cms.createCollection("parentCollection");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chmod("rwxrwsrwx");

        //now copy /db/securityTest2/src to /db/securityTest2/parentCollection/src
        //as "user3:guest", it should inherit the group ownership 'users' from the parent
        //collection which is setGid and it should have its setGid bit set
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test3", "test3");
        cms = (EXistCollectionManagementService)test.getService("CollectionManagementService", "1.0");
        cms.copy("src", "/db/securityTest2/parentCollection", "src");
        parentCollection = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2/parentCollection", "test3", "test3");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");

        final Collection destCollection = parentCollection.getChildCollection("src");

        final Permission permissions = ums.getPermissions(destCollection);
        assertEquals("users", permissions.getGroup().getName());
        assertTrue(permissions.isSetGid());
    }


    @Test
    public void noSetGid_copyResource_resourceGroupIsUsersPrimaryGroup() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService)test.getService("CollectionManagementService", "1.0");

        //create the /db/securityTest2/test.xml resource
        Resource resource = test.createResource("test.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<test/>");
        test.storeResource(resource);

        //create /db/securityTest2/parentCollection with owner "test1:users" and mode "rwxrwxrwx"
        Collection parentCollection = cms.createCollection("parentCollection");
        UserManagementService ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chmod("rwxrwxrwx");

        //now copy /db/securityTest2/test.xml to /db/securityTest2/parentCollection/test.xml
        //as user3, it should have it's group set to the primary group of user3 i.e. 'guest'
        //as the collection is NOT setGid and it should not have the setGid bit
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test3", "test3");
        cms = (EXistCollectionManagementService)test.getService("CollectionManagementService", "1.0");
        cms.copyResource("test.xml", "/db/securityTest2/parentCollection", "test.xml");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");

        parentCollection = test.getChildCollection("parentCollection");
        resource = parentCollection.getResource("test.xml");

        final Permission permissions = ums.getPermissions(resource);
        assertEquals("guest", permissions.getGroup().getName());
        assertFalse(permissions.isSetGid());
    }

    @Test
    public void setGid_copyResource_resourceGroupInheritedFromParent() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test1", "test1");
        EXistCollectionManagementService cms = (EXistCollectionManagementService)test.getService("CollectionManagementService", "1.0");
        UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        //create the /db/securityTest2/test.xml resource
        Resource resource = test.createResource("test.xml", XMLResource.RESOURCE_TYPE);
        resource.setContent("<test/>");
        test.storeResource(resource);
        ums.chgrp(resource, "extusers");

        //create /db/securityTest2/parentCollection with owner "test1:users" and mode "rwxrwsrwx"
        Collection parentCollection = cms.createCollection("parentCollection");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");
        ums.chmod("rwxrwsrwx");

        //now copy /db/securityTest2/test.xml to /db/securityTest2/parentCollection/test.xml
        //as "user3:guest", it should inherit the group ownership 'users' from the parent collection which is setGid
        //and it should NOT have its setGid bit set as it is a resource
        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest2", "test3", "test3");
        cms = (EXistCollectionManagementService)test.getService("CollectionManagementService", "1.0");
        cms.copyResource("test.xml", "/db/securityTest2/parentCollection", "test.xml");
        ums = (UserManagementService)parentCollection.getService("UserManagementService", "1.0");

        parentCollection = test.getChildCollection("parentCollection");
        resource = parentCollection.getResource("test.xml");

        final Permission permissions = ums.getPermissions(resource);
        assertEquals("users", permissions.getGroup().getName());
        assertFalse(permissions.isSetGid());
    }

    
    //TODO need tests for
    //4) CopyingCollections to dests where permission is denied!
    //5) What about move Document, move Collection?
    
    /**
     * 1) Sets '/db' to rwxr-xr-x (0755)
     * 2) Adds the Group 'users'
     * 3) Adds the User 'test1' with password 'test1' and set's their primary group to 'users'
     * 4) Creates the group 'extusers' and adds 'test1' to it
     * 5) Adds the User 'test2' with password 'test2' and set's their primary group to 'users'
     * 6) Creates the group 'test2-only` and adds 'test2' to it
     * 7) Adds the User 'test3' with password 'test3' and set's their primary group to 'guest'
     * 8) Creates the Collection '/db/securityTest1' owned by 'test1':'users' with permissions rwxrwx--- (0770)
     * 9) Creates the XML resource '/db/securityTest1/test.xml' owned by 'test1':'users' with permissions rwxrwx--- (0770)
     * 10) Creates the Binary resource '/db/securityTest1/test.bin' owned by 'test1':'users' with permissions rwxrwx--- (0770)
     * 11) Creates the Collection '/db/securityTest2' owned by 'test1':'users' with permissions rwxrwxr-x (0775)
     * 12) Creates the Collection '/db/securityTest3' owned by 'test3':'guest' with permissions rwxrwxrwx (0777)
     */
    @Before
    public void setup() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
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

        final Group extGroup = new GroupAider("exist", "extusers");
        ums.addGroup(extGroup);
        ums.addAccountToGroup("test1", "extusers");

        user = new UserAider("test2", group);
        user.setPassword("test2");
        ums.addAccount(user);

        final Group test2OnlyGroup = new GroupAider("exist", "test2-only");
        ums.addGroup(test2OnlyGroup);
        ums.addAccountToGroup("test2", "test2-only");

        user = new UserAider("test3", ums.getGroup("guest"));
        user.setPassword("test3");
        ums.addAccount(user);

        // create a collection /db/securityTest1 as owned by "test1:users" and mode 0770
        CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        Collection test = cms.createCollection("securityTest1");
        ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        //change ownership to test1
        final Account test1 = ums.getAccount("test1");
        ums.chown(test1, "users");
        // full permissions for user and group, none for world
        ums.chmod(0770);

        test = DatabaseManager.getCollection(getBaseUri() + "/db/securityTest1", "test1", "test1");

        // create a resource /db/securityTest1/test.xml owned by "test1:users" and mode 0770
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

        // create a sub-collection /db/securityTest1/sub1 as user "test1"
        cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");
        Collection sub1 = cms.createCollection("sub1");
        ums = (UserManagementService) sub1.getService("UserManagementService", "1.0");
        //change ownership to test1
        ums.chown(test1, "users");
        // full permissions for all
        ums.chmod(0777);
    }

    @After
    public void cleanup() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", "admin", "");
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

        //remove group 'users', 'extusers', 'test2-only'
        removeGroups(ums, new String[]{"users", "extusers", "test2-only"});
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
}
