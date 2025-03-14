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
import org.exist.dom.persistent.BinaryDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.util.Configuration;
import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.expath.pkg.repo.FileSystemStorage;
import org.expath.pkg.repo.FileSystemStorage.FileSystemResolver;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.Packages;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Repository;
import org.expath.pkg.repo.URISpace;
import org.w3c.dom.Document;

import javax.annotation.Nullable;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Optional;

/**
 * A repository as viewed by eXist.
 *
 * @author Florent Georges - H2O Consulting
 * @since  2010-09-22
 *
 * @author Wolfgang Meier
 * @author Adam Retter
 */
public class ExistRepository extends Observable implements BrokerPoolService {

    private final static Logger EXIST_LOG = LogManager.getLogger(BrokerPool.class);
    private final static Logger LOG = LogManager.getLogger(ExistRepository.class);
    private static final String EXPATH_REPO_DIR_NAME = "expathrepo";
    private static final String LEGACY_DEFAULT_EXPATH_REPO_DIR = "webapp/WEB-INF/" + EXPATH_REPO_DIR_NAME;

    /** The wrapped EXPath repository. */
    private Path expathDir;
    private Repository myParent;

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        final Path dataDir = Optional.ofNullable((Path) configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR))
                .orElse(Paths.get(NativeBroker.DEFAULT_DATA_DIR));
        this.expathDir = dataDir.resolve(EXPATH_REPO_DIR_NAME);
    }

    @Override
    public void prepare(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        if (!Files.exists(expathDir) && brokerPool != null) {
            moveOldRepo(brokerPool.getConfiguration().getExistHome(), expathDir);
        }
        try {
            Files.createDirectories(expathDir);
        } catch(final IOException e) {
            throw new BrokerPoolServiceException("Unable to access EXPath repository", e);
        }

        LOG.info("Using directory {} for expath package repository", expathDir.toAbsolutePath().toString());

        final FileSystemStorage storage;
        try {
            storage = new FileSystemStorage(expathDir);
        } catch(final PackageException e) {
            throw new BrokerPoolServiceException("Unable to open storage for EXPath Package Repository: " + expathDir.toAbsolutePath(), e);
        }
        storage.setErrorIfNoContentDir(false);

        this.myParent = new Repository(storage);
        final List<PackageException> exceptions = this.myParent.init();
        if (!exceptions.isEmpty()) {
            EXIST_LOG.warn("It may not have been possible to load all EXPath Packages, see repo.log for details...");
            for (final PackageException exception : exceptions) {
                LOG.error(exception.getMessage(), exception);
            }
        }

        try {
            myParent.registerExtension(new ExistPkgExtension());
        } catch(final PackageException e) {
            throw new BrokerPoolServiceException("Unable to register EXPath Package Repository extension 'ExistPkgExtension': " + e.getMessage(), e);
        }
    }

    public Repository getParentRepo() {
        return myParent;
    }


    /**
     * Resolve a Java Module.
     *
     * @param namespace the namespace of the module
     * @param ctxt the xquery context
     * @return the Java module, or null
     *
     * @throws XPathException with:
     *      XQST0046 if the namespace URI is invalid
     */
    public Module resolveJavaModule(final String namespace, final XQueryContext ctxt) throws XPathException {
        final URI uri;
        try {
            uri = new URI(namespace);
        }
        catch (final URISyntaxException ex) {
            throw new XPathException((Expression) null, ErrorCodes.XQST0046, "Invalid URI: " + namespace, ex);
        }
        for (final Packages pp : myParent.listPackages()) {
            final Package pkg = pp.latest();
            final ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
            if (info != null) {
                final String clazz = info.getJava(uri);
                if (clazz != null) {
                    return getModule(clazz, namespace, ctxt);
                }
            }
        }
        return null;
    }

    /**
     * Load a module instance from its class name.  Check the namespace is consistent.
     */
    private Module getModule(final String name, final String namespace, final XQueryContext ctxt)
            throws XPathException {
        try {
            final ClassLoader existClassLoader = ctxt.getBroker().getBrokerPool().getClassLoader();
            final Class<Module> clazz = (Class<Module>)Class.forName(name, false, existClassLoader);
            final Module module = instantiateModule(clazz);
            final String ns = module.getNamespaceURI();
            if (!ns.equals(namespace)) {
                throw new XPathException((Expression) null, "The namespace in the Java module " +
                    "does not match the namespace in the package descriptor: " +
                    namespace + " - " + ns);
            }
            return ctxt.loadBuiltInModule(namespace, name);
        } catch (final ClassNotFoundException ex) {
            throw new XPathException((Expression) null, "Cannot find module class from EXPath repository: " + name, ex);
        } catch (final ClassCastException ex) {
            throw new XPathException((Expression) null, "The class configured in EXPath repository is not a Module: " + name, ex);
        } catch (final IllegalArgumentException ex) {
            throw new XPathException((Expression) null, "Illegal argument passed to the module ctor", ex);
        }
    }

    /**
     * Try to instantiate the class using the constructor with a Map parameter, 
     * or the default constructor.
     */
    private Module instantiateModule(final Class<Module> clazz) throws XPathException {
        try {
            try {
                // attempt for a constructor that takes 1 argument
                final Constructor<Module> cstr1 = clazz.getConstructor(Map.class);
                return cstr1.newInstance(Collections.emptyMap());

            } catch (final NoSuchMethodException nsme) {
                // attempt for a constructor that takes 0 arguments
                return clazz.newInstance();
            }
        } catch (final Throwable e) {
            if (e instanceof InterruptedException) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }

            final String msg = "Unable to instantiate module from EXPath" +
                    "repository: " + clazz.getName();

            // need to log here, otherwise details are swallowed by XQueryTreeParser
            LOG.error(e.getMessage(), e);

            throw new XPathException((Expression) null, msg, e);
        }
    }

    /**
     * Resolve an XQuery Module.
     *
     * @param namespace the namespace of the module
     * @return the path to the module, or null
     *
     * @throws XPathException with:
     *      XQST0046 if the namespace URI is invalid
     *      XQST0059 if an error occurs loading the module
     */
    public Path resolveXQueryModule(final String namespace) throws XPathException {
        final URI uri;
        try {
            uri = new URI(namespace);
        } catch (final URISyntaxException ex) {
            throw new XPathException((Expression) null, ErrorCodes.XQST0046, "Invalid URI: " + namespace, ex);
        }
        for (final Packages pp : myParent.listPackages()) {
            final Package pkg = pp.latest();
            // FIXME: Rely on having a file system storage, that's probably a bad design!
            final FileSystemResolver resolver = (FileSystemResolver) pkg.getResolver();
            final ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
            if (info != null) {
                final String f = info.getXQuery(uri);
                if (f != null) {
                    return resolver.resolveComponentAsFile(f);
                }
            }
            String sysid = null; // declared here to be used in catch
            Source src = null;
            try {
                src = pkg.resolve(namespace, URISpace.XQUERY);
                if (src != null) {
                    sysid = src.getSystemId();
                    return Paths.get(new URI(sysid));
                }
            } catch (final URISyntaxException ex) {
                throw new XPathException((Expression) null, ErrorCodes.XQST0046, "Error parsing the URI of the query library: " + sysid, ex);
            } catch (final PackageException ex) {
                throw new XPathException((Expression) null, ErrorCodes.XQST0059, "Error resolving the query library: " + namespace, ex);
            } finally {
                if (src != null && src instanceof StreamSource streamSource) {
                    try {
                        if (streamSource.getInputStream() != null) {
                            streamSource.getInputStream().close();
                        } else if (streamSource.getReader() != null) {
                            streamSource.getReader().close();
                        }
                    } catch (final IOException e) {
                        LOG.warn("Unable to close pkg source: {}", e.getMessage(), e);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Attempt to lookup an XQuery from the filesystem in the database.
     *
     * @param broker the database broker
     * @param xqueryPath the path to the xquery within the EXPath filesystem repo.
     *
     * @return the database source for the xquery, or null.
     */
    public @Nullable org.exist.source.Source resolveStoredXQueryModuleFromDb(final DBBroker broker, final Path xqueryPath) throws PermissionDeniedException {
        if (!xqueryPath.startsWith(expathDir)) {
            return null;
        }

        final String relXQueryPath = expathDir.relativize(xqueryPath).toString();

        // 1. attempt to locate it within a library
        XmldbURI xqueryDbPath = XmldbURI.create("xmldb:exist:///db/system/repo/" + relXQueryPath);
        @Nullable Document doc = broker.getXMLResource(xqueryDbPath);
        if (doc != null && doc instanceof BinaryDocument) {
            return new DBSource(broker.getBrokerPool(), (BinaryDocument) doc, false);
        }

        // 2. attempt to locate it within an app
        xqueryDbPath = XmldbURI.create("xmldb:exist:///db/apps/" + relXQueryPath);
        doc = broker.getXMLResource(xqueryDbPath);
        if (doc != null && doc instanceof BinaryDocument) {
            return new DBSource(broker.getBrokerPool(), (BinaryDocument) doc, false);
        }

        return null;
    }

    public Source resolveXSLTModule(final String namespace) throws PackageException {
        for (final Packages pp : myParent.listPackages()) {
            final Package pkg = pp.latest();
            final Source src = pkg.resolve(namespace, URISpace.XSLT);
            if (src != null) {
                return src;
            }
        }
        return null;
    }

    public List<URI> getJavaModules() {
        final List<URI> modules = new ArrayList<>();
        for (final Packages pp : myParent.listPackages()) {
            final Package pkg = pp.latest();
            final ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
            if (info != null) {
                modules.addAll(info.getJavaModules());
            }
        }
        return modules;
    }

    public static Path getRepositoryDir(final Configuration config) throws IOException {
        final Path dataDir = Optional.ofNullable((Path) config.getProperty(BrokerPool.PROPERTY_DATA_DIR))
                        .orElse(Paths.get(NativeBroker.DEFAULT_DATA_DIR));
        final Path expathDir = dataDir.resolve(EXPATH_REPO_DIR_NAME);

        if(!Files.exists(expathDir)) {
            moveOldRepo(config.getExistHome(), expathDir);
        }
        Files.createDirectories(expathDir);
        return expathDir;
    }

    private static void moveOldRepo(final Optional<Path> home, final Path newRepo) {
        final Path repo_dir = home.map(h -> {
            if(FileUtils.fileName(h).equals("WEB-INF")) {
                return h.resolve(EXPATH_REPO_DIR_NAME);
            } else {
                return h.resolve(LEGACY_DEFAULT_EXPATH_REPO_DIR);
            }
        }).orElse(Paths.get(System.getProperty("java.io.tmpdir")).resolve(EXPATH_REPO_DIR_NAME));

        if (Files.isReadable(repo_dir)) {
            LOG.info("Found old expathrepo directory. Moving to new default location: {}", newRepo.toAbsolutePath().toString());
            try {
                Files.move(repo_dir, newRepo, StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException e) {
                LOG.error("Failed to move old expathrepo directory to new default location. Keeping it.", e);
            }
        }
    }

    public void reportAction(final Action action, final String packageURI) {
        notifyObservers(new Notification(action, packageURI));
        setChanged();
    }

    public enum Action {
        INSTALL, UNINSTALL
    }

    public final static class Notification {
        private final Action action;
        private final String packageURI;

        public Notification(final Action action, final String packageURI) {
            this.action = action;
            this.packageURI = packageURI;
        }

        public Action getAction() {
            return action;
        }

        public String getPackageURI() {
            return packageURI;
        }
    }
}
