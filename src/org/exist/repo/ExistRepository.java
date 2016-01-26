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
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
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
 * @since  2010-09-22
 */
public class ExistRepository extends Observable {

    public final static String EXPATH_REPO_DIR = "expathrepo";

    public final static String EXPATH_REPO_DEFAULT = "webapp/WEB-INF/" + EXPATH_REPO_DIR;

    public final static Logger LOG = LogManager.getLogger(ExistRepository.class);

    public ExistRepository(FileSystemStorage storage) throws PackageException {
        myParent = new Repository(storage);
        myParent.registerExtension(new ExistPkgExtension());
    }

    public Repository getParentRepo() {
        return myParent;
    }

    public Module resolveJavaModule(String namespace, XQueryContext ctxt)
            throws XPathException {
        // the URI
        URI uri;
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
    private Module getModule(String name, String namespace, XQueryContext ctxt)
            throws XPathException {
        try {
            final Class clazz = Class.forName(name);
            final Module module = instantiateModule(clazz);
            final String ns = module.getNamespaceURI();
            if (!ns.equals(namespace)) {
                throw new XPathException("The namespace in the Java module " +
                    "does not match the namespace in the package descriptor: " +
                    namespace + " - " + ns);
            }
            return ctxt.loadBuiltInModule(namespace, name);
        } catch ( final ClassNotFoundException ex ) {
            throw new XPathException("Cannot find module class from EXPath repository: " + name, ex);
        } catch ( final InstantiationException ex ) {
            throw new XPathException("Problem instantiating module class from EXPath repository: " + name, ex);
        } catch ( final IllegalAccessException ex ) {
            throw new XPathException("Problem instantiating module class from EXPath repository: " + name, ex);
        } catch ( final InvocationTargetException ex ) {
            throw new XPathException("Problem instantiating module class from EXPath repository: " + name, ex);
        } catch ( final ClassCastException ex ) {
            throw new XPathException("The class configured in EXPath repository is not a Module: " + name, ex);
        } catch ( final IllegalArgumentException ex ) {
            throw new XPathException("Illegal argument passed to the module ctor", ex);
        }
    }

    /**
     * Try to instantiate the class using the constructor with a Map parameter, 
     * or the default constructor.
     */
    private Module instantiateModule(Class clazz) throws XPathException,
        InstantiationException, IllegalAccessException, InvocationTargetException {
        try {
            final Constructor ctor = clazz.getConstructor(Map.class);
            return (Module) ctor.newInstance(EMPTY_MAP);
        } catch (final NoSuchMethodException ex) {
            try {
                final Constructor ctor = clazz.getConstructor();
                return (Module) ctor.newInstance();
            }
            catch (final NoSuchMethodException exx) {
                throw new XPathException("Cannot find suitable constructor " +
                    "for module from expath repository", exx);
            }
        }
    }

    public Path resolveXQueryModule(String namespace) throws XPathException {
        // the URI
        URI uri;
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
                    return resolver.resolveComponentAsFile(f).toPath();
                }
            }
            String sysid = null; // declared here to be used in catch
            try {
                final StreamSource src = pkg.resolve(namespace, URISpace.XQUERY);
                if (src != null) {
                    sysid = src.getSystemId();
                    return Paths.get(new URI(sysid));
                }
            } catch (final URISyntaxException ex) {
                throw new XPathException("Error parsing the URI of the query library: " + sysid, ex);
            } catch (final PackageException ex) {
                throw new XPathException("Error resolving the query library: " + namespace, ex);
            }
        }
        return null;
    }

    public List<URI> getJavaModules() {
        final List<URI> modules = new ArrayList<URI>(13);
        for (final Packages pp : myParent.listPackages()) {
            final Package pkg = pp.latest();
            final ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
            if (info != null) {
                modules.addAll(info.getJavaModules());
            }
        }
        return modules;
    }

    public static ExistRepository getRepository(Configuration config) throws PackageException {
        try {
            final Path expathDir = getRepositoryDir(config);

            LOG.info("Using directory " + expathDir.toAbsolutePath().toString() + " for expath package repository");
            final FileSystemStorage storage = new FileSystemStorage(expathDir.toFile());
            return new ExistRepository(storage);
        } catch(final IOException ioe) {
            throw new PackageException("Unable to access EXPath repository", ioe);
        }
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

    public void reportAction(Action action, String packageURI) {
        notifyObservers(new Notification(action, packageURI));
        setChanged();
    }

    /** The wrapped EXPath repository. */
    private Repository myParent;
    /** An empty map for constructors expecting a parameter map. */
    private static final Map<String, List<Object>> EMPTY_MAP = new HashMap<String, List<Object>>();

    public enum Action {
        INSTALL, UNINSTALL
    }

    public final static class Notification {
        private Action action;
        private String packageURI;

        public Notification(Action action, String packageURI) {
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
/*  Contributor(s): none.                                                   */
/* ------------------------------------------------------------------------ */
