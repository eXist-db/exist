package org.exist.ant;

import org.apache.tools.ant.Project;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ServerTaskTest extends AbstractTaskTest {

    private static final String PROP_ANT_TEST_DATA_BACKUP_DIR = "test.data.backup.dir";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Nullable
    @Override
    protected URL getBuildFile() {
        return getClass().getResource("server.xml");
    }

    @Test
    public void backup() throws IOException {
        final Project project = buildFileRule.getProject();
        final Path backupDir = temporaryFolder.newFolder().toPath();
        project.setProperty(PROP_ANT_TEST_DATA_BACKUP_DIR, backupDir.toAbsolutePath().toString());

        buildFileRule.executeTarget("backup");

        assertTrue(Files.exists(backupDir.resolve("db").resolve("__contents__.xml")));
    }

    @Test
    public void restore() throws URISyntaxException, XMLDBException {
        final URL backupContentsUrl = getClass().getResource("backup-test/db/__contents__.xml");
        assertNotNull(backupContentsUrl);
        final Path backupDir = Paths.get(backupContentsUrl.toURI()).getParent().getParent();

        final Project project = buildFileRule.getProject();
        project.setProperty(PROP_ANT_TEST_DATA_BACKUP_DIR, backupDir.toAbsolutePath().toString());

        buildFileRule.executeTarget("restore");

        final Resource res = existEmbeddedServer.getRoot().getResource("example.xml");
        assertNotNull(res);
    }

    @Test
    public void backupRestore() throws IOException {
        final Project project = buildFileRule.getProject();
        final Path backupDir = temporaryFolder.newFolder().toPath();
        project.setProperty(PROP_ANT_TEST_DATA_BACKUP_DIR, backupDir.toAbsolutePath().toString());

        buildFileRule.executeTarget("backup");

        buildFileRule.executeTarget("restore");
    }

    @Test
    public void shutdown() {
        buildFileRule.executeTarget("shutdown");
    }
}
