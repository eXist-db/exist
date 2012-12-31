/****************************************************************************/
/*  File:       ExistRepository.java                                        */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2010-09-22                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2010 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.exist.repo;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.NativeBroker;
import org.exist.util.Configuration;
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

    public final static Logger LOG = Logger.getLogger(ExistRepository.class);

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
        catch (URISyntaxException ex) {
            throw new XPathException("Invalid URI: " + namespace, ex);
        }
        for (Packages pp : myParent.listPackages()) {
            Package pkg = pp.latest();
            ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
            if (info != null) {
                String clazz = info.getJava(uri);
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
            Class clazz = Class.forName(name);
            Module module = instantiateModule(clazz);
            String ns = module.getNamespaceURI();
            if (!ns.equals(namespace)) {
                throw new XPathException("The namespace in the Java module " +
                    "does not match the namespace in the package descriptor: " +
                    namespace + " - " + ns);
            }
            return ctxt.loadBuiltInModule(namespace, name);
        } catch ( ClassNotFoundException ex ) {
            throw new XPathException("Cannot find module class from EXPath repository: " + name, ex);
        } catch ( InstantiationException ex ) {
            throw new XPathException("Problem instantiating module class from EXPath repository: " + name, ex);
        } catch ( IllegalAccessException ex ) {
            throw new XPathException("Problem instantiating module class from EXPath repository: " + name, ex);
        } catch ( InvocationTargetException ex ) {
            throw new XPathException("Problem instantiating module class from EXPath repository: " + name, ex);
        } catch ( ClassCastException ex ) {
            throw new XPathException("The class configured in EXPath repository is not a Module: " + name, ex);
        } catch ( IllegalArgumentException ex ) {
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
            Constructor ctor = clazz.getConstructor(Map.class);
            return (Module) ctor.newInstance(EMPTY_MAP);
        } catch (NoSuchMethodException ex) {
            try {
                Constructor ctor = clazz.getConstructor();
                return (Module) ctor.newInstance();
            }
            catch (NoSuchMethodException exx) {
                throw new XPathException("Cannot find suitable constructor " +
                    "for module from expath repository", exx);
            }
        }
    }

    public File resolveXQueryModule(String namespace) throws XPathException {
        // the URI
        URI uri;
        try {
            uri = new URI(namespace);
        } catch (URISyntaxException ex) {
            throw new XPathException("Invalid URI: " + namespace, ex);
        }
        for (Packages pp : myParent.listPackages()) {
            Package pkg = pp.latest();
            // FIXME: Rely on having a file system storage, that's probably a bad design!
            FileSystemResolver resolver = (FileSystemResolver) pkg.getResolver();
            ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
            if (info != null) {
                String f = info.getXQuery(uri);
                if (f != null) {
                    return resolver.resolveComponentAsFile(f);
                }
            }
            String sysid = null; // declared here to be used in catch
            try {
                StreamSource src = pkg.resolve(namespace, URISpace.XQUERY);
                if (src != null) {
                    sysid = src.getSystemId();
                    return new File(new URI(sysid));
                }
            } catch (URISyntaxException ex) {
                throw new XPathException("Error parsing the URI of the query library: " + sysid, ex);
            } catch (PackageException ex) {
                throw new XPathException("Error resolving the query library: " + namespace, ex);
            }
        }
        return null;
    }

    public List<URI> getJavaModules() {
        List<URI> modules = new ArrayList<URI>(13);
        for (Packages pp : myParent.listPackages()) {
            Package pkg = pp.latest();
            ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
            if (info != null) {
                modules.addAll(info.getJavaModules());
            }
        }
        return modules;
    }

    public static ExistRepository getRepository(Configuration config) throws PackageException {
        String dataDirPath = (String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        if (dataDirPath == null)
            dataDirPath = NativeBroker.DEFAULT_DATA_DIR;
        File dataDir = new File(dataDirPath);
        File expathDir = new File(dataDir, EXPATH_REPO_DIR);
        if (!expathDir.exists()) {
            moveOldRepo(config.getExistHome(), expathDir);
        }
        expathDir.mkdir();

        LOG.info("Using directory " + expathDir.getAbsolutePath() + " for expath package repository");
        FileSystemStorage storage = new FileSystemStorage(expathDir);
        return new ExistRepository(storage);
    }

    private static void moveOldRepo(File home, File newRepo) {
        File repo_dir = null;
        if (home != null){
            if (home.getName().equals("WEB-INF"))
                repo_dir = new File(home, EXPATH_REPO_DIR);
            else
                repo_dir = new File(home, EXPATH_REPO_DEFAULT);
        } else {
            repo_dir = new File(System.getProperty("java.io.tmpdir"), EXPATH_REPO_DIR);
        }
        if (repo_dir.exists() && repo_dir.canRead()) {
            LOG.info("Found old expathrepo directory. Moving to new default location: " + newRepo.getAbsolutePath());
            try {
                FileUtils.moveDirectory(repo_dir, newRepo);
            } catch (IOException e) {
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
