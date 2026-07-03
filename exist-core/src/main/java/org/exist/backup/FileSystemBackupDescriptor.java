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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.repo.RepoBackup;
import org.exist.util.EXistInputSource;
import org.exist.util.FileInputSource;
import org.exist.util.FileUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class FileSystemBackupDescriptor extends AbstractBackupDescriptor {

    private final static Logger LOG = LogManager.getLogger();

    protected Path root;
    protected Path descriptor;

    public FileSystemBackupDescriptor(final Path root, final Path descriptor) throws FileNotFoundException {
        if (!FileUtils.fileName(descriptor).equals(BackupDescriptor.COLLECTION_DESCRIPTOR) || Files.isDirectory(descriptor) || !Files.isReadable(descriptor)) {
            throw new FileNotFoundException(descriptor.toAbsolutePath() + " is not a valid collection descriptor");
        }
        this.descriptor = descriptor;
        this.root = root;

        // Count number of files
        countFileEntries(descriptor);
    }

    @Override
    public BackupDescriptor getChildBackupDescriptor(final String describedItem) {
        final Path child = descriptor.getParent().resolve(describedItem).resolve(BackupDescriptor.COLLECTION_DESCRIPTOR);
        BackupDescriptor bd = null;

        try {
            bd = new FileSystemBackupDescriptor(root, child);
        } catch (final FileNotFoundException fnfe) {
            // DoNothing(R)
        }
        return bd;
    }

    @Override
    public List<BackupDescriptor> getChildBackupDescriptors() {
        try {
            try (final Stream<BackupDescriptor> entries = Files.list(descriptor.getParent())
                    .filter(p -> Files.isDirectory(p) && Files.exists(p.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR)))
                    .map(p -> p.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR))
                    .map(p -> {
                        try {
                            return Optional.<BackupDescriptor>of(new FileSystemBackupDescriptor(root, p));
                        } catch (final FileNotFoundException e) {
                            // Do nothing
                            LOG.warn(e.getMessage(), e);
                            return Optional.<BackupDescriptor>empty();
                        }
                    })
                    .flatMap(Optional::stream)) {
                return entries.collect(Collectors.toList());
            }
        } catch (final IOException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public BackupDescriptor getBackupDescriptor(final String describedItem) {
        final String topDir = descriptor.getParent().getParent().toAbsolutePath().toString();
        final String subDir = topDir + describedItem;
        final String desc = subDir + '/' + BackupDescriptor.COLLECTION_DESCRIPTOR;
        BackupDescriptor bd = null;

        try {
            bd = new FileSystemBackupDescriptor(root, Path.of(desc));
        } catch (final FileNotFoundException fnfe) {
            // DoNothing(R)
        }
        return bd;
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

        return is;
    }

    @Override
    public EXistInputSource getBlobInputSource(final String blobId) {
        final Path blobFile = root.resolve("blob").resolve(blobId);

        EXistInputSource is = null;
        if((!Files.isDirectory(blobFile)) && Files.isReadable(blobFile)) {
            is = new FileInputSource(blobFile);
        }

        return is;
    }

    @Override
    public String getSymbolicPath() {
        return descriptor.toAbsolutePath().toString();
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

    /**
     * Count resources declared in {@link BackupDescriptor#COLLECTION_DESCRIPTOR} files under this
     * collection's subtree (same approach as {@link ZipArchiveBackupDescriptor}).
     * {@link org.exist.backup.Restore} uses only the root descriptor's count for the progress total,
     * so arbitrary subcollections (e.g. /db/apps, /db/foo/stuff) are included without double-counting
     * the pre-queued /db/system hierarchy.
     */
    private void countFileEntries(final Path descriptor) {
        final Path collectionDir = descriptor.getParent();
        if (collectionDir == null) {
            return;
        }
        try {
            final DescriptorResourceCounter descriptorResourceCounter = new DescriptorResourceCounter();
            try (final Stream<Path> walk = Files.walk(collectionDir)) {
                for (final Path contentsFile : walk
                        .filter(p -> !Files.isDirectory(p))
                        .filter(p -> COLLECTION_DESCRIPTOR.equals(p.getFileName().toString()))
                        .toList()) {
                    try (final InputStream is = Files.newInputStream(contentsFile)) {
                        numberOfFiles += descriptorResourceCounter.count(is);
                    }
                }
            }
        } catch (final IOException | ParserConfigurationException | SAXException e) {
            LOG.error("Unable to count number of files in {}.", descriptor.toString(), e);
        }
    }

}
