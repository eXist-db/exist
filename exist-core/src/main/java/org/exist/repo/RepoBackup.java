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
package org.exist.repo;

import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.util.FileUtils;
import org.exist.util.io.TemporaryFileManager;
import org.exist.xmldb.XmldbURI;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility methods for backing up and restoring the expath file system repository.
 */
public class RepoBackup {

    public final static String REPO_ARCHIVE = "expathrepo.zip";

    public static Path backup(final DBBroker broker) throws IOException {
        final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
        final Path tempFile = temporaryFileManager.getTemporaryFile();
        try(final ZipOutputStream os = new ZipOutputStream(Files.newOutputStream(tempFile))) {
            final Path directory = ExistRepository.getRepositoryDir(broker.getConfiguration());
            zipDir(directory.toAbsolutePath(), os, "");
        }
        return tempFile;
    }

    public static void restore(final Txn transaction, final DBBroker broker) throws IOException, PermissionDeniedException {
        final XmldbURI docPath = XmldbURI.createInternal(XmldbURI.ROOT_COLLECTION + "/" + REPO_ARCHIVE);
        try(final LockedDocument lockedDoc = broker.getXMLResource(docPath, LockMode.READ_LOCK)) {
            if (lockedDoc == null) {
                return;
            }

            final DocumentImpl doc = lockedDoc.getDocument();
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                throw new IOException(docPath + " is not a binary resource");
            }

            try (final InputStream is = broker.getBrokerPool().getBlobStore().get(transaction,  ((BinaryDocument)doc).getBlobId())) {
                final Path directory = ExistRepository.getRepositoryDir(broker.getConfiguration());
                unzip(doc.getURI(), is, directory);
            }
        }
    }

    /**
     * Zip up a directory path
     *
     * @param directory the directory to zip
     * @param zos output stream for the zip entries
     * @param path prefix path to be prepended to entries
     * @throws IOException in case of an io error zipping the directory
     */
    public static void zipDir(final Path directory, final ZipOutputStream zos, final String path) throws IOException {
        // get a listing of the directory content
        final List<Path> dirList = FileUtils.list(directory);

        // loop through dirList, and zip the files
        for (final Path f : dirList) {
            if (Files.isDirectory(f)) {
                zipDir(f, zos, path + FileUtils.fileName(f) + "/");
                continue;
            }

            final ZipEntry anEntry = new ZipEntry(path + FileUtils.fileName(f));
            zos.putNextEntry(anEntry);
            Files.copy(f, zos);
        }
    }

    /***
     * Extract zipfile to outdir with complete directory structure.
     *
     * @param fileUri the file URI
     * @param file Input .zip file
     * @param outdir Output directory
     * @throws IOException in case of an error unzipping the file
     */
    public static void unzip(final XmldbURI fileUri, final InputStream file, final Path outdir) throws IOException {
        try (final ZipInputStream zin = new ZipInputStream(file)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                final String name = entry.getName();
                final Path out = outdir.resolve(name);

                if (!out.startsWith(outdir)) {
                    throw new IOException("Detected archive exit attack! zipFile=" + fileUri.getRawCollectionPath() + ", entry=" + name + ", outdir=" + outdir.toAbsolutePath().normalize());
                }

                if (entry.isDirectory() ) {
                    Files.createDirectories(outdir.resolve(name));
                    continue;
                }

                final String dir = dirpart(name);
                if (dir != null) {
                    Files.createDirectories(outdir.resolve(name));
                }

                //extract file
                Files.copy(zin, outdir.resolve(name));
            }
        }
    }

    private static String dirpart(final String name) {
        final int s = name.lastIndexOf(File.separatorChar);
        return s == -1 ? null : name.substring( 0, s );
    }
}
