/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.client;

import org.easymock.EasyMockExtension;
import org.easymock.Mock;
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.xmldb.UserManagementService;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Properties;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.*;
import static org.easymock.EasyMock.*;
import static org.exist.client.InteractiveClient.PERMISSIONS;

@ExtendWith(EasyMockExtension.class)
class InteractiveClientTest {
    private final static String EOL = System.lineSeparator();
    private final static String FILE_SEPARATOR = System.getProperty("file.separator");

    @Mock
    Collection collection;
    @Mock
    ClientFrame clientFrame;
    @Mock
    UserManagementService mgtService;
    @Mock
    Permission perm;
    @Mock
    Resource resource;
    @Mock
    Account account;
    @Mock
    Group group;

    InteractiveClient client;

    @BeforeEach
    void setUp() throws URISyntaxException {
        client = new InteractiveClient(CommandlineOptions.parse(new String[0])) {
            @Override
            protected void connect() throws Exception {
                current = collection;
            }
        };
        client.frame = clientFrame;
    }

    @Test
    void getResourcesNoConnection() {
        replay(collection, mgtService, perm, resource, clientFrame, account, group);
        assertThatNoException().isThrownBy(client::getResources);
    }

    @Test
    void getCollection() {
        replay(collection, mgtService, perm, resource, clientFrame, account, group);
        assertThat(client.getCollection()).isNull();
        client.current = collection;
        assertThat(client.getCollection()).isNotNull();
    }

    @Test
    void getProperties() {
        replay(collection, mgtService, perm, resource, clientFrame, account, group);
        assertThat(client.getProperties()).isNotNull();
    }

    @Test
    void getResourcesWithConnection() throws XMLDBException {
        client.current = collection;

        recordResourceData(false);
        clientFrame.setResources(anyObject());

        replay(collection, mgtService, perm, resource, clientFrame, account, group);
        assertThatNoException().isThrownBy(client::getResources);
    }

    @Test
    void getResourcesWithConnectionWithPermissions() throws XMLDBException {
        client.current = collection;

        collection.setProperty(PERMISSIONS, "true");
        recordResourceData(true);
        clientFrame.setResources(anyObject());

        replay(collection, mgtService, perm, resource, clientFrame, account, group);
        client.properties.setProperty(PERMISSIONS, "true");
        assertThatNoException().isThrownBy(client::getResources);
    }

    @Test
    void getGuiLoginData() {
        Properties props = new Properties();
        UnaryOperator<Properties> loginPropertyOperator = createMock(UnaryOperator.class);

        expect(loginPropertyOperator.apply(props)).andReturn(props);

        replay(collection, mgtService, perm, resource, clientFrame, account, group, loginPropertyOperator);
        assertThat(client.getGuiLoginData(props, loginPropertyOperator)).isFalse();
    }

    @Test
    void displayHelp() {
        clientFrame.display("--- general commands ---" + EOL);
        clientFrame.display("ls                   list collection contents" + EOL);
        clientFrame.display("cd [collection|..]   change current collection" + EOL);
        clientFrame.display("put [file pattern]   upload file or directory to the database" + EOL);
        clientFrame.display("putgz [file pattern] upload possibly gzip compressed file or directory to the database" + EOL);
        clientFrame.display("putzip [file pattern] upload the contents of a ZIP archive to the database" + EOL);
        clientFrame.display("edit [resource] open the resource for editing" + EOL);
        clientFrame.display("mkcol collection     create new sub-collection in current collection" + EOL);
        clientFrame.display("rm document          remove document from current collection" + EOL);
        clientFrame.display("rmcol collection     remove collection" + EOL);
        clientFrame.display("set [key=value]      set property. Calling set without " + EOL);
        clientFrame.display("                     argument shows current settings." + EOL);
        clientFrame.display(EOL + "--- search commands ---" + EOL);
        clientFrame.display("find xpath-expr      execute the given XPath expression." + EOL);
        clientFrame.display("show [position]      display query result value at position." + EOL);
        clientFrame.display(EOL + "--- user management (may require dba rights) ---" + EOL);
        clientFrame.display("users                list existing users." + EOL);
        clientFrame.display("adduser username     create a new user." + EOL);
        clientFrame.display("passwd username      change password for user. " + EOL);
        clientFrame.display("chown user group [resource]" + EOL);
        clientFrame.display("                     change resource ownership. chown without" + EOL);
        clientFrame.display("                     resource changes ownership of the current" + EOL);
        clientFrame.display("                     collection." + EOL);
        clientFrame.display("chmod [resource] permissions" + EOL);
        clientFrame.display("                     change resource permissions. Format:" + EOL);
        clientFrame.display("                     [user|group|other]=[+|-][read|write|execute]." + EOL);
        clientFrame.display("                     chmod without resource changes permissions for" + EOL);
        clientFrame.display("                     the current collection." + EOL);
        clientFrame.display("lock resource        put a write lock on the specified resource." + EOL);
        clientFrame.display("unlock resource      remove a write lock from the specified resource." + EOL);
        clientFrame.display("svn                  subversion command-line client." + EOL);
        clientFrame.display("threads              threads debug information." + EOL);
        clientFrame.display("quit                 quit the program" + EOL);

        replay(collection, mgtService, perm, resource, clientFrame, account, group);
        assertThatNoException().isThrownBy(client::displayHelp);
    }

