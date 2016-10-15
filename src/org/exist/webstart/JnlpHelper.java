/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */

package org.exist.webstart;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class for webstart.
 *
 * @author Dannes Wessels
 */
public class JnlpHelper {

    private final static String LIB_CORE = "../lib/core";
    private final static String LIB_EXIST = "..";
    private final static String LIB_WEBINF = "WEB-INF/lib/";

    private static final Logger LOGGER = LogManager.getLogger(JnlpHelper.class);

    private Path coreJarsFolder = null;
    private Path existJarFolder = null;
    private Path webappsFolder = null;


    private boolean isInWarFile(final Path existHome) {
        return !Files.isDirectory(existHome.resolve(LIB_CORE));
    }

    /**
     * Creates a new instance of JnlpHelper
     */
    public JnlpHelper(final Path contextRoot) {

        // Setup path based on installation (in jetty, container)
        if (isInWarFile(contextRoot)) {
            // all files mixed in contextRoot/WEB-INF/lib
            LOGGER.debug("eXist is running in servlet container (.war).");
            coreJarsFolder = contextRoot.resolve(LIB_WEBINF).normalize();
            existJarFolder = coreJarsFolder;
            webappsFolder = contextRoot;

        } else {
            //files located in contextRoot/lib/core and contextRoot
            LOGGER.debug("eXist is running private jetty server.");
            coreJarsFolder = contextRoot.resolve(LIB_CORE).normalize();
            existJarFolder = contextRoot.resolve(LIB_EXIST).normalize();
            webappsFolder = contextRoot;
        }

        LOGGER.debug(String.format("CORE jars location=%s", coreJarsFolder.toAbsolutePath().toString()));
        LOGGER.debug(String.format("EXIST jars location=%s", existJarFolder.toAbsolutePath().toString()));
        LOGGER.debug(String.format("WEBAPP location=%s", webappsFolder.toAbsolutePath().toString()));
    }

    public Path getWebappFolder() {
        return webappsFolder;
    }

    public Path getCoreJarsFolder() {
        return coreJarsFolder;
    }

    public Path getExistJarFolder() {
        return existJarFolder;
    }

}
