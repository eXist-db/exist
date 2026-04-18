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

import com.fasterxml.jackson.core.JsonGenerator;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.repo.PackageService;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.expath.pkg.repo.PackageException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in REST API for package management.
 * <p>
 * This servlet provides package CRUD operations (list, get, install, remove,
 * update-check) as a built-in Java endpoint that does not depend on any XAR
 * package. This ensures package management remains available even during
 * package upgrades, solving the self-upgrade problem that affects XQuery-based
 * package management implementations.
 * </p>
 * <p>
 * Endpoints:
 * <ul>
 *   <li>GET /api/packages — list all installed packages</li>
 *   <li>GET /api/packages/{name} — get single package details</li>
 *   <li>GET /api/packages/{name}/icon — get package icon</li>
 *   <li>POST /api/packages/install — install from registry (JSON) or upload (multipart)</li>
 *   <li>POST /api/packages/update-check — check for available updates</li>
 *   <li>DELETE /api/packages/{name} — remove package</li>
 * </ul>
 * </p>
 */
public class PackageManagementServlet extends AbstractExistHttpServlet {

    private static final Logger LOG = LogManager.getLogger(PackageManagementServlet.class);

    private final PackageService packageService = new PackageService();

    @Override
    public Logger getLog() {
        return LOG;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        final Subject user = authenticate(request, response);
        if (user == null) {
            return;
        }

        final String pathInfo = normalizePath(request.getPathInfo());

        try {
            if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
                // GET /api/packages — list all
                handleListPackages(response);
            } else if (pathInfo.endsWith("/icon")) {
                // GET /api/packages/{name}/icon
                final String name = pathInfo.substring(1, pathInfo.length() - "/icon".length());
                handleGetIcon(response, name);
            } else {
                // GET /api/packages/{name}
                final String name = pathInfo.substring(1);
                handleGetPackage(response, name);
            }
        } catch (final PackageException e) {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "PACKAGE_ERROR", e.getMessage());
        }
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        final Subject user = authenticate(request, response);
        if (user == null) {
            return;
        }
        if (!user.hasDbaRole()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN", "DBA role required for package installation");
            return;
        }

        final String pathInfo = normalizePath(request.getPathInfo());

        try {
            if ("/install".equals(pathInfo)) {
                handleInstall(request, response, user);
            } else if ("/update-check".equals(pathInfo)) {
                handleUpdateCheck(request, response);
            } else {
                writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "BAD_REQUEST", "Unknown POST endpoint: " + pathInfo);
            }
        } catch (final PackageException e) {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "PACKAGE_ERROR", e.getMessage());
        } catch (final EXistException e) {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "DATABASE_ERROR", e.getMessage());
        }
    }

    @Override
    protected void doDelete(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        final Subject user = authenticate(request, response);
        if (user == null) {
            return;
        }
        if (!user.hasDbaRole()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN", "DBA role required for package removal");
            return;
        }

        final String pathInfo = normalizePath(request.getPathInfo());
        if (pathInfo == null || pathInfo.length() <= 1) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST", "Package name required");
            return;
        }

        final String nameOrAbbrev = pathInfo.substring(1);

        try {
            // Check for dependents first
            final List<String> dependents = packageService.findDependents(getPool(), nameOrAbbrev);
            final boolean force = "true".equals(request.getParameter("force"));
            if (!dependents.isEmpty() && !force) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setContentType("application/json");
                try (final OutputStream os = response.getOutputStream();
                     final JsonGenerator gen = createJsonGenerator(os)) {
                    gen.writeStartObject();
                    gen.writeStringField("status", "error");
                    gen.writeStringField("code", "HAS_DEPENDENTS");
                    gen.writeStringField("message", "Cannot remove: other packages depend on " + nameOrAbbrev);
                    gen.writeArrayFieldStart("dependents");
                    for (final String dep : dependents) {
                        gen.writeString(dep);
                    }
                    gen.writeEndArray();
                    gen.writeEndObject();
                }
                return;
            }

            try (final DBBroker broker = getPool().get(Optional.of(user));
                 final Txn transaction = getPool().getTransactionManager().beginTransaction()) {
                final Map<String, Object> result = packageService.removePackage(broker, transaction, nameOrAbbrev);
                transaction.commit();
                writeJsonMap(response, HttpServletResponse.SC_OK, result);
            } catch (final EXistException e) {
                writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "DATABASE_ERROR", e.getMessage());
            }
        } catch (final PackageException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                writeError(response, HttpServletResponse.SC_NOT_FOUND,
                        "PACKAGE_NOT_FOUND", e.getMessage());
            } else {
                writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "PACKAGE_ERROR", e.getMessage());
            }
        }
    }

    @Override
    protected void doOptions(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    // --- Handler methods ---

    private void handleListPackages(final HttpServletResponse response)
            throws IOException, PackageException {
        final List<Map<String, Object>> packages = packageService.listPackages(getPool());
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        try (final OutputStream os = response.getOutputStream();
             final JsonGenerator gen = createJsonGenerator(os)) {
            gen.writeStartArray();
            for (final Map<String, Object> pkg : packages) {
                writeMapAsJson(gen, pkg);
            }
            gen.writeEndArray();
        }
    }

    private void handleGetPackage(final HttpServletResponse response, final String nameOrAbbrev)
            throws IOException, PackageException {
        final Map<String, Object> pkg = packageService.getPackage(getPool(), nameOrAbbrev);
        if (pkg == null) {
            writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    "PACKAGE_NOT_FOUND", "Package not found: " + nameOrAbbrev);
            return;
        }
        writeJsonMap(response, HttpServletResponse.SC_OK, pkg);
    }

    private void handleGetIcon(final HttpServletResponse response, final String nameOrAbbrev)
            throws IOException, PackageException {
        final Object[] icon = packageService.getPackageIcon(getPool(), nameOrAbbrev);
        if (icon == null) {
            writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    "ICON_NOT_FOUND", "No icon found for package: " + nameOrAbbrev);
            return;
        }
        response.setContentType((String) icon[1]);
        response.setStatus(HttpServletResponse.SC_OK);
        try (final OutputStream os = response.getOutputStream()) {
            os.write((byte[]) icon[0]);
        }
    }

    private void handleInstall(final HttpServletRequest request, final HttpServletResponse response,
                                final Subject user)
            throws IOException, PackageException, EXistException {
        final String contentType = request.getContentType();

        if (contentType != null && contentType.startsWith("multipart/")) {
            // Upload a XAR file
            handleInstallUpload(request, response, user);
        } else {
            // Install from registry (JSON body)
            handleInstallFromRegistry(request, response, user);
        }
    }

    private void handleInstallFromRegistry(final HttpServletRequest request,
                                            final HttpServletResponse response,
                                            final Subject user)
            throws IOException, PackageException, EXistException {
        // Parse JSON body manually (avoid adding a JSON parsing dependency)
        final String body;
        try (final InputStream is = request.getInputStream()) {
            body = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        final String name = PackageService.extractJsonStringValue(body, "name");
        final String url = PackageService.extractJsonStringValue(body, "url");
        final String version = PackageService.extractJsonStringValue(body, "version");

        if (name == null || name.isEmpty() || url == null || url.isEmpty()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST", "Missing required fields: name, url");
            return;
        }

        try (final DBBroker broker = getPool().get(Optional.of(user));
             final Txn transaction = getPool().getTransactionManager().beginTransaction()) {
            final Map<String, Object> result = packageService.installFromRegistry(
                    broker, transaction, name, url, version);
            transaction.commit();
            writeJsonMap(response, HttpServletResponse.SC_OK, result);
        }
    }

    private void handleInstallUpload(final HttpServletRequest request,
                                      final HttpServletResponse response,
                                      final Subject user)
            throws IOException, PackageException, EXistException {
        try {
            final Part xarPart = request.getPart("xar");
            if (xarPart == null) {
                writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "BAD_REQUEST", "Missing 'xar' file part in multipart upload");
                return;
            }
            try (final InputStream is = xarPart.getInputStream();
                 final DBBroker broker = getPool().get(Optional.of(user));
                 final Txn transaction = getPool().getTransactionManager().beginTransaction()) {
                final Map<String, Object> result = packageService.installFromUpload(
                        broker, transaction, is);
                transaction.commit();
                writeJsonMap(response, HttpServletResponse.SC_OK, result);
            }
        } catch (final ServletException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST", "Failed to process multipart upload: " + e.getMessage());
        }
    }

    private void handleUpdateCheck(final HttpServletRequest request,
                                    final HttpServletResponse response)
            throws IOException, PackageException {
        // Parse optional JSON body for registry URL
        String registryUrl = "https://exist-db.org/exist/apps/public-repo";
        final String contentType = request.getContentType();
        if (contentType != null && contentType.contains("json")) {
            final String body;
            try (final InputStream is = request.getInputStream()) {
                body = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            final String url = PackageService.extractJsonStringValue(body, "registry");
            if (url != null && !url.isEmpty()) {
                registryUrl = url;
            }
        }

        final List<Map<String, Object>> updates = packageService.checkUpdates(getPool(), registryUrl);
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        try (final OutputStream os = response.getOutputStream();
             final JsonGenerator gen = createJsonGenerator(os)) {
            gen.writeStartObject();
            gen.writeStringField("registry", registryUrl);
            gen.writeArrayFieldStart("updates");
            for (final Map<String, Object> update : updates) {
                writeMapAsJson(gen, update);
            }
            gen.writeEndArray();
            gen.writeEndObject();
        }
    }

    // --- JSON helpers ---

    private JsonGenerator createJsonGenerator(final OutputStream os) throws IOException {
        final com.fasterxml.jackson.core.JsonFactory factory = new com.fasterxml.jackson.core.JsonFactory();
        final JsonGenerator gen = factory.createGenerator(os);
        gen.useDefaultPrettyPrinter();
        return gen;
    }

    private void writeError(final HttpServletResponse response, final int statusCode,
                             final String code, final String message) throws IOException {
        response.setContentType("application/json");
        response.setStatus(statusCode);
        try (final OutputStream os = response.getOutputStream();
             final JsonGenerator gen = createJsonGenerator(os)) {
            gen.writeStartObject();
            gen.writeStringField("status", "error");
            gen.writeStringField("code", code);
            gen.writeStringField("message", message);
            gen.writeEndObject();
        }
    }

    private void writeJsonMap(final HttpServletResponse response, final int statusCode,
                               final Map<String, Object> data) throws IOException {
        response.setContentType("application/json");
        response.setStatus(statusCode);
        try (final OutputStream os = response.getOutputStream();
             final JsonGenerator gen = createJsonGenerator(os)) {
            writeMapAsJson(gen, data);
        }
    }

    @SuppressWarnings("unchecked")
    private void writeMapAsJson(final JsonGenerator gen, final Map<String, Object> map) throws IOException {
        gen.writeStartObject();
        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            if (value == null) {
                gen.writeNullField(key);
            } else if (value instanceof String s) {
                gen.writeStringField(key, s);
            } else if (value instanceof Boolean b) {
                gen.writeBooleanField(key, b);
            } else if (value instanceof Number n) {
                gen.writeNumberField(key, n.longValue());
            } else if (value instanceof List<?> list) {
                gen.writeArrayFieldStart(key);
                for (final Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        writeMapAsJson(gen, (Map<String, Object>) itemMap);
                    } else if (item instanceof String s) {
                        gen.writeString(s);
                    } else {
                        gen.writeString(String.valueOf(item));
                    }
                }
                gen.writeEndArray();
            } else if (value instanceof Map<?, ?> nested) {
                gen.writeFieldName(key);
                writeMapAsJson(gen, (Map<String, Object>) nested);
            } else {
                gen.writeStringField(key, String.valueOf(value));
            }
        }
        gen.writeEndObject();
    }

    private String normalizePath(final String pathInfo) {
        if (pathInfo == null) {
            return null;
        }
        // URL-decode the path
        return java.net.URLDecoder.decode(pathInfo, java.nio.charset.StandardCharsets.UTF_8);
    }
}
