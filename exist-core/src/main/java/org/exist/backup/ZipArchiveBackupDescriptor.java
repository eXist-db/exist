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
import org.exist.util.*;
import org.exist.util.io.TemporaryFileManager;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class ZipArchiveBackupDescriptor extends AbstractBackupDescriptor {

    private final static Logger LOG = LogManager.getLogger();

    protected ZipFile archive;
    protected ZipEntry descriptor;
    protected String base;
    protected final String blob = "blob/";

    public ZipArchiveBackupDescriptor(final Path fileArchive) throws IOException {

        archive = new ZipFile(fileArchive.toFile());

        //is it full backup?
        base = "db/";
        descriptor = archive.getEntry(base + BackupDescriptor.COLLECTION_DESCRIPTOR);

        if ((descriptor == null) || descriptor.isDirectory()) {

            base = null;

            //looking for highest collection
            //TODO: better to put some information on top?
            ZipEntry item = null;
            final Enumeration<? extends ZipEntry> zipEnum = archive.entries();

            while (zipEnum.hasMoreElements()) {
                item = zipEnum.nextElement();

                if (!item.isDirectory()) {

                    if (item.getName().endsWith(BackupDescriptor.COLLECTION_DESCRIPTOR)) {

                        if ((base == null) || (base.length() > item.getName().length())) {
                            descriptor = item;
                            base = item.getName();
                        }
                    }
                }
            }

            if (base != null) {
                base = base.substring(0, base.length() - BackupDescriptor.COLLECTION_DESCRIPTOR.length());
            }
        }

        if (descriptor == null) {
            throw new FileNotFoundException("Archive " + fileArchive.toAbsolutePath() + " is not a valid eXist backup archive");
        }

        final Path fakeDbRoot = Paths.get("/db");
        if (!fakeDbRoot.resolve(Paths.get(base)).normalize().startsWith(fakeDbRoot)) {
            throw new IOException("Detected archive exit attack! zipFile=" + fileArchive.toAbsolutePath().normalize());
        }

        // Count number of files
        countDescendantResourceEntries(archive, base);
    }


    private ZipArchiveBackupDescriptor(final ZipFile archive, final String base) throws FileNotFoundException {
        this.archive = archive;
        this.base = base;
        descriptor = archive.getEntry(base + BackupDescriptor.COLLECTION_DESCRIPTOR);

        if (descriptor == null || descriptor.isDirectory()) {
            throw new FileNotFoundException(archive.getName() + " is a bit corrupted (" + base + " descriptor not found): not a valid eXist backup archive");
        }

        // Count number of files
        countDescendantResourceEntries(archive, base);
    }

    @Override
    public BackupDescriptor getChildBackupDescriptor(final String describedItem) {
        BackupDescriptor bd = null;

        try {
            bd = new ZipArchiveBackupDescriptor(archive, base + describedItem + "/");
        } catch (final FileNotFoundException fnfe) {
            // DoNothing(R)
        }

        return bd;
    }

    @Override
    public BackupDescriptor getBackupDescriptor(String describedItem) {
        if ((!describedItem.isEmpty()) && (describedItem.charAt(0) == '/')) {
            describedItem = describedItem.substring(1);
        }

        if (!describedItem.endsWith("/")) {
            describedItem = describedItem + '/';
        }

        BackupDescriptor bd = null;

        try {
            bd = new ZipArchiveBackupDescriptor(archive, describedItem);
        } catch (final FileNotFoundException e) {
            // DoNothing(R)
        }

        return bd;
    }

    @Override
    public List<BackupDescriptor> getChildBackupDescriptors() {
        final Pattern ptnDescriptor = Pattern.compile("(" + base + "/[^/]+/" + ")" + BackupDescriptor.COLLECTION_DESCRIPTOR);
        final Matcher mtcDescriptor = ptnDescriptor.matcher("");
        try (final Stream<BackupDescriptor> entries = archive.stream()
                .map(zipEntry -> {
                    mtcDescriptor.reset(zipEntry.getName());
                    if (mtcDescriptor.matches()) {
                        try {
                            return Optional.<ZipArchiveBackupDescriptor>of(new ZipArchiveBackupDescriptor(archive, mtcDescriptor.group(1)));
                        } catch (final FileNotFoundException e) {
                            LOG.warn(e.getMessage(), e);
                            return Optional.<ZipArchiveBackupDescriptor>empty();
                        }
                    } else {
                        return Optional.<ZipArchiveBackupDescriptor>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)) {

            return entries.collect(Collectors.toList());
        }
    }


    @Override
    public EXistInputSource getInputSource() {
        return new ZipEntryInputSource(archive, descriptor);
    }

    @Override
    public EXistInputSource getInputSource(final String describedItem) {
        final ZipEntry ze = archive.getEntry(base + describedItem);
        EXistInputSource retval = null;

        if ((ze != null) && !ze.isDirectory()) {
            retval = new ZipEntryInputSource(archive, ze);
        }

        return retval;
    }

    @Override
    public EXistInputSource getBlobInputSource(final String blobId) {
        final ZipEntry ze = archive.getEntry(blob + blobId);
        EXistInputSource retval = null;

        if ((ze != null) && !ze.isDirectory()) {
            retval = new ZipEntryInputSource(archive, ze);
        }

        return retval;
    }

    @Override
    public String getSymbolicPath() {
        return archive.getName() + "#" + descriptor.getName();
    }

    @Override
    public String getSymbolicPath(final String describedItem, final boolean isChildDescriptor) {
        String retval = archive.getName() + "#" + base + describedItem;

        if (isChildDescriptor) {
            retval += "/" + BackupDescriptor.COLLECTION_DESCRIPTOR;
        }
        return retval;
    }

    @Override
    public Properties getProperties() throws IOException {
        Properties properties = null;
        final ZipEntry ze = archive.getEntry(BACKUP_PROPERTIES);

        if (ze != null) {
            properties = new Properties();
            try (final InputStream is = archive.getInputStream(ze)) {
                properties.load(is);
            }
        }
        return properties;
    }

    @Override
    public Path getRepoBackup() throws IOException {
        final ZipEntry ze = archive.getEntry(RepoBackup.REPO_ARCHIVE);

        if (ze == null) {
            return null;
        }
        final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
        final Path temp = temporaryFileManager.getTemporaryFile();
        try (final InputStream is = archive.getInputStream(ze)) {
            Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        return temp;
    }

    @Override
    public Path getParentDir() {
        return Paths.get(archive.getName()).getParent();
    }

    @Override
    public String getName() {
        return FileUtils.fileName(Paths.get(archive.getName()));
    }

    private void countDescendantResourceEntries(final ZipFile zipFile, final String base) {
        try {
            final DescriptorResourceCounter descriptorResourceCounter = new DescriptorResourceCounter();

            final Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.isDirectory()
                        && zipEntry.getName().startsWith(base)
                        && zipEntry.getName().endsWith(COLLECTION_DESCRIPTOR)) {

                    try (final InputStream is = zipFile.getInputStream(zipEntry)) {
                        numberOfFiles += descriptorResourceCounter.count(is);
                    }
                }
            }

        } catch (final IOException | ParserConfigurationException | SAXException e) {
            LOG.error("Unable to count number of files in {}.", zipFile.toString(), e);
        }
    }
}