    @Test
    void readQueryHistory(@TempDir Path tempDir) throws IOException {
        client.queryHistoryFile = tempDir.resolve(".exist_query_history");
        replay(collection, mgtService, perm, resource, clientFrame, account, group);
        assertThatNoException().isThrownBy(client::readQueryHistory);

        assertThat(client.queryHistory).isEmpty();
        ArrayList<String> content = new ArrayList<>();
        content.add("<history>");
        content.add("<query>query one</query>");
        content.add("<query>query two</query>");
        content.add("</history>");
        Files.write(client.queryHistoryFile, content);
        assertThatNoException().isThrownBy(client::readQueryHistory);
        assertThat(client.queryHistory).containsExactly("query one", "query two");
    }

    @Test
    void writeQueryHistory(@TempDir Path tempDir) throws IOException {
        Path historyFile = tempDir.resolve(".exist_query_history");
        client.console = LineReaderBuilder.builder().variable(LineReader.HISTORY_FILE, historyFile).build();
        client.queryHistoryFile = historyFile;
        for (int index = 0; index < 21; index++) {
            client.queryHistory.add("query" + index);
        }
        replay(collection, mgtService, perm, resource, clientFrame, account, group);

        assertThat(historyFile).doesNotExist();
        assertThatNoException().isThrownBy(client::writeQueryHistory);
        assertThat(historyFile).exists();
        String expected = "<history><query>query1</query><query>query2</query><query>query3</query>" +
                "<query>query4</query><query>query5</query><query>query6</query><query>query7</query>" +
                "<query>query8</query><query>query9</query><query>query10</query><query>query11</query>" +
                "<query>query12</query><query>query13</query><query>query14</query><query>query15</query>" +
                "<query>query16</query><query>query17</query><query>query18</query><query>query19</query>" +
                "<query>query20</query></history>";
        assertThat(Files.readAllLines(historyFile)).containsExactly(expected);
    }

    @Test
    void getNotice() {
        BinaryOperator<String> propertyAction = createMock(BinaryOperator.class);

        expect(propertyAction.apply("product-name", "eXist-db")).andReturn("eXist-db");
        expect(propertyAction.apply("product-version", "unknown")).andReturn("testVersion");
        expect(propertyAction.apply("git-commit", "")).andReturn("gitCommitId");

        replay(collection, mgtService, perm, resource, clientFrame, account, group, propertyAction);

        String expected = "eXist-db version testVersion (gitCommitId), Copyright (C) 2001-" +
                Calendar.getInstance().get(Calendar.YEAR) + " The eXist-db Project" + EOL +
                "eXist-db comes with ABSOLUTELY NO WARRANTY." + EOL +
                "This is free software, and you are welcome to redistribute it" + EOL +
                "under certain conditions; for details read the license file." + EOL;
        assertThat(client.getNotice(propertyAction)).isNotNull().isEqualTo(expected);
    }

