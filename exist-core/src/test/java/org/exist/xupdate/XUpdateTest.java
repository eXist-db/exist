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
package org.exist.xupdate;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistXmldbEmbeddedServer;

import org.exist.util.LockException;
import org.exist.xmldb.UserManagementService;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertFalse;

import org.junit.runners.Parameterized.Parameters;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XUpdateQueryService;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

/**
 * @author berlinge-to
 */
@RunWith(Parameterized.class)
public class XUpdateTest {

    @Rule
    public final ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    //TODO should not execute as 'admin' user
    //also additional tests needed to verify update permissions

    @Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"append", "address.xml"},
                {"insert_after", "address.xml"},
                {"insert_before", "address.xml"},
                {"remove", "address.xml"},
                {"update", "address.xml"},
                {"append_attribute", "address.xml"},
                {"append_child", "address.xml"},
                {"insert_after_big", "address_big.xml"},
                {"conditional", "address.xml"},
                {"variables", "address.xml"},
                {"replace", "address.xml"},
                {"whitespace", "address.xml"},
                {"namespaces", "namespaces.xml"},

                /* TODO Added by Geoff Shuetrim (geoff@galexy.net) on 15 July 2006
                to highlight that root element renaming does not currently succeed,
                resulting instead in a null pointer exception because the renaming
                relies upon obtaining the parent element of the element being
                renamed and this is null for the root element. */
                {"rename_root_element", "address.xml"},

                /* TODO Added by Geoff Shuetrim (geoff@galexy.net) on 15 July 2006
                to highlight that renaming of an element fails when the renaming also
                involves a change of namespace */
                {"rename_including_namespace", "namespaces.xml"}
        });
    }

    @Parameter
    public String testName;

    @Parameter(value = 1)
    public String sourceFile;

    private final static String XUPDATE_COLLECTION = "xupdate_tests";

    private final static String MODIFICATION_DIR_NAME = "modifications";
    private final static String RESULT_DIR_NAME = "results";
    private final static String SOURCE_DIR_NAME = "input";
    private final static String XUPDATE_FILE = "xu.xml";       // xml document name in eXist

    private Collection col = null;

    @Test
    public void xupdate() throws Exception {

        //skip tests from Geoff Shuetrim (see above!)
        Assume.assumeThat(testName, not(anyOf(equalTo("rename_root_element"), equalTo("rename_including_namespace"))));

        //update input xml file
        final Path modFile = getRelFile(MODIFICATION_DIR_NAME + "/" + testName + ".xml");
        final String xupdateResult = updateDocument(modFile);

        //Read reference xml file
        final Path resultFile = getRelFile(RESULT_DIR_NAME + "/" + testName + ".xml");

        //compare
        final Source expectedDoc = Input.fromFile(resultFile.toFile()).build();
        final Source actualDoc = Input.fromString(xupdateResult).build();
        final Diff diff = DiffBuilder.compare(expectedDoc)
                .withTest(actualDoc)
                .ignoreWhitespace()
                .checkForIdentical()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    private void addDocument(final String sourceFile) throws XMLDBException, IOException, URISyntaxException {
        final Path f = getRelFile(SOURCE_DIR_NAME + "/" + sourceFile);

        final XMLResource document = col.createResource(XUPDATE_FILE, XMLResource.class);
        document.setContent(f);
        col.storeResource(document);
    }

    private Path getRelFile(final String relPath) throws IOException, URISyntaxException {
        final URL url = getClass().getResource(relPath);
        if (url == null) {
            throw new IOException("Can't find file: " + relPath);
        }

        final Path f = Paths.get(url.toURI());
        if (!Files.isReadable(f)) {
            throw new IOException("Can't read file: " + url);
        }

        return f;
    }

    private void removeDocument() throws XMLDBException {
        final Resource document = col.getResource(XUPDATE_FILE);
        col.removeResource(document);
    }

    /**
     * @return resultant XML document as a String
     */
    private String updateDocument(final Path updateFile) throws XMLDBException, IOException, ParserConfigurationException, SAXException {
        final XUpdateQueryService service = col.getService(XUpdateQueryService.class);

        // Read XUpdate-Modifcations
        final String xUpdateModifications = Files.readString(updateFile);

        service.update(xUpdateModifications);

        final XMLResource ret = (XMLResource) col.getResource(XUPDATE_FILE);
        return ((String) ret.getContent());
    }

    @Before
    public void startup() throws XMLDBException, IOException, URISyntaxException {
        col = existXmldbEmbeddedServer.getRoot().getChildCollection(XUPDATE_COLLECTION);

        if (col == null) {
            final CollectionManagementService collectionManagementService = existXmldbEmbeddedServer.getRoot().getService(CollectionManagementService.class);
            col = collectionManagementService.createCollection(XUPDATE_COLLECTION);
            final UserManagementService ums = col.getService(UserManagementService.class);
            // change ownership to guest
            final Account guest = ums.getAccount("guest");
            ums.chown(guest, guest.getPrimaryGroup());
            ums.chmod(Permission.DEFAULT_COLLECTION_PERM);
        }

        addDocument(sourceFile);
    }

    @After
    public void shutdown() throws XMLDBException, LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        removeDocument();

        TestUtils.cleanupDB();
        col.close();
    }
}
