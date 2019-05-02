/****************************************************************************/
/*  File:       ExistRepository.java                                        */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2010-09-22                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2010 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.exist.repo;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.storage.NativeBroker;
import org.exist.util.Configuration;
import org.exist.util.FileUtils;
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

/**
 * A repository as viewed by eXist.
 *
 * @author Florent Georges
 * @author Wolfgang Meier
 * @author Adam Retter
 * @since  2010-09-22
 */
public class ExistRepository extends Observable implements BrokerPoolService {

    private final static Logger LOG = LogManager.getLogger(ExistRepository.class);
    public final static String EXPATH_REPO_DIR = "expathrepo";
    public final static String EXPATH_REPO_DEFAULT = "webapp/WEB-INF/" + EXPATH_REPO_DIR;

    /** The wrapped EXPath repository. */
    private Path expathDir;
    private Repository myParent;

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        final Path dataDir = Optional.ofNullable((Path) configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR))
                .orElse(Paths.get(NativeBroker.DEFAULT_DATA_DIR));
        this.expathDir = dataDir.resolve(EXPATH_REPO_DIR);
    }

    @Override
    public void prepare(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        if(!Files.exists(expathDir)) {
            moveOldRepo(brokerPool.getConfiguration().getExistHome(), expathDir);
        }
        try {
            Files.createDirectories(expathDir);
        } catch(final IOException e) {
            throw new BrokerPoolServiceException("Unable to access EXPath repository", e);
        }

        LOG.info("Using directory " + expathDir.toAbsolutePath().toString() + " for expath package repository");

        try {
            final FileSystemStorage storage = new FileSystemStorage(expathDir);
            storage.setErrorIfNoContentDir(false);
            this.myParent = new Repository(storage);
            myParent.registerExtension(new ExistPkgExtension());
        } catch(final PackageException e) {
            throw new BrokerPoolServiceException("Unable to prepare EXPath Package Repository: " + expathDir.toAbsolutePath().toString(), e);
        }
    }

    public Repository getParentRepo() {
        return myParent;
    }

    public Module resolveJavaModule(final String namespace, final XQueryContext ctxt) throws XPathException {
        final URI uri;
        try {
            uri = new URI(namespace);
        }
        catch (final URISyntaxException ex) {
            throw new XPathException("Invalid URI: " + namespace, ex);
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
                throw new XPathException("The namespace in the Java module " +
                    "does not match the namespace in the package descriptor: " +
                    namespace + " - " + ns);
            }
            return ctxt.loadBuiltInModule(namespace, name);
        } catch (final ClassNotFoundException ex) {
            throw new XPathException("Cannot find module class from EXPath repository: " + name, ex);
        } catch (final ClassCastException ex) {
            throw new XPathException("The class configured in EXPath repository is not a Module: " + name, ex);
        } catch (final IllegalArgumentException ex) {
            throw new XPathException("Illegal argument passed to the module ctor", ex);
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

            throw new XPathException("Unable to instantiate module from EXPath" +
                    "repository: " + clazz.getName(), e);
        }
    }

    public Path resolveXQueryModule(final String namespace) throws XPathException {
        final URI uri;
        try {
            uri = new URI(namespace);
        } catch (final URISyntaxException ex) {
            throw new XPathException("Invalid URI: " + namespace, ex);
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
                throw new XPathException("Error parsing the URI of the query library: " + sysid, ex);
            } catch (final PackageException ex) {
                throw new XPathException("Error resolving the query library: " + namespace, ex);
            } finally {
                if (src != null && src instanceof StreamSource) {
                    final StreamSource streamSource = ((StreamSource)src);
                    try {
                        if (streamSource.getInputStream() != null) {
                            streamSource.getInputStream().close();
                        } else if (streamSource.getReader() != null) {
                            streamSource.getReader().close();
                        }
                    } catch (final IOException e) {
                        LOG.warn("Unable to close pkg source: " + e.getMessage(), e);
                    }
                }
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
        final Path expathDir = dataDir.resolve(EXPATH_REPO_DIR);

        if(!Files.exists(expathDir)) {
            moveOldRepo(config.getExistHome(), expathDir);
        }
        Files.createDirectories(expathDir);
        return expathDir;
    }

    private static void moveOldRepo(final Optional<Path> home, final Path newRepo) {
        final Path repo_dir = home.map(h -> {
            if(FileUtils.fileName(h).equals("WEB-INF")) {
                return h.resolve(EXPATH_REPO_DIR);
            } else {
                return h.resolve( EXPATH_REPO_DEFAULT);
            }
        }).orElse(Paths.get(System.getProperty("java.io.tmpdir")).resolve(EXPATH_REPO_DIR));

        if (Files.isReadable(repo_dir)) {
            LOG.info("Found old expathrepo directory. Moving to new default location: " + newRepo.toAbsolutePath().toString());
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

/* ------------------------------------------------------------------------ */
/*  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS COMMENT.               */
/*                                                                          */
/*  The contents of this file are subject to the Mozilla Public License     */
/*  Version 1.0 (the "License"); you may not use this file except in        */
/*  compliance with the License. You may obtain a copy of the License at    */
/*  http://www.mozilla.org/MPL/.                                            */
/*                                                                          */
/*  Software distributed under the License is distributed on an "AS IS"     */
/*  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.  See    */
/*  the License for the specific language governing rights and limitations  */
/*  under the License.                                                      */
/*                                                                          */
/*  The Original Code is: all this file.                                    */
/*                                                                          */
/*  The Initial Developer of the Original Code is Florent Georges.          */
/*                                                                          */
/*  Contributor(s): Wolfgang Meier, Adam Retter                             */
/* ------------------------------------------------------------------------ */
