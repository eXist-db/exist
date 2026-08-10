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
package org.exist.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FileSystemBackupDescriptorTest {

    private static final String CONTENTS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <exist:collection xmlns:exist="http://exist.sourceforge.net/NS/exist">
                <exist:resource name="doc1.xml" type="XMLResource" filename="doc1.xml"/>
                <exist:resource name="test.binary" type="BinaryResource" filename="test.binary"/>
            </exist:collection>
            """;

    private static final String NESTED_CONTENTS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <exist:collection xmlns:exist="http://exist.sourceforge.net/NS/exist">
                <exist:resource name="nested.xml" type="XMLResource" filename="nested.xml"/>
            </exist:collection>
            """;

    @Test
    void countsResourcesFromContentsXmlOnly(@TempDir final Path tempDir) throws Exception {
        final Path backupRoot = tempDir.resolve("backup");
        final Path dbDir = backupRoot.resolve("db");
        Files.createDirectories(dbDir);

        Files.writeString(dbDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), CONTENTS_XML);
        Files.writeString(dbDir.resolve("doc1.xml"), "<test/>");
        Files.write(dbDir.resolve("test.binary"), "test".getBytes(UTF_8));

        final FileSystemBackupDescriptor descriptor = new FileSystemBackupDescriptor(
                backupRoot,
                dbDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR));

        assertEquals(2, descriptor.getNumberOfFiles());
    }

    @Test
    void rootDescriptorCountsAllDescendantResources(@TempDir final Path tempDir) throws Exception {
        final Path backupRoot = tempDir.resolve("backup");
        final Path dbDir = backupRoot.resolve("db");
        final Path nestedDir = dbDir.resolve("nested");
        Files.createDirectories(nestedDir);

        Files.writeString(dbDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), CONTENTS_XML);
        Files.writeString(nestedDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), NESTED_CONTENTS_XML);
        Files.writeString(dbDir.resolve("doc1.xml"), "<test/>");
        Files.write(dbDir.resolve("test.binary"), "test".getBytes(UTF_8));
        Files.writeString(nestedDir.resolve("nested.xml"), "<nested/>");

        final FileSystemBackupDescriptor descriptor = new FileSystemBackupDescriptor(
                backupRoot,
                dbDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR));

        // recursive: root counts its own 2 resources plus the nested collection's 1
        assertEquals(3, descriptor.getNumberOfFiles());
    }

    @Test
    void restoreProgressTotalIsRootDescriptorCount(@TempDir final Path tempDir) throws Exception {
        // Restore.restore() uses only the root /db descriptor's count for totalNrOfFiles.
        // The root must recursively include /db/system so that system resources are not
        // excluded from the progress total.
        final Path backupRoot = tempDir.resolve("backup");
        final Path dbDir = Files.createDirectories(backupRoot.resolve("db"));
        final Path systemDir = Files.createDirectories(dbDir.resolve("system"));

        final String dbContents = """
                <?xml version="1.0" encoding="UTF-8"?>
                <exist:collection xmlns:exist="http://exist.sourceforge.net/NS/exist">
                    <exist:resource name="r0.xml" type="XMLResource" filename="r0.xml"/>
                    <exist:resource name="r1.xml" type="XMLResource" filename="r1.xml"/>
                    <exist:resource name="r2.xml" type="XMLResource" filename="r2.xml"/>
                </exist:collection>
                """;
        final String systemContents = """
                <?xml version="1.0" encoding="UTF-8"?>
                <exist:collection xmlns:exist="http://exist.sourceforge.net/NS/exist">
                    <exist:resource name="s0.xml" type="XMLResource" filename="s0.xml"/>
                    <exist:resource name="s1.xml" type="XMLResource" filename="s1.xml"/>
                    <exist:resource name="s2.xml" type="XMLResource" filename="s2.xml"/>
                    <exist:resource name="s3.xml" type="XMLResource" filename="s3.xml"/>
                    <exist:resource name="s4.xml" type="XMLResource" filename="s4.xml"/>
                </exist:collection>
                """;
        Files.writeString(dbDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), dbContents);
        Files.writeString(systemDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), systemContents);

        // root recursively includes /db/system: 3 + 5 = 8
        assertEquals(8,
                new FileSystemBackupDescriptor(backupRoot, dbDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR)).getNumberOfFiles(),
                "root descriptor must recursively include all subcollections for accurate restore progress");
    }

    @Test
    void rootDescriptorCountsNonSystemSubcollections(@TempDir final Path tempDir) throws Exception {
        // Restore pre-queues only system/security/groups; arbitrary collections like /db/apps and
        // /db/foo/stuff must be covered by the recursive root count or they are never counted.
        final Path backupRoot = tempDir.resolve("backup");
        final Path dbDir = Files.createDirectories(backupRoot.resolve("db"));
        final Path appsDir = Files.createDirectories(dbDir.resolve("apps"));
        final Path fooStuffDir = Files.createDirectories(dbDir.resolve("foo").resolve("stuff"));

        Files.writeString(dbDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), CONTENTS_XML);         // 2 resources
        Files.writeString(appsDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), NESTED_CONTENTS_XML); // 1 resource
        Files.writeString(fooStuffDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), NESTED_CONTENTS_XML); // 1 resource

        // 2 (db) + 1 (apps) + 1 (foo/stuff) = 4
        assertEquals(4,
                new FileSystemBackupDescriptor(backupRoot, dbDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR)).getNumberOfFiles(),
                "root descriptor must count all subcollections, not only system/security/groups");
    }

    @Test
    void collectionDescriptorCountsOwnSubtreeOnly(@TempDir final Path tempDir) throws Exception {
        final Path backupRoot = tempDir.resolve("backup");
        final Path dbDir = backupRoot.resolve("db");
        final Path nestedDir = dbDir.resolve("nested");
        Files.createDirectories(nestedDir);

        Files.writeString(dbDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), CONTENTS_XML);
        Files.writeString(nestedDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), NESTED_CONTENTS_XML);

        final FileSystemBackupDescriptor nestedDescriptor = new FileSystemBackupDescriptor(
                backupRoot,
                nestedDir.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR));

        assertEquals(1, nestedDescriptor.getNumberOfFiles());
    }
}
