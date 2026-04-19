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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.io.TemporaryFileManager;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.tui.BatchUserInteraction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Business logic for package management operations.
 * Used by {@link org.exist.http.servlets.PackageManagementServlet} to provide
 * a built-in REST API for package CRUD that does not depend on any XAR package.
 */
public class PackageService {

    private static final Logger LOG = LogManager.getLogger(PackageService.class);

    private static final String PKG_NAMESPACE = "http://expath.org/ns/pkg";
    private static final String REPO_NAMESPACE = "http://exist-db.org/xquery/repo";

    /**
     * List all installed packages with metadata.
     */
    public List<Map<String, Object>> listPackages(final BrokerPool pool) throws PackageException {
        final Optional<ExistRepository> maybeRepo = pool.getExpathRepo();
        if (maybeRepo.isEmpty()) {
            return Collections.emptyList();
        }
        final Repository repo = maybeRepo.get().getParentRepo();
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final Packages packages : repo.listPackages()) {
            final Package pkg = packages.latest();
            result.add(buildPackageInfo(pkg));
        }
        return result;
    }

    /**
     * Get metadata for a single package by name (URI) or abbreviation.
     */
    @Nullable
    public Map<String, Object> getPackage(final BrokerPool pool, final String nameOrAbbrev) throws PackageException {
        final Package pkg = resolvePackage(pool, nameOrAbbrev);
        if (pkg == null) {
            return null;
        }
        return buildPackageInfo(pkg);
    }

    /**
     * Get a package icon file.
     * Returns a two-element array: [byte[], contentType] or null if no icon found.
     */
    @Nullable
    public Object[] getPackageIcon(final BrokerPool pool, final String nameOrAbbrev) throws PackageException, IOException {
        final Package pkg = resolvePackage(pool, nameOrAbbrev);
        if (pkg == null) {
            return null;
        }
        final Path pkgDir = getPackageDir(pkg);
        // Try common icon filenames
        for (final String iconName : new String[]{"icon.png", "icon.svg", "icon.jpg", "icon.gif"}) {
            final Path iconFile = pkgDir.resolve(iconName);
            if (Files.isReadable(iconFile)) {
                final String contentType;
                if (iconName.endsWith(".svg")) {
                    contentType = "image/svg+xml";
                } else if (iconName.endsWith(".png")) {
                    contentType = "image/png";
                } else if (iconName.endsWith(".jpg")) {
                    contentType = "image/jpeg";
                } else {
                    contentType = "image/gif";
                }
                return new Object[]{Files.readAllBytes(iconFile), contentType};
            }
        }
        return null;
    }

    /**
     * Install a package from a remote registry.
     */
    public Map<String, Object> installFromRegistry(final DBBroker broker, final Txn transaction,
                                                    final String name, final String registryUrl,
                                                    @Nullable final String version) throws PackageException, IOException {
        final RepoPackageLoader loader = new RepoPackageLoader(registryUrl);
        // Build version constraint
        PackageLoader.Version pkgVersion = null;
        if (version != null && !version.isEmpty()) {
            pkgVersion = new PackageLoader.Version(version, true);
        }

        // Download the XAR from the registry
        final XarSource xar = loader.load(name, pkgVersion);
        if (xar == null) {
            throw new PackageException("Package not found in registry: " + name);
        }

        // Install and deploy
        final Deployment deployment = new Deployment();
        final Optional<String> target = deployment.installAndDeploy(broker, transaction, xar, loader);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("name", name);
        target.ifPresent(t -> result.put("target", t));
        return result;
    }

    /**
     * Install a package from an uploaded XAR file.
     */
    public Map<String, Object> installFromUpload(final DBBroker broker, final Txn transaction,
                                                  final InputStream xarStream) throws PackageException, IOException {
        // Save to temporary file
        final TemporaryFileManager tempManager = TemporaryFileManager.getInstance();
        final Path tempFile = tempManager.getTemporaryFile();
        Files.copy(xarStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

        final XarFileSource xarSource = new XarFileSource(tempFile);
        final Deployment deployment = new Deployment();
        final Optional<String> target = deployment.installAndDeploy(broker, transaction, xarSource, null);

        // Read package name from the XAR descriptor
        final Optional<org.exist.dom.memtree.DocumentImpl> descriptor = deployment.getDescriptor(broker, xarSource);
        final String name = descriptor.map(d -> d.getDocumentElement().getAttribute("name")).orElse("unknown");

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("name", name);
        target.ifPresent(t -> result.put("target", t));
        return result;
    }

    /**
     * Remove (undeploy + delete) a package.
     */
    public Map<String, Object> removePackage(final DBBroker broker, final Txn transaction,
                                              final String nameOrAbbrev) throws PackageException {
        final BrokerPool pool = broker.getBrokerPool();
        final Package pkg = resolvePackage(pool, nameOrAbbrev);
        if (pkg == null) {
            throw new PackageException("Package not found: " + nameOrAbbrev);
        }
        final String pkgName = pkg.getName();

        final Optional<ExistRepository> maybeRepo = pool.getExpathRepo();
        if (maybeRepo.isEmpty()) {
            throw new PackageException("EXPath repository not available");
        }

        // Undeploy (remove database resources)
        final Deployment deployment = new Deployment();
        deployment.undeploy(broker, transaction, pkgName, maybeRepo);

        // Remove from repository
        final Repository parentRepo = maybeRepo.get().getParentRepo();
        parentRepo.removePackage(pkgName, false, new BatchUserInteraction());
        maybeRepo.get().reportAction(ExistRepository.Action.UNINSTALL, pkgName);

        // Clear XQuery cache
        pool.getXQueryPool().clear();

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("name", pkgName);
        return result;
    }

    /**
     * Find packages that depend on the given package.
     */
    public List<String> findDependents(final BrokerPool pool, final String packageName) throws PackageException {
        final Optional<ExistRepository> maybeRepo = pool.getExpathRepo();
        if (maybeRepo.isEmpty()) {
            return Collections.emptyList();
        }
        final Repository repo = maybeRepo.get().getParentRepo();
        final List<String> dependents = new ArrayList<>();

        for (final Packages packages : repo.listPackages()) {
            final Package pkg = packages.latest();
            if (pkg.getName().equals(packageName)) {
                continue;
            }
            // Read expath-pkg.xml to check dependencies
            final Path pkgDir = getPackageDir(pkg);
            final Path expathFile = pkgDir.resolve("expath-pkg.xml");
            if (Files.isReadable(expathFile)) {
                try {
                    final Document doc = parseXml(expathFile);
                    final NodeList deps = doc.getElementsByTagNameNS(PKG_NAMESPACE, "dependency");
                    for (int i = 0; i < deps.getLength(); i++) {
                        final Element dep = (Element) deps.item(i);
                        if (packageName.equals(dep.getAttribute("package"))) {
                            dependents.add(pkg.getName());
                            break;
                        }
                    }
                } catch (final Exception e) {
                    LOG.warn("Failed to parse expath-pkg.xml for package {}", pkg.getName(), e);
                }
            }
        }
        return dependents;
    }

    /**
     * Check a remote registry for available updates.
     */
    public List<Map<String, Object>> checkUpdates(final BrokerPool pool, final String registryUrl) throws PackageException {
        final Optional<ExistRepository> maybeRepo = pool.getExpathRepo();
        if (maybeRepo.isEmpty()) {
            return Collections.emptyList();
        }
        final Repository repo = maybeRepo.get().getParentRepo();
        final String findUrl = registryUrl + "/find";
        final String processorVersion = org.exist.SystemProperties.getInstance()
                .getSystemProperty("product-version", "7.0.0");

        final List<Map<String, Object>> updates = new ArrayList<>();
        for (final Packages packages : repo.listPackages()) {
            final Package pkg = packages.latest();
            final String abbrev = pkg.getAbbrev();
            final String installed = pkg.getVersion();
            if (abbrev == null || installed == null) {
                continue;
            }
            try {
                final String queryUrl = findUrl
                        + "?abbrev=" + URLEncoder.encode(abbrev, StandardCharsets.UTF_8)
                        + "&processor=http://exist-db.org"
                        + "&info=true"
                        + "&processorVersion=" + URLEncoder.encode(processorVersion, StandardCharsets.UTF_8);

                final HttpURLConnection conn = (HttpURLConnection) URI.create(queryUrl).toURL().openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestProperty("Accept", "application/json");
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    // Parse the simple JSON response to extract version
                    // The response format is: {"version": "x.y.z", ...}
                    final String body;
                    try (final InputStream is = conn.getInputStream()) {
                        body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    final String available = extractJsonStringValue(body, "version");
                    if (available != null && !available.equals(installed)) {
                        final Map<String, Object> update = new LinkedHashMap<>();
                        update.put("name", pkg.getName());
                        update.put("abbrev", abbrev);
                        update.put("installed", installed);
                        update.put("available", available);
                        updates.add(update);
                    }
                }
            } catch (final IOException e) {
                LOG.debug("Failed to check updates for package {}: {}", abbrev, e.getMessage());
            }
        }
        return updates;
    }

    // --- Private helpers ---

    @Nullable
    private Package resolvePackage(final BrokerPool pool, final String nameOrAbbrev) throws PackageException {
        final Optional<ExistRepository> maybeRepo = pool.getExpathRepo();
        if (maybeRepo.isEmpty()) {
            return null;
        }
        final Repository repo = maybeRepo.get().getParentRepo();

        // Try as package name (URI) first
        final Packages byName = repo.getPackages(nameOrAbbrev);
        if (byName != null) {
            return byName.latest();
        }

        // Try as abbreviation
        for (final Packages packages : repo.listPackages()) {
            final Package pkg = packages.latest();
            if (nameOrAbbrev.equals(pkg.getAbbrev())) {
                return pkg;
            }
        }
        return null;
    }

    private Path getPackageDir(final Package pkg) {
        final FileSystemStorage.FileSystemResolver resolver =
                (FileSystemStorage.FileSystemResolver) pkg.getResolver();
        return resolver.resolveResourceAsFile("");
    }

    private Map<String, Object> buildPackageInfo(final Package pkg) {
        final Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", pkg.getName());
        info.put("abbrev", pkg.getAbbrev());
        info.put("version", pkg.getVersion());

        // Read additional metadata from descriptors on the filesystem
        final Path pkgDir = getPackageDir(pkg);
        readExpathMetadata(pkgDir, info);
        readRepoMetadata(pkgDir, info);
        return info;
    }

    private void readExpathMetadata(final Path pkgDir, final Map<String, Object> info) {
        final Path expathFile = pkgDir.resolve("expath-pkg.xml");
        if (!Files.isReadable(expathFile)) {
            return;
        }
        try {
            final Document doc = parseXml(expathFile);
            final Element root = doc.getDocumentElement();
            // Title
            final NodeList titles = root.getElementsByTagNameNS(PKG_NAMESPACE, "title");
            if (titles.getLength() > 0) {
                info.put("title", titles.item(0).getTextContent());
            }
            // Dependencies
            final NodeList deps = root.getElementsByTagNameNS(PKG_NAMESPACE, "dependency");
            final List<Map<String, String>> depList = new ArrayList<>();
            for (int i = 0; i < deps.getLength(); i++) {
                final Element dep = (Element) deps.item(i);
                final Map<String, String> depInfo = new LinkedHashMap<>();
                if (!dep.getAttribute("package").isEmpty()) {
                    depInfo.put("package", dep.getAttribute("package"));
                }
                if (!dep.getAttribute("processor").isEmpty()) {
                    depInfo.put("processor", dep.getAttribute("processor"));
                }
                if (!dep.getAttribute("semver-min").isEmpty()) {
                    depInfo.put("semverMin", dep.getAttribute("semver-min"));
                }
                if (!dep.getAttribute("semver-max").isEmpty()) {
                    depInfo.put("semverMax", dep.getAttribute("semver-max"));
                }
                if (!dep.getAttribute("semver").isEmpty()) {
                    depInfo.put("semver", dep.getAttribute("semver"));
                }
                if (!dep.getAttribute("version").isEmpty()) {
                    depInfo.put("version", dep.getAttribute("version"));
                }
                depList.add(depInfo);
            }
            info.put("dependencies", depList);
        } catch (final Exception e) {
            LOG.debug("Failed to read expath-pkg.xml from {}", pkgDir, e);
        }
    }

    private void readRepoMetadata(final Path pkgDir, final Map<String, Object> info) {
        final Path repoFile = pkgDir.resolve("repo.xml");
        if (!Files.isReadable(repoFile)) {
            return;
        }
        try {
            final Document doc = parseXml(repoFile);
            final Element root = doc.getDocumentElement();
            setTextElement(root, REPO_NAMESPACE, "description", "description", info);
            setTextElement(root, REPO_NAMESPACE, "website", "website", info);
            setTextElement(root, REPO_NAMESPACE, "license", "license", info);
            setTextElement(root, REPO_NAMESPACE, "type", "type", info);
            setTextElement(root, REPO_NAMESPACE, "target", "target", info);
            setTextElement(root, REPO_NAMESPACE, "deployed", "deployed", info);
            // Authors
            final NodeList authors = root.getElementsByTagNameNS(REPO_NAMESPACE, "author");
            if (authors.getLength() > 0) {
                final List<String> authorList = new ArrayList<>();
                for (int i = 0; i < authors.getLength(); i++) {
                    authorList.add(authors.item(i).getTextContent().trim());
                }
                info.put("authors", authorList);
            }
        } catch (final Exception e) {
            LOG.debug("Failed to read repo.xml from {}", pkgDir, e);
        }
    }

    private void setTextElement(final Element root, final String ns, final String localName,
                                final String key, final Map<String, Object> info) {
        final NodeList nodes = root.getElementsByTagNameNS(ns, localName);
        if (nodes.getLength() > 0) {
            final String value = nodes.item(0).getTextContent().trim();
            if (!value.isEmpty()) {
                info.put(key, value);
            }
        }
    }

    private Document parseXml(final Path file) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file.toFile());
    }

    /**
     * Simple extraction of a string value from a JSON object.
     * Avoids pulling in a JSON parsing library for this single use case.
     */
    @Nullable
    public static String extractJsonStringValue(final String json, final String key) {
        final String search = "\"" + key + "\"";
        final int keyIdx = json.indexOf(search);
        if (keyIdx < 0) {
            return null;
        }
        final int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) {
            return null;
        }
        final int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) {
            return null;
        }
        final int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) {
            return null;
        }
        return json.substring(startQuote + 1, endQuote);
    }
}
