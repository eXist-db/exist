/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xmldb;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.util.FileUtils;
import org.exist.xmlrpc.RpcAPI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.exist.xmldb.RemoteCollection.MAX_UPLOAD_CHUNK;

public class RemoteRestoreService implements EXistRestoreService {

    private final XmlRpcClient client;

    public RemoteRestoreService(final XmlRpcClient client) {
        this.client = client;
    }

    @Override
    public String getName() {
        return "RestoreService";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void restore(final String backup, @Nullable final String newAdminPassword,
            final RestoreServiceTaskListener restoreListener, final boolean overwriteApps) throws XMLDBException {
        final Path backupPath = Paths.get(backup).normalize().toAbsolutePath();
        if (!Files.exists(backupPath)) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Backup does not exist: " + backupPath);
        }

        final String remoteFileName;
        final String backupFileName = FileUtils.fileName(backupPath);
        if (backupFileName.endsWith(".zip")) {
            remoteFileName = uploadBackupFile(backupPath);
        } else if (Files.isDirectory(backupPath)) {
            final Path tmpZipFile = zipBackupDir(backupPath);
            try {
                remoteFileName = uploadBackupFile(tmpZipFile);
            } finally {
                FileUtils.deleteQuietly(tmpZipFile);
            }
        } else if (backupFileName.equals("__contents__.xml")) {
            final Path tmpZipFile = zipBackupDir(backupPath.getParent());
            try {
                remoteFileName = uploadBackupFile(tmpZipFile);
            } finally {
                FileUtils.deleteQuietly(tmpZipFile);
            }
        } else {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Backup does not appear to be an eXist-db backup");
        }

        final String restoreTaskHandle;
        List<Object> params = new ArrayList<>();
        params.add(newAdminPassword);
        params.add(remoteFileName);
        params.add(overwriteApps);
        try {
            restoreTaskHandle = (String)client.execute("restore", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Unable to begin restore: " + e.getMessage());
        }

        // has the admin password changed?
        final XmlRpcClientConfigImpl config = (XmlRpcClientConfigImpl) client.getClientConfig();
        final String currentPassword = config.getBasicPassword();
        if (newAdminPassword != null && !currentPassword.equals(newAdminPassword)) {
            config.setBasicPassword(newAdminPassword);
        }
        try {
            Thread.sleep(3000);
        } catch (final InterruptedException e) {
            // restore interrupt status
            Thread.currentThread().interrupt();

            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e);
        }

        // now we need to poll for results...
        boolean finished = false;
        params = new ArrayList<>();
        params.add(restoreTaskHandle);
        while (!finished) {
            final List<String> events = new ArrayList<>();
            try {
                final Object[] results = (Object[]) client.execute("getRestoreTaskEvents", params);
                if (results != null) {
                    for (final Object result : results) {
                        events.add((String)result);
                    }
                }
            } catch (final XmlRpcException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e);
            }

            for (final String event : events) {

                // dispatch event to the listener
                switch (RpcAPI.RestoreTaskEvent.fromCode(event.charAt(0))) {
                    case STARTED:
                        restoreListener.started(Long.valueOf(event.substring(1)));
                        break;

                    case PROCESSING_DESCRIPTOR:
                        restoreListener.processingDescriptor(event.substring(1));
                        break;

                    case CREATED_COLLECTION:
                        restoreListener.createdCollection(event.substring(1));
                        break;

                    case RESTORED_RESOURCE:
                        restoreListener.restoredResource(event.substring(1));
                        break;

                    case INFO:
                        restoreListener.info(event.substring(1));
                        break;

                    case WARN:
                        restoreListener.warn(event.substring(1));
                        break;

                    case ERROR:
                        restoreListener.error(event.substring(1));
                        break;

                    case FINISHED:
                        restoreListener.finished();
                        finished = true;
                        break;
                }

                if (finished) {
                    break; // exit the for loop! we are done...
                }
            }

            // before looping... sleep a bit, if we got zero events sleep longer as the server is likely busy restoring something large
            if (!finished) {
                try {
                    if (!events.isEmpty()) {
                        Thread.sleep(1500);
                    } else {
                        Thread.sleep(3000);
                    }
                } catch (final InterruptedException e) {
                    // restore interrupt status
                    Thread.currentThread().interrupt();

                    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e);
                }
            }

            // stop looping on finished event or on exception
        }
    }

    private String uploadBackupFile(final Path backupZipFile) throws XMLDBException {
        try {
            String fileName = null;
            final byte[] chunk = new byte[(int) Math.min(Files.size(backupZipFile), MAX_UPLOAD_CHUNK)];
            try (final InputStream is = Files.newInputStream(backupZipFile)) {
                int len = -1;
                while ((len = is.read(chunk)) > -1) {
                    final List<Object> params = new ArrayList<>(4);
                    if (fileName != null) {
                        params.add(fileName);
                    }
                    params.add(chunk);
                    params.add(len);
                    fileName = (String) client.execute("upload", params);
                }
            }

            return fileName;
        } catch (final IOException | XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Unable to upload backup file: " + e.getMessage());
        }
    }

    private Path zipBackupDir(final Path dir) throws XMLDBException {
        try {
            final Path zipFile = Files.createTempFile("remote-restore-service", "zip");
            try (final OutputStream fos = Files.newOutputStream(zipFile);
                 final ZipOutputStream zos = new ZipOutputStream(fos)) {
                Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        final Path zipEntryPath = dir.relativize(file);
                        zos.putNextEntry(new ZipEntry(zipEntryPath.toString()));
                        Files.copy(file, zos);
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            return zipFile;
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Unable to zip backup dir: " + e.getMessage());
        }
    }

    @Override
    public void setCollection(final Collection collection) {
    }

    @Override
    public String getProperty(final String s) {
        return null;
    }

    @Override
    public void setProperty(final String s, final String s1) {
    }
}
