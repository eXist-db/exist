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
package org.exist.http.servlets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.ConfigurationHelper;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Serves eXist's native config-file XSDs ({@code $EXIST_HOME/schema/*.xsd})
 * unauthenticated at {@code /exist/schema/{name}.xsd}, so external tooling
 * (IDE plugins, editors) can resolve a config file's grammar without
 * vendoring its own copy. See #6563. No database access, no secrets — the
 * schemas are plain, publicly-shippable XSDs (see {@code schema/README.md}).
 */
public class SchemaServlet extends HttpServlet {

    private static final Logger LOG = LogManager.getLogger(SchemaServlet.class);

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final String pathInfo = request.getPathInfo();
        // reject anything but a bare "<name>.xsd" filename: no path traversal, no subdirectories
        if (pathInfo == null || pathInfo.length() < 2 || pathInfo.indexOf('/', 1) >= 0
                || !pathInfo.endsWith(".xsd")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        final String fileName = pathInfo.substring(1);

        final Optional<Path> existHome = ConfigurationHelper.getExistHome();
        if (existHome.isEmpty()) {
            LOG.warn("Could not resolve $EXIST_HOME while serving {}", pathInfo);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        final Path schemaDir = existHome.get().resolve("schema").normalize();
        final Path schemaFile = schemaDir.resolve(fileName).normalize();
        if (!schemaFile.startsWith(schemaDir) || !Files.isRegularFile(schemaFile)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType("application/xml");
        response.setContentLengthLong(Files.size(schemaFile));
        Files.copy(schemaFile, response.getOutputStream());
    }
}
