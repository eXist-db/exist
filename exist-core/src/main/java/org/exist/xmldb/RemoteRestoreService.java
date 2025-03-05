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

package org.exist.xmldb;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.util.FileUtils;
import org.exist.util.Leasable;
import org.exist.xmlrpc.RpcAPI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.exist.util.FileUtils.withUnixSep;
import static org.exist.xmldb.RemoteCollection.MAX_UPLOAD_CHUNK;

public class RemoteRestoreService implements EXistRestoreService {

    private final Leasable<XmlRpcClient> leasableXmlRpcClient;
    private final RemoteCallSite remoteCallSite;

    /**
     * Constructor for DatabaseInstanceManagerImpl.
     *
     * @param leasableXmlRpcClient the leasable instance of a the XML RPC client
     * @param remoteCallSite the remote call site
     */
    public RemoteRestoreService(final Leasable<XmlRpcClient> leasableXmlRpcClient, final RemoteCallSite remoteCallSite) {
        this.leasableXmlRpcClient = leasableXmlRpcClient;
        this.remoteCallSite = remoteCallSite;
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
            remoteFileName = uploadBackupFile(backupPath, restoreListener);
        } else if (Files.isDirectory(backupPath)) {
            final Path tmpZipFile = zipBackupDir(backupPath, restoreListener);
            try {
                remoteFileName = uploadBackupFile(tmpZipFile, restoreListener);
            } finally {
                FileUtils.deleteQuietly(tmpZipFile);
            }
        } else if (backupFileName.equals("__contents__.xml")) {
            final Path tmpZipFile = zipBackupDir(backupPath.getParent(), restoreListener);
            try {
                remoteFileName = uploadBackupFile(tmpZipFile, restoreListener);
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
        restoreTaskHandle = (String) remoteCallSite.execute("restore", params);

        // has the admin password changed?
        try (Leasable<XmlRpcClient>.Lease xmlRpcClientLease = leasableXmlRpcClient.lease()){
            final XmlRpcClientConfigImpl config = (XmlRpcClientConfigImpl) xmlRpcClientLease.get().getClientConfig();
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
        }

        // now we need to poll for results...
        boolean finished = false;
        params = new ArrayList<>();
        params.add(restoreTaskHandle);
        while (!finished) {
            final List<String> events = new ArrayList<>();
            final Object[] results = (Object[]) remoteCallSite.execute("getRestoreTaskEvents", params);
            if (results != null) {
                for (final Object result : results) {
                    events.add((String)result);
                }
            }

            for (final String event : events) {

                // dispatch event to the listener
                switch (RpcAPI.RestoreTaskEvent.fromCode(event.charAt(0))) {
                    case STARTED:
                        restoreListener.started(Long.parseLong(event.substring(1)));
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

                    case SKIP_RESOURCES:
                        final int sep = event.indexOf('@');
                        final String strCount = event.substring(1, sep);
                        final String message = event.substring(sep + 1);
                        restoreListener.skipResources(message, Long.valueOf(strCount));

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

    private String uploadBackupFile(final Path backupZipFile, final RestoreServiceTaskListener restoreListener) throws XMLDBException {
        try {
            final long backupZipFileSize = Files.size(backupZipFile);

            restoreListener.startedTransfer(backupZipFileSize);

            String fileName = null;
            final byte[] chunk = new byte[(int) Math.min(backupZipFileSize, MAX_UPLOAD_CHUNK)];
            try (final InputStream is = new BufferedInputStream(Files.newInputStream(backupZipFile))) {
                int len = -1;
                while ((len = is.read(chunk)) > -1) {
                    final List<Object> params = new ArrayList<>(4);
                    if (fileName != null) {
                        params.add(fileName);
                    }
                    params.add(chunk);
                    params.add(len);
                    fileName = (String) remoteCallSite.execute("upload", params);

                    restoreListener.transferred(len);
                }
            }

            restoreListener.finishedTransfer();

            return fileName;
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Unable to upload backup file: " + e.getMessage());
        }
    }

    private Path zipBackupDir(final Path dir, final RestoreServiceTaskListener restoreListener) throws XMLDBException {
        restoreListener.startedZipForTransfer(FileUtils.sizeQuietly(dir));
        try {
            final Path zipFile = Files.createTempFile("remote-restore-service", "zip");
            try (final OutputStream fos = new BufferedOutputStream(Files.newOutputStream(zipFile));
                 final ZipOutputStream zos = new ZipOutputStream(fos)) {
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        final Path zipEntryPath = dir.relativize(file);
                        final String zipEntryName = withUnixSep(zipEntryPath.toString());
                        zos.putNextEntry(new ZipEntry(zipEntryName));
                        final long written = Files.copy(file, zos);
                        zos.closeEntry();

                        restoreListener.addedFileToZipForTransfer(written);

                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            restoreListener.finishedZipForTransfer();
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
    public String getProperty(String name, String defaultValue) throws XMLDBException {
        return defaultValue;
    }

    @Override
    public void setProperty(final String s, final String s1) {
    }
}
