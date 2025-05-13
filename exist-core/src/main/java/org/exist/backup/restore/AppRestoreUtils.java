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
package org.exist.backup.restore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.backup.BackupDescriptor;
import org.exist.repo.Deployment;
import org.exist.repo.ExistRepository;
import org.exist.storage.DBBroker;
import org.exist.util.XMLReaderPool;
import org.exist.xmldb.XmldbURI;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Packages;
import org.expath.pkg.repo.deps.Semver;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.util.*;

/**
 * Utility to compare the applications contained in a backup with the already
 * installed applications in the package repo.
 *
 * @author Wolfgang
 */
public class AppRestoreUtils {

    private final static Logger LOG = LogManager.getLogger(AppRestoreUtils.class);

    private final static String PKG_NAMESPACE = "http://expath.org/ns/pkg";

    /**
     * Inspects the apps contained in the backup against installed apps in the database
     * and return a set of symbolic backup paths pointing to the collection of those
     * apps for which newer versions are installed within the database. The returned
     * paths may then be ignored during a restore.
     *
     * The method attempts to be fail safe to make sure even bad backups can be restored. Errors
     * reading package descriptors are thus only logged and should not abort the process.
     *
     * @param broker the broker used for reading the backup and retrieving the expath repo
     * @param descriptors a queue of backup descriptors to inspect
     * @return a set of paths for which newer versions exist in the database. may be empty.
     */
    public static Set<String> checkApps(final DBBroker broker, final Deque<BackupDescriptor> descriptors) {
        final List<AppDetail> apps = getAppsFromBackup(broker, descriptors);
        final Set<String> paths = new HashSet<>();
        final Optional<ExistRepository> repo = broker.getBrokerPool().getExpathRepo();
        if (repo.isPresent()) {
            for (final AppDetail app: apps) {
                final Packages packages = repo.get().getParentRepo().getPackages(app.name);
                if (packages != null) {
                    final Package latest = packages.latest();
                    try {
                        final Semver version = Semver.parse(latest.getVersion());
                        if (version.compareTo(app.version) > 0) {
                            paths.add(app.path);
                        }
                    } catch (PackageException e) {
                        LOG.warn("Invalid semver in expath repository for {}", app.name, e);
                    }
                }
            }
        }
        return paths;
    }

    /**
     * Inspect all collections which may belong to apps in the backup descriptor. Return a list
     * of {@link AppDetail} objects containing the symbolic path, name and version of every app
     * found.
     *
     * The method attempts to be fail safe to make sure even bad backups can be restored. Errors
     * reading package descriptors are thus only logged and should not abort the process.
     *
     * @param broker the broker to use for parsing the descriptor and obtaining the app root
     * @param descriptors a queue of backup descriptors to inspect
     * @return list of application details
     */
    private static List<AppDetail> getAppsFromBackup(final DBBroker broker, final Deque<BackupDescriptor> descriptors) {
        final String appRoot = getAppRoot(broker);
        final List<AppDetail> result = new ArrayList<>();
        final XMLReaderPool parserPool = broker.getBrokerPool().getParserPool();
        for (final BackupDescriptor descriptor : descriptors) {
            final BackupDescriptor apps = descriptor.getChildBackupDescriptor(appRoot);
            if (apps != null) {
                getAppsFromSubColl(result, parserPool, apps);
            }

            final BackupDescriptor system = descriptor.getChildBackupDescriptor("system");
            if (system != null) {
                final BackupDescriptor repo = system.getChildBackupDescriptor("repo");
                if (repo != null) {
                    getAppsFromSubColl(result, parserPool, repo);
                }
            }
        }
        return result;
    }

    private static void getAppsFromSubColl(final List<AppDetail> result, final XMLReaderPool parserPool, final BackupDescriptor descriptor) {
        final List<String> collections = getSubcollectionNames(parserPool, descriptor);

        for (final String collection : collections) {
            final BackupDescriptor app = descriptor.getChildBackupDescriptor(collection);
            final InputSource is = app.getInputSource("expath-pkg.xml");
            if (is != null) {
                XMLReader reader = null;
                try {
                    reader = parserPool.borrowXMLReader();
                    reader.setContentHandler(new DefaultHandler() {
                        @Override
                        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                            if (PKG_NAMESPACE.equals(uri) && "package".equals(localName)) {
                                final String version = attributes.getValue("version");
                                final String name = attributes.getValue("name");
                                if (version.isEmpty() || name.isEmpty()) {
                                    LOG.warn("Invalid package descriptor for {}", app.getSymbolicPath());
                                    return;
                                }
                                try {
                                    final AppDetail detail = new AppDetail(app.getSymbolicPath(), name, Semver.parse(version));
                                    result.add(detail);
                                } catch (PackageException e) {
                                    LOG.warn("Invalid semver found while parsing {}", app.getSymbolicPath());
                                }
                            }
                        }
                    });
                    reader.parse(is);
                } catch (IOException | SAXException e) {
                    LOG.warn("Parse exception while parsing {}", app.getSymbolicPath("expath-pkg.xml", false));
                } finally {
                    if (reader != null) {
                        parserPool.returnXMLReader(reader);
                    }
                }
            }
        }
    }

    private static List<String> getSubcollectionNames(final XMLReaderPool parserPool, final BackupDescriptor apps) {
        final List<String> collections = new ArrayList<>();
        try {
            apps.parse(parserPool, new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    if (Namespaces.EXIST_NS.equals(uri) && "subcollection".equals(localName)) {
                        collections.add(attributes.getValue("filename"));
                    }
                }
            });
        } catch (IOException | SAXException e) {
            LOG.warn("SAX error while parsing backup descriptor {}", apps.getSymbolicPath(), e);
        }
        return collections;
    }

    /**
     * Get the database root path for applications, removing /db and trailing slash.
     * @param broker the broker to get the configuration from
     * @return the root path for applications
     */
    private static String getAppRoot(final DBBroker broker) {
        String appRoot = (String) broker.getConfiguration().getProperty(Deployment.PROPERTY_APP_ROOT);
        if (appRoot.startsWith(XmldbURI.ROOT_COLLECTION + '/')) {
            appRoot = appRoot.substring(XmldbURI.ROOT_COLLECTION.length() + 1);
        }
        if (appRoot.endsWith("/")) {
            appRoot = appRoot.substring(0, appRoot.length() - 1);
        }
        return appRoot;
    }

    final static class AppDetail {
        protected final String path;
        protected final String name;
        protected final Semver version;

        AppDetail(String path, String name, Semver version) {
            this.path = path;
            this.name = name;
            this.version = version;
        }
    }
}
