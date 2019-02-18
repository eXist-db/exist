/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2010 The eXist Project
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

import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;


/**
 * Implementation of BackupWriter that writes to the file system.
 */
public class FileSystemWriter implements BackupWriter {
    private final Path rootDir;
    private final Path blobDir;
    private Path currentDir;
    private Path currentContents;
    private Writer currentContentsOut;
    private OutputStream currentOut;
    private boolean dataWritten = false;

    public FileSystemWriter(final String path) throws IOException {
        this(Paths.get(path));
    }

    public FileSystemWriter(final Path file) throws IOException {
        if (Files.exists(file)) {

            //removing "path"
            FileUtils.deleteQuietly(file);
        }

        currentDir = file;
        rootDir = file;
        blobDir = file.resolve("blob");

        Files.createDirectories(blobDir);
    }

    @Override
    public void newCollection(final String name) throws IOException {
        final Path file;
        if (XmldbURI.createInternal(name).isAbsolute()) {
            file = rootDir.resolve(name.replaceAll("^/?(.*)", "$1"));
        } else {
            file = currentDir.resolve(name.replaceAll("^/?(.*)", "$1"));
        }

        if (Files.exists(file)) {
            FileUtils.deleteQuietly(file);
        }
        Files.createDirectories(file);
        dataWritten = true;
        currentDir = file;
    }

    @Override
    public void closeCollection() {
        currentDir = currentDir.getParent();
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public Writer newContents() throws IOException {
        currentContents = currentDir.resolve("__contents__.xml");
        currentContentsOut = Files.newBufferedWriter(currentContents, StandardCharsets.UTF_8);
        dataWritten = true;
        return (currentContentsOut);
    }

    @Override
    public void closeContents() throws IOException {
        currentContentsOut.close();
    }

    @Override
    public OutputStream newEntry(final String name) throws IOException {
        currentOut = Files.newOutputStream(currentDir.resolve(name));
        dataWritten = true;
        return (currentOut);
    }

    @Override
    public OutputStream newBlobEntry(final String blobId) throws IOException {
        currentOut = Files.newOutputStream(blobDir.resolve(blobId));
        dataWritten = true;
        return currentOut;
    }

    @Override
    public void closeEntry() throws IOException {
        currentOut.close();
    }

    @Override
    public void setProperties(final Properties properties) throws IOException {
        if (dataWritten) {
            throw (new IOException("Backup properties need to be set before any backup data is written"));
        }
        final Path propFile = rootDir.resolve("backup.properties");
        try (final OutputStream os = Files.newOutputStream(propFile)) {
            properties.store(os, "Backup properties");
        }
    }
}
