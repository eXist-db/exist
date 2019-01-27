/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.backup;

import org.exist.repo.RepoBackup;
import org.exist.util.EXistInputSource;
import org.exist.util.FileInputSource;
import org.exist.util.FileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;


public class FileSystemBackupDescriptor extends AbstractBackupDescriptor {
    protected Path descriptor;

    public FileSystemBackupDescriptor(final Path theDesc) throws FileNotFoundException {
        if (!FileUtils.fileName(theDesc).equals(BackupDescriptor.COLLECTION_DESCRIPTOR) || Files.isDirectory(theDesc) || !Files.isReadable(theDesc)) {
            throw (new FileNotFoundException(theDesc.toAbsolutePath().toString() + " is not a valid collection descriptor"));
        }
        descriptor = theDesc;

        // Count number of files
        countFileEntries(theDesc);
    }

    @Override
    public BackupDescriptor getChildBackupDescriptor(final String describedItem) {
        final Path child = descriptor.getParent().resolve(describedItem).resolve(BackupDescriptor.COLLECTION_DESCRIPTOR);
        BackupDescriptor bd = null;

        try {
            bd = new FileSystemBackupDescriptor(child);
        } catch (final FileNotFoundException fnfe) {
            // DoNothing(R)
        }
        return (bd);
    }

    @Override
    public BackupDescriptor getBackupDescriptor(final String describedItem) {
        final String topDir = descriptor.getParent().getParent().toAbsolutePath().toString();
        final String subDir = topDir + describedItem;
        final String desc = subDir + '/' + BackupDescriptor.COLLECTION_DESCRIPTOR;
        BackupDescriptor bd = null;

        try {
            bd = new FileSystemBackupDescriptor(Paths.get(desc));
        } catch (final FileNotFoundException fnfe) {
            // DoNothing(R)
        }
        return (bd);
    }

    @Override
    public EXistInputSource getInputSource() {
        return new FileInputSource(descriptor);
    }

    @Override
    public EXistInputSource getInputSource(final String describedItem) {
        final Path child = descriptor.getParent().resolve(describedItem);
        EXistInputSource is = null;

        if ((!Files.isDirectory(child)) && Files.isReadable(child)) {
            is = new FileInputSource(child);
        }

        return (is);
    }

    @Override
    public String getSymbolicPath() {
        return (descriptor.toAbsolutePath().toString());
    }

    @Override
    public String getSymbolicPath(final String describedItem, final boolean isChildDescriptor) {
        Path resbase = descriptor.getParent().resolve(describedItem);

        if (isChildDescriptor) {
            resbase = resbase.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR);
        }
        return (resbase.toAbsolutePath().toString());
    }

    @Override
    public Properties getProperties() throws IOException {
        Path dir = descriptor.getParent();

        if (dir != null) {
            dir = dir.getParent();
            if (dir != null) {
                final Path propFile = dir.resolve(BACKUP_PROPERTIES);
                if (Files.exists(propFile)) {
                    final Properties properties = new Properties();
                    try (final InputStream is = Files.newInputStream(propFile)) {
                        properties.load(is);
                    }
                    return properties;
                }
            }
        }
        return null;
    }

    @Override
    public Path getParentDir() {
        return (descriptor.getParent().getParent().getParent());
    }

    @Override
    public String getName() {
        return (FileUtils.fileName(descriptor.getParent().getParent()));
    }

    @Override
    public Path getRepoBackup() throws IOException {
        final Path archive = descriptor.getParent().getParent().resolve(RepoBackup.REPO_ARCHIVE);
        if (Files.exists(archive)) {
            return archive;
        }
        return null;
    }

    private void countFileEntries(Path file) {

        // Only count files from top level.
        if(!file.toString().endsWith("/db/" + COLLECTION_DESCRIPTOR)){
            return;
        }

        try {
            numberOfFiles = Files.walk(file.getParent())
                    .filter(f -> !Files.isDirectory(f))
                    .filter(f -> !COLLECTION_DESCRIPTOR.equals(f.getFileName().toString()))
                    .count();

        } catch (IOException e) {
            // Swallow
        }
    }

}