    @Test
    void message() {
        clientFrame.display("message1");
        replay(collection, mgtService, perm, resource, clientFrame, account, group);
        assertThatNoException().isThrownBy(() -> client.message("message1"));
        client.frame = null;
        assertThatNoException().isThrownBy(() -> client.message("message2"));
    }

    @Test
    void messageln() {
        clientFrame.display("message1" + EOL);
        replay(collection, mgtService, perm, resource, clientFrame, account, group);
        assertThatNoException().isThrownBy(() -> client.messageln("message1"));
        client.frame = null;
        assertThatNoException().isThrownBy(() -> client.messageln("message2"));
    }

    @Test
    void errorln() {
        clientFrame.display("message1" + EOL);
        replay(collection, mgtService, perm, resource, clientFrame, account, group);
        assertThatNoException().isThrownBy(() -> client.errorln("message1"));
        client.frame = null;
        assertThatNoException().isThrownBy(() -> client.errorln("message2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"xxxxInvalid password for userXXXX", "YYYYYConnection refused: connectXXXXX", "XXXUser AAAA unknownXXXX"})
    void isRetryableError(String errorMessage) {
        assertThat(client.isRetryableError(errorMessage)).isTrue();
    }

    @Test
    void runInteractive() throws Exception {
        client = new InteractiveClient(CommandlineOptions.parse(new String[0])) {
            @Override
            ClientFrame createClientFrame() {
                return clientFrame;
            }

            @Override
            protected void connect() throws Exception {
                current = collection;
            }

            @Override
            boolean getGuiLoginData(Properties props, UnaryOperator<Properties> loginPropertyOperator) {
                return true;
            }
        };

        clientFrame.setLocation(100, 100);
        clientFrame.setSize(500, 500);
        clientFrame.setVisible(true);
        collection.setProperty(eq("configuration"), endsWith(FILE_SEPARATOR + "conf.xml"));
        collection.setProperty("uri", "xmldb:exist://localhost:8080/exist/xmlrpc");
        recordResourceData(false);
        clientFrame.setResources(notNull());
        clientFrame.display(EOL + "type help or ? for help." + EOL);
        clientFrame.displayPrompt();

        replay(collection, mgtService, perm, resource, clientFrame, account, group);

        assertThatNoException().isThrownBy(client::run);
    }

    private void recordResourceData(boolean withPermission) throws XMLDBException {
        expect(collection.getService(UserManagementService.class)).andReturn(mgtService);
        expect(collection.listChildCollections()).andReturn(asList("col1", "col2"));
        expect(collection.listResources()).andReturn(asList("res1", "res2"));
        // collection 1
        expect(mgtService.getSubCollectionPermissions(collection, "col1")).andReturn(perm);
        expect(mgtService.getSubCollectionCreationTime(collection, "col1")).andReturn(Instant.ofEpochMilli(1000));
        recordPermissions(withPermission);
        // collection 2
        expect(mgtService.getSubCollectionPermissions(collection, "col2")).andReturn(perm);
        expect(mgtService.getSubCollectionCreationTime(collection, "col2")).andReturn(Instant.ofEpochMilli(2000));
        recordPermissions(withPermission);
        // resource 1
        expect(collection.getResource("res1")).andReturn(resource);
        expect(mgtService.getPermissions(resource)).andReturn(perm);
        expect(resource.getLastModificationTime()).andReturn(Instant.ofEpochMilli(3000));
        recordPermissions(withPermission);
        resource.close();
        // resource 2
        expect(collection.getResource("res2")).andReturn(resource);
        expect(mgtService.getPermissions(resource)).andReturn(perm);
        expect(resource.getLastModificationTime()).andReturn(Instant.ofEpochMilli(4000));
        recordPermissions(withPermission);
        resource.close();
    }

    private void recordPermissions(boolean withPermission) {
        if (!withPermission) {
            return;
        }
        expect(perm.getOwner()).andReturn(account);
        expect(account.getName()).andReturn("user");
        expect(perm.getGroup()).andReturn(group);
        expect(group.getName()).andReturn("group");
    }
}