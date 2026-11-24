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
package org.exist.storage;

import org.exist.TestUtils;
import org.exist.backup.Backup;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.EXistRestoreService;
import org.exist.xmldb.NullRestoreServiceTaskListener;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.xmldb.api.DatabaseManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The attribute types a DTD declares ({@code ID}, {@code IDREF}, {@code IDREFS}) are part of what the database keeps
 * of a document. A document stores the types of its attributes, but not the declarations that gave them, so the
 * types were lost as soon as the document was parsed again: after a backup and restore, or an export and a store.
 * The internal subset of the DOCTYPE is parsed together with the document, and the types of an internal declaration
 * must not depend on which other document with the same external DTD was stored before.
 */
public class DtdAttributeTypesRoundTripTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @TempDir
    Path temporaryFolder;

    private static final String INTERNAL_SUBSET = """
            <!DOCTYPE r [<!ELEMENT r (c*)><!ELEMENT c EMPTY>
            <!ATTLIST c id ID #IMPLIED ref IDREF #IMPLIED refs IDREFS #IMPLIED tok NMTOKEN #IMPLIED>
            <!ENTITY e "entity value">]>
            <r><c id="x"/><c id="y" ref="x" refs="x y" tok="t"/>&e;</r>""";

    private static String run(final String query) throws Exception {
        return DtdAttributeTypesTestSupport.run(existEmbeddedServer, query);
    }

    private static String content(final String collection, final String name) throws Exception {
        return DtdAttributeTypesTestSupport.content(collection, name);
    }

    private static String store(final String collection, final String name, final String xml) throws Exception {
        return run("xmldb:store('" + collection + "', '" + name + "', \"" + xml.replace("\"", "\"\"").replace("&", "&amp;") + "\", 'application/xml')");
    }

    private static String counts(final String function, final String collection, final String... values) throws Exception {
        final StringBuilder query = new StringBuilder("string-join((");
        for (int i = 0; i < values.length; i++) {
            query.append(i == 0 ? "" : ", ").append("'").append(values[i]).append("=' || count(for $d in collection('")
                    .append(collection).append("') return ").append(function).append("('").append(values[i]).append("', $d))");
        }
        return run(query.append("), ' ')").toString());
    }

    @Test
    void typesAreFoundAfterTheDocumentIsStored() throws Exception {
        run("xmldb:create-collection('/db', 'dta-stored')");
        store("/db/dta-stored", "d.xml", INTERNAL_SUBSET);
        assertEquals("x=1 y=1", counts("id", "/db/dta-stored", "x", "y"));
        assertEquals("x=2 y=1", counts("idref", "/db/dta-stored", "x", "y"));
    }

    @Test
    void theSerializedDocumentCarriesTheDeclaredTypes() throws Exception {
        run("xmldb:create-collection('/db', 'dta-serialized')");
        store("/db/dta-serialized", "d.xml", INTERNAL_SUBSET);
        final String xml = content("/db/dta-serialized", "d.xml");
        System.out.println("DTA-SERIALIZED " + xml.replace("\n", " "));
        assertTrue(xml.contains("<!ATTLIST c id ID #IMPLIED>"), xml);
        assertTrue(xml.contains("<!ATTLIST c ref IDREF #IMPLIED>"), xml);
        assertTrue(xml.contains("<!ATTLIST c refs IDREFS #IMPLIED>"), xml);
        assertTrue(!xml.contains("NMTOKEN"), "only the types the database keeps: " + xml);
    }

    @Test
    void aCollectionCopyKeepsTheDeclaredTypes() throws Exception {
        run("xmldb:create-collection('/db', 'dta-copy-source')");
        run("xmldb:create-collection('/db', 'dta-copy-target')");
        store("/db/dta-copy-source", "d.xml", INTERNAL_SUBSET);
        run("xmldb:copy-collection('/db/dta-copy-source', '/db/dta-copy-target')");
        assertEquals("x=1 y=1", counts("id", "/db/dta-copy-target", "x", "y"));
        assertEquals(content("/db/dta-copy-source", "d.xml"), content("/db/dta-copy-target/dta-copy-source", "d.xml"));
    }

    /** An export that is stored again gives the same export: the declarations that are written are the ones that are kept. */
    @Test
    void anExportThatIsStoredAgainGivesTheSameExport() throws Exception {
        run("xmldb:create-collection('/db', 'dta-stable')");
        store("/db/dta-stable", "a.xml", INTERNAL_SUBSET);
        final String first = content("/db/dta-stable", "a.xml");

        store("/db/dta-stable", "b.xml", first);
        assertEquals(first, content("/db/dta-stable", "b.xml"));
        assertEquals("x=2 y=2", counts("id", "/db/dta-stable", "x", "y"), "both documents are typed");
    }

    @Test
    void typesSurviveBackupAndRestore() throws Exception {
        DatabaseManager.registerDatabase(new DatabaseImpl());
        run("xmldb:create-collection('/db', 'dta-backup')");
        store("/db/dta-backup", "d.xml", INTERNAL_SUBSET);
        store("/db/dta-backup", "plain.xml", "<r xml:id=\"b1\"/>");

        final Path backupDir = Files.createDirectories(temporaryFolder.resolve("backup"));
        new Backup(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD, backupDir, XmldbURI.EMBEDDED_SERVER_URI.append("/db/dta-backup"), new Properties(), false)
                .backup(false, null);
        final Path contents = backupDir.resolve("db").resolve("dta-backup").resolve("__contents__.xml");
        assertTrue(Files.exists(contents), "the backup was written");

        run("xmldb:remove('/db/dta-backup')");
        run("xmldb:create-collection('/db', 'dta-backup')");
        assertEquals("x=0 y=0", counts("id", "/db/dta-backup", "x", "y"), "control: nothing left before the restore");

        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        root.getService(EXistRestoreService.class).restore(contents.toAbsolutePath().toString(), null, new NullRestoreServiceTaskListener(), false);

        assertEquals("2", run("count(collection('/db/dta-backup'))"), "both documents are back");
        assertEquals("x=1 y=1", counts("id", "/db/dta-backup", "x", "y"), "the IDs declared by the DTD are found again");
        assertEquals("x=2 y=1", counts("idref", "/db/dta-backup", "x", "y"), "and the references");
        assertEquals("b1=1", counts("id", "/db/dta-backup", "b1"), "xml:id as before");
        assertEquals("entity value", run("string(doc('/db/dta-backup/d.xml')/r/text()[normalize-space()][1])"), "the entity was expanded when the document was stored");
    }

    /**
     * The grammar cache keys a DTD by its system id, so a document with an internal subset on top of an external DTD
     * got the declarations of the first document that used the same system id.
     */
    @Test
    void theInternalDeclarationsDoNotDependOnWhichDocumentWasStoredFirst() throws Exception {
        final String dtd = "<!ELEMENT r (c*)><!ELEMENT c EMPTY><!ATTLIST c eid ID #IMPLIED>";
        for (final String order : new String[] {"ab", "ba"}) {
            final String collection = "dta-order-" + order;
            run("xmldb:create-collection('/db', '" + collection + "')");
            run("xmldb:store('/db/" + collection + "', 'order-" + order + ".dtd', \"" + dtd + "\", 'application/xml-dtd')");
            for (final char which : order.toCharArray()) {
                final String attribute = which == 'a' ? "aid" : "bid";
                store("/db/" + collection, which + ".xml", "<!DOCTYPE r SYSTEM 'order-" + order + ".dtd' [<!ATTLIST c " + attribute
                        + " ID #IMPLIED>]><r><c " + attribute + "='" + which + "1'/></r>");
            }
            assertEquals("a1=1 b1=1", counts("id", "/db/" + collection, "a1", "b1"), "stored in the order " + order);
        }
    }
}
