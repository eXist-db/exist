/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.replication.jms.obsolete;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import javax.jms.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.exist.replication.shared.eXistMessage;


/**
 * Listener for actual handling of JMS message.
 *
 * @author Dannes Wessels
 *
 */
public class FileSystemListener implements MessageListener {

    private static File baseDir;

    private eXistMessage convertMessage(BytesMessage bm) {
        eXistMessage em = new eXistMessage();

        try {
            String value = bm.getStringProperty(eXistMessage.EXIST_RESOURCE_TYPE);
            eXistMessage.ResourceType resourceType = eXistMessage.ResourceType.valueOf(value);
            em.setResourceType(resourceType);

            value = bm.getStringProperty(eXistMessage.EXIST_RESOURCE_OPERATION);
            eXistMessage.ResourceOperation changeType = eXistMessage.ResourceOperation.valueOf(value);
            em.setResourceOperation(changeType);

            value = bm.getStringProperty(eXistMessage.EXIST_SOURCE_PATH);
            em.setResourcePath(value);

            value = bm.getStringProperty(eXistMessage.EXIST_DESTINATION_PATH);
            em.setDestinationPath(value);

            long size = bm.getBodyLength();
            LOG.debug("length=" + size);

            // This is potentially memory intensive
            byte[] payload = new byte[(int) size];
            bm.readBytes(payload);
            em.setPayload(payload);

        } catch (JMSException ex) {
            LOG.error(ex);
        }

        return em;

    }

    public FileSystemListener() {
        baseDir = new File("clusteringTest");
        if (!baseDir.exists()) {
            LOG.info("Creating " + baseDir.getAbsolutePath());
            baseDir.mkdirs();
        }
    }
    private final static Logger LOG = Logger.getLogger(FileSystemListener.class);

    @Override
    public void onMessage(Message message) {
        try {
            LOG.info("JMSMessageID=" + message.getJMSMessageID());

            StringBuilder sb = new StringBuilder();

            // Write properties
            Enumeration names = message.getPropertyNames();
            for (Enumeration<?> e = names; e.hasMoreElements();) {
                String key = (String) e.nextElement();
                sb.append("'" + key + "='" + message.getStringProperty(key) + "'");
            }
            LOG.info(sb.toString());

            // Handle message
            if (message instanceof TextMessage) {
                LOG.info(((TextMessage) message).getText());

            } else if (message instanceof BytesMessage) {

                BytesMessage bm = (BytesMessage) message;

                eXistMessage em = convertMessage(bm);

                switch (em.getResourceType()) {
                    case DOCUMENT:
                        LOG.info("document");
                        handleDocument(em);
                        break;
                    case COLLECTION:
                        LOG.info("collection");
                        handleCollection(em);
                        break;
                    default:
                        LOG.error("Unknown resource type");
                        break;
                }

            }

        } catch (JMSException ex) {
            LOG.error(ex);
        }

    }

    private void handleDocument(eXistMessage em) {

        // Get original path
        String resourcePath = em.getResourcePath();

        String[] srcSplitPath = splitPath(resourcePath);
        String srcDir = srcSplitPath[0];
        String srcDoc = srcSplitPath[1];


        File dir = new File(baseDir, srcDir);
        File file = new File(dir, srcDoc);

        switch (em.getResourceOperation()) {
            case CREATE:
            case UPDATE:
                // Create dirs if not existent

                dir.mkdirs();

                // Create file reference

                LOG.info(file.getAbsolutePath());

                try {
                    // Prepare streams
                    FileOutputStream fos = new FileOutputStream(file);
                    ByteArrayInputStream bais = new ByteArrayInputStream(em.getPayload());
                    GZIPInputStream gis = new GZIPInputStream(bais);

                    // Copy and unzip
                    IOUtils.copy(gis, fos);

                    // Cleanup
                    IOUtils.closeQuietly(fos);
                    IOUtils.closeQuietly(gis);
                } catch (IOException ex) {
                    LOG.error(ex);

                }
                break;

            case DELETE:
                FileUtils.deleteQuietly(file);
                break;

            case MOVE:
                File mvFile = new File(baseDir, em.getDestinationPath());
                try {
                    FileUtils.moveFile(file, mvFile);
                } catch (IOException ex) {
                    LOG.error(ex);
                }
                break;

            case COPY:
                File cpFile = new File(baseDir, em.getDestinationPath());
                try {
                    FileUtils.copyFile(file, cpFile);
                } catch (IOException ex) {
                    LOG.error(ex);
                }
                break;

            default:
                LOG.error("Unknown change type");
        }
    }

    private String[] splitPath(String fullPath) {
        String directory, documentname;
        int separator = fullPath.lastIndexOf("/");
        if (separator == -1) {
            directory = "";
            documentname = fullPath;
        } else {
            directory = fullPath.substring(0, separator);
            documentname = fullPath.substring(separator + 1);
        }

        return new String[]{directory, documentname};
    }

    private void handleCollection(eXistMessage em) {

        File src = new File(baseDir, em.getResourcePath());


        switch (em.getResourceOperation()) {
            case CREATE:
            case UPDATE:
                try {
                    // Create dirs if not existent
                    FileUtils.forceMkdir(src);
                } catch (IOException ex) {
                    LOG.error(ex);
                }

                break;

            case DELETE:
                FileUtils.deleteQuietly(src);
                break;

            case MOVE:
                File mvDest = new File(baseDir, em.getDestinationPath());
                try {
                    FileUtils.moveDirectoryToDirectory(src, mvDest, true);
                } catch (IOException ex) {
                    LOG.error(ex);
                }
                break;

            case COPY:

                File cpDest = new File(baseDir, em.getDestinationPath());
                try {
                    FileUtils.copyDirectoryToDirectory(src, cpDest);
                } catch (IOException ex) {
                    LOG.error(ex);
                }
                break;

            default:
                LOG.error("Unknown change type");
        }
    }
}
