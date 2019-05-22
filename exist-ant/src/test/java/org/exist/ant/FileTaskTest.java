package org.exist.ant;

import org.apache.tools.ant.Project;
import org.exist.TestUtils;
import org.exist.xmldb.EXistResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import javax.annotation.Nullable;
import java.net.URL;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class FileTaskTest extends AbstractTaskTest {

    private static final String TEST_COLLECTION_NAME = "test";
    private static final String TEST_RESOURCE_NAME = "test.xml";

    private static final String PROP_ANT_TEST_DATA_TEST_COLLECTION  = "test.data.test.collection";
    private static final String PROP_ANT_TEST_DATA_TEST_RESOURCE  = "test.data.test.resource";
    private static final String PROP_ANT_TEST_DATA_USER =  "test.data.user";
    private static final String PROP_ANT_TEST_DATA_GROUP =  "test.data.group";

    @Nullable
    @Override
    protected URL getBuildFile() {
        return getClass().getResource("file.xml");
    }

    @Before
    public void fileSetup() throws XMLDBException {
        final Collection col = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), TEST_COLLECTION_NAME);
        final Resource res = col.createResource(TEST_RESOURCE_NAME, XMLResource.RESOURCE_TYPE);
        res.setContent("<test/>");
        col.storeResource(res);
    }

    @After
    public void fileCleanup() throws XMLDBException {
        final CollectionManagementService service = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        service.removeCollection(TEST_COLLECTION_NAME);
    }

    @Test
    public void chmod() throws XMLDBException {
        final Project project = buildFileRule.getProject();
        project.setProperty(PROP_ANT_TEST_DATA_TEST_COLLECTION, TEST_COLLECTION_NAME);
        project.setProperty(PROP_ANT_TEST_DATA_TEST_RESOURCE, TEST_RESOURCE_NAME);

        buildFileRule.executeTarget("chmod");

        final String result = project.getProperty(PROP_ANT_TEST_DATA_RESULT);
        assertThat(result, containsString(TEST_RESOURCE_NAME));

        final Collection col = existEmbeddedServer.getRoot().getChildCollection(TEST_COLLECTION_NAME);
        final EXistResource res = (EXistResource)col.getResource(TEST_RESOURCE_NAME);
        assertEquals("---rwxrwx", res.getPermissions().toString());
    }

    @Test
    public void chown() throws XMLDBException {
        final Project project = buildFileRule.getProject();
        project.setProperty(PROP_ANT_TEST_DATA_TEST_COLLECTION, TEST_COLLECTION_NAME);
        project.setProperty(PROP_ANT_TEST_DATA_TEST_RESOURCE, TEST_RESOURCE_NAME);
        project.setProperty(PROP_ANT_TEST_DATA_USER, TestUtils.GUEST_DB_USER);
        project.setProperty(PROP_ANT_TEST_DATA_GROUP, TestUtils.GUEST_DB_USER);

        buildFileRule.executeTarget("chown");

        final String result = project.getProperty(PROP_ANT_TEST_DATA_RESULT);
        assertThat(result, containsString(TEST_RESOURCE_NAME));

        final Collection col = existEmbeddedServer.getRoot().getChildCollection(TEST_COLLECTION_NAME);
        final EXistResource res = (EXistResource)col.getResource(TEST_RESOURCE_NAME);
        assertEquals(TestUtils.GUEST_DB_USER, res.getPermissions().getOwner().getName());
        assertEquals(TestUtils.GUEST_DB_USER, res.getPermissions().getGroup().getName());
    }

    @Ignore("Would require implementing an UnlockResourceTask as well")
    @Test
    public void lockResource() {
        buildFileRule.executeTarget("lockResource");
    }
}
