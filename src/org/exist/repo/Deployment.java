/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-12 The eXist Project
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
package org.exist.repo;

import org.exist.SystemProperties;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.InMemoryNodeSet;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.ElementImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.config.ConfigurationException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.QName;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.security.xacml.AccessContext;
import org.exist.source.FileSource;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.SyntaxException;
import org.exist.util.serializer.AttrList;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.deps.DependencyVersion;
import org.expath.pkg.repo.tui.BatchUserInteraction;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.Date;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Deploy a .xar package into the database using the information provided
 * in expath-pkg.xml and repo.xml.
 */
public class Deployment {

    public final static String PROPERTY_APP_ROOT = "repo.root-collection";

    private final static Logger LOG = LogManager.getLogger(Deployment.class);

    public final static String PROCESSOR_NAME = "http://exist-db.org";

    private final static String REPO_NAMESPACE = "http://exist-db.org/xquery/repo";
    private final static String PKG_NAMESPACE = "http://expath.org/ns/pkg";

    private final static QName SETUP_ELEMENT = new QName("setup", REPO_NAMESPACE);
    private static final QName PRE_SETUP_ELEMENT = new QName("prepare", REPO_NAMESPACE);
    private static final QName POST_SETUP_ELEMENT = new QName("finish", REPO_NAMESPACE);
    private static final QName TARGET_COLL_ELEMENT = new QName("target", REPO_NAMESPACE);
    private static final QName PERMISSIONS_ELEMENT = new QName("permissions", REPO_NAMESPACE);
    private static final QName CLEANUP_ELEMENT = new QName("cleanup", REPO_NAMESPACE);
    private static final QName DEPLOYED_ELEMENT = new QName("deployed", REPO_NAMESPACE);
    private static final QName DEPENDENCY_ELEMENT = new QName("dependency", PKG_NAMESPACE);

    private DBBroker broker;

    private String user = null;
    private String password = null;
    private String group = null;
    private int perms = -1;
    private String permsStr = null;

    public Deployment(DBBroker broker) {
        this.broker = broker;
    }

    protected File getPackageDir(String pkgName, ExistRepository repo) throws PackageException {
        File packageDir = null;

        for (final Packages pp : repo.getParentRepo().listPackages()) {
            final org.expath.pkg.repo.Package pkg = pp.latest();
            if (pkg.getName().equals(pkgName)) {
                packageDir = getPackageDir(pkg);
            }
        }
        return packageDir;
    }

    protected File getPackageDir(Package pkg) {
        final FileSystemStorage.FileSystemResolver resolver = (FileSystemStorage.FileSystemResolver) pkg.getResolver();
        return resolver.resolveResourceAsFile(".");
    }

    protected org.expath.pkg.repo.Package getPackage(String pkgName, ExistRepository repo) throws PackageException {
        for (final Packages pp : repo.getParentRepo().listPackages()) {
            final org.expath.pkg.repo.Package pkg = pp.latest();
            if (pkg.getName().equals(pkgName)) {
                return pkg;
            }
        }
        return null;
    }

    protected DocumentImpl getRepoXML(File packageDir) throws PackageException {
        // find and parse the repo.xml descriptor
        final File repoFile = new File(packageDir, "repo.xml");
        if (!repoFile.canRead())
            {return null;}
        try {
            return DocUtils.parse(broker.getBrokerPool(), null, new FileInputStream(repoFile));
        } catch (final XPathException e) {
            throw new PackageException("Failed to parse repo.xml: " + e.getMessage(), e);
        } catch (final FileNotFoundException e) {
            throw new PackageException("Failed to read repo.xml: " + e.getMessage(), e);
        }
    }

    public String installAndDeploy(File xar, PackageLoader loader) throws PackageException, IOException {
        return installAndDeploy(xar, loader, true);
    }

    /**
     * Install and deploy a give xar archive. Dependencies are installed from
     * the PackageLoader.
     *
     * @param xar the .xar file to install
     * @param loader package loader to use
     * @param enforceDeps when set to true, the method will throw an exception if a dependency could not be resolved
     *                    or an older version of the required dependency is installed and needs to be replaced.
     */
    public String installAndDeploy(File xar, PackageLoader loader, boolean enforceDeps) throws PackageException, IOException {
        final DocumentImpl document = getDescriptor(xar);
        final ElementImpl root = (ElementImpl) document.getDocumentElement();
        final String name = root.getAttribute("name");
        final String pkgVersion = root.getAttribute("version");

        final ExistRepository repo = broker.getBrokerPool().getExpathRepo();
        final Packages packages = repo.getParentRepo().getPackages(name);
        if (packages != null && (!enforceDeps || pkgVersion.equals(packages.latest().getVersion()))) {
            LOG.info("Application package " + name + " already installed. Skipping.");
            return null;
        }

        InMemoryNodeSet deps;
        try {
            deps = findElements(root, DEPENDENCY_ELEMENT);
            for (final SequenceIterator i = deps.iterate(); i.hasNext(); ) {
                final Element dependency = (Element) i.nextItem();
                final String pkgName = dependency.getAttribute("package");
                final String processor = dependency.getAttribute("processor");
                final String versionStr = dependency.getAttribute("version");
                final String semVer = dependency.getAttribute("semver");
                final String semVerMin = dependency.getAttribute("semver-min");
                final String semVerMax = dependency.getAttribute("semver-max");
                PackageLoader.Version version = null;
                if (semVer != null) {
                    version = new PackageLoader.Version(semVer, true);
                } else if (semVerMax != null || semVerMin != null) {
                    version = new PackageLoader.Version(semVerMin, semVerMax);
                } else if (pkgVersion != null) {
                    version = new PackageLoader.Version(versionStr, false);
                }

                if (processor != null && processor.equals(PROCESSOR_NAME) && version != null) {
                    checkProcessorVersion(version);
                } else if (pkgName != null) {
                    LOG.info("Package " + name + " depends on " + pkgName);
                    boolean isInstalled = false;
                    if (repo.getParentRepo().getPackages(pkgName) != null) {
                        LOG.debug("Package " + pkgName + " already installed");
                        Packages pkgs = repo.getParentRepo().getPackages(pkgName);
                        // check if installed package matches required version
                        if (pkgs != null) {
                            if (version != null) {
                                Package latest = pkgs.latest();
                                DependencyVersion depVersion = version.getDependencyVersion();
                                if (depVersion.isCompatible(latest.getVersion())) {
                                    isInstalled = true;
                                } else {
                                    LOG.debug("Package " + pkgName + " needs to be upgraded");
                                    if (enforceDeps) {
                                        throw new PackageException("Package requires version " + version.toString() +
                                            " of package " + pkgName +
                                            ". Installed version is " + latest.getVersion() + ". Please upgrade!");
                                    }
                                }
                            } else {
                                isInstalled = true;
                            }
                            if (isInstalled) {
                                LOG.debug("Package " + pkgName + " already installed");
                            }
                        }
                    }
                    if (!isInstalled && loader != null) {
                        final File depFile = loader.load(pkgName, version);
                        if (depFile != null) {
                            installAndDeploy(depFile, loader);
                        } else {
                            if (enforceDeps) {
                                LOG.warn("Missing dependency: package " + pkgName + " could not be resolved. This error " +
                                        "is not fatal, but the package may not work as expected");
                            } else {
                                throw new PackageException("Missing dependency: package " + pkgName + " could not be resolved.");
                            }
                        }
                    }
                }
            }
        } catch (final XPathException e) {
            throw new PackageException("Invalid descriptor found in " + xar.getAbsolutePath());
        }

        // installing the xar into the expath repo
        LOG.info("Installing package " + xar.getAbsolutePath());
        final UserInteractionStrategy interact = new BatchUserInteraction();
        final org.expath.pkg.repo.Package pkg = repo.getParentRepo().installPackage(xar, true, interact);
        final ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
        if (info != null && !info.getJars().isEmpty())
            {ClasspathHelper.updateClasspath(broker.getBrokerPool(), pkg);}
        broker.getBrokerPool().getXQueryPool().clear();
        final String pkgName = pkg.getName();
        // signal status
        broker.getBrokerPool().reportStatus("Installing app: " + pkg.getAbbrev());
        repo.reportAction(ExistRepository.Action.INSTALL, pkg.getName());

        LOG.info("Deploying package " + pkgName);
        return deploy(pkgName, repo, null);
    }

    private void checkProcessorVersion(PackageLoader.Version version) throws PackageException {
        final String procVersion = SystemProperties.getInstance().getSystemProperty("product-semver", "1.0");
        final DependencyVersion depVersion = version.getDependencyVersion();
        if (!depVersion.isCompatible(procVersion)) {
            throw new PackageException("Package requires eXistdb version " + version.toString() + ". " +
                "Installed version is " + procVersion);
        }
    }

    public String undeploy(String pkgName, ExistRepository repo) throws PackageException {
        final File packageDir = getPackageDir(pkgName, repo);
        if (packageDir == null)
            // fails silently if package dir is not found?
            {return null;}
        final DocumentImpl repoXML = getRepoXML(packageDir);
        final Package pkg = getPackage(pkgName, repo);
        if (repoXML != null) {
            ElementImpl target = null;
            try {
                target = findElement(repoXML, TARGET_COLL_ELEMENT);
                final ElementImpl cleanup = findElement(repoXML, CLEANUP_ELEMENT);
                if (cleanup != null) {
                    runQuery(null, packageDir, cleanup.getStringValue(), false);
                }

                uninstall(pkg, target);
                return target == null ? null : target.getStringValue();
            } catch (final XPathException e) {
                throw new PackageException("Error found while processing repo.xml: " + e.getMessage(), e);
            } catch (final IOException e) {
                throw new PackageException("Error found while processing repo.xml: " + e.getMessage(), e);
            }
        } else {
            // we still may need to remove the copy of the package from /db/system/repo
            uninstall(pkg, null);
        }
        return null;
    }

    public String deploy(String pkgName, ExistRepository repo, String userTarget) throws PackageException, IOException {
        final File packageDir = getPackageDir(pkgName, repo);
        if (packageDir == null)
            {throw new PackageException("Package not found: " + pkgName);}
        final DocumentImpl repoXML = getRepoXML(packageDir);
        if (repoXML == null)
            {return null;}
        try {
            // if there's a <setup> element, run the query it points to
            final ElementImpl setup = findElement(repoXML, SETUP_ELEMENT);
            String path = setup == null ? null : setup.getStringValue();
            if (path != null && path.length() > 0) {
                runQuery(null, packageDir, path, true);
                return null;
            } else {
                // otherwise copy all child directories to the target collection
                XmldbURI targetCollection = null;
                if (userTarget != null) {
                    try {
                        targetCollection = XmldbURI.create(userTarget);
                    } catch (final Exception e) {
                        throw new PackageException("Bad collection URI: " + userTarget, e);
                    }
                } else {
                    final ElementImpl target = findElement(repoXML, TARGET_COLL_ELEMENT);
                    if (target != null) {
                        final String targetPath = target.getStringValue();
                        if (targetPath.length() > 0) {
                            // determine target collection
                            try {
                                targetCollection = XmldbURI.create(getTargetCollection(targetPath));
                            } catch (final Exception e) {
                                throw new PackageException("Bad collection URI for <target> element: " + target.getStringValue(), e);
                            }
                        }
                    }
                }
                if (targetCollection == null) {
                    // no target means: package does not need to be deployed into database
                    // however, we need to preserve a copy for backup purposes
                    final Package pkg = getPackage(pkgName, repo);
                    final String pkgColl = pkg.getAbbrev() + "-" + pkg.getVersion();
                    targetCollection = XmldbURI.SYSTEM.append("repo/" + pkgColl);
                }
                final ElementImpl permissions = findElement(repoXML, PERMISSIONS_ELEMENT);
                if (permissions != null) {
                    // get user, group and default permissions
                    user = permissions.getAttribute("user");
                    group = permissions.getAttribute("group");
                    password = permissions.getAttribute("password");
                    String mode = permissions.getAttribute("mode");
                    try {
                        perms = Integer.parseInt(mode, 8);
                    } catch (final NumberFormatException e) {
                        permsStr = mode;
                        if (!permsStr.matches("^[rwx-]{9}"))
                            {throw new PackageException("Bad format for mode attribute in <permissions>: " + mode);}
                    }
                }

                // run the pre-setup query if present
                final ElementImpl preSetup = findElement(repoXML, PRE_SETUP_ELEMENT);
                if (preSetup != null) {
                    path = preSetup.getStringValue();
                    if (path.length() > 0)
                        {runQuery(targetCollection, packageDir, path, true);}
                }

                // any required users and group should have been created by the pre-setup query.
                // check for invalid users now.
                checkUserSettings();

                // install
                scanDirectory(packageDir, targetCollection, true);

                // run the post-setup query if present
                final ElementImpl postSetup = findElement(repoXML, POST_SETUP_ELEMENT);
                if (postSetup != null) {
                    path = postSetup.getStringValue();
                    if (path.length() > 0)
                        {runQuery(targetCollection, packageDir, path, false);}
                }

                storeRepoXML(repoXML, targetCollection);

                // TODO: it should be save to clean up the file system after a package
                // has been deployed. Might be enabled after 2.0
                //cleanup(pkgName, repo);

                return targetCollection.getCollectionPath();
            }
        } catch (final XPathException e) {
            throw new PackageException("Error found while processing repo.xml: " + e.getMessage(), e);
        }
    }

    /**
     * After deployment, clean up the package directory and remove all files which have been
     * stored into the db. They are not needed anymore. Only preserve the descriptors and the
     * contents directory.
     *
     * @param pkgName
     * @param repo
     * @throws PackageException
     */
    private void cleanup(String pkgName, ExistRepository repo) throws PackageException {
        final Package pkg = getPackage(pkgName, repo);
        final String abbrev = pkg.getAbbrev();
        final File packageDir = getPackageDir(pkg);
        if (packageDir == null) {
            throw new PackageException("Cleanup: package dir for package " + pkgName + " not found");
        }
        File[] filesToDelete = packageDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                String name = file.getName();
                if (file.isDirectory()) {
                    return !(name.equals(abbrev) || name.equals("content"));
                } else {
                    return !(name.equals("expath-pkg.xml") || name.equals("repo.xml") ||
                            "exist.xml".equals(name) || name.startsWith("icon"));
                }
            }
        });
        for (final File fileToDelete : filesToDelete) {
            try {
                FileUtils.forceDelete(fileToDelete);
            } catch (final IOException e) {
                LOG.warn("Cleanup: failed to delete file " + fileToDelete.getAbsolutePath() + " in package " +
                    pkgName);
            }
        }
    }

    private String getTargetCollection(String targetFromRepo) {
        final String appRoot = (String) broker.getConfiguration().getProperty(PROPERTY_APP_ROOT);
        if (appRoot != null) {
            if (targetFromRepo.startsWith("/db/")) {
                targetFromRepo = targetFromRepo.substring(4);
            }
            return appRoot + targetFromRepo;
        }
        if (targetFromRepo.startsWith("/db")) {
            return targetFromRepo;
        } else {
            return "/db/" + targetFromRepo;
        }
    }

    /**
     * Delete the target collection of the package. If there's no repo.xml descriptor,
     * target will be null.
     *
     * @param pkg
     * @param target
     * @throws PackageException
     */
    private void uninstall(Package pkg, ElementImpl target)
            throws PackageException {
        // determine target collection
        XmldbURI targetCollection;
        if (target == null || target.getStringValue().length() == 0) {
            final String pkgColl = pkg.getAbbrev() + "-" + pkg.getVersion();
            targetCollection = XmldbURI.SYSTEM.append("repo/" + pkgColl);
        } else {
            final String targetPath = target.getStringValue();
            try {
                targetCollection = XmldbURI.create(getTargetCollection(targetPath));
            } catch (final Exception e) {
                throw new PackageException("Bad collection URI for <target> element: " + targetPath);
            }
        }
        final TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        try(final Txn txn = mgr.beginTransaction()) {
            Collection collection = broker.getOrCreateCollection(txn, targetCollection);
            if (collection != null)
                {broker.removeCollection(txn, collection);}
            if (target != null) {
                final XmldbURI configCollection = XmldbURI.CONFIG_COLLECTION_URI.append(targetCollection);
                collection = broker.getOrCreateCollection(txn, configCollection);
                if (collection != null)
                    {broker.removeCollection(txn, collection);}
            }
            mgr.commit(txn);
        } catch (final Exception e) {
            LOG.error("Exception occurred while removing package.", e);
        }
    }

    /**
     * Store repo.xml into the db. Adds the time of deployment to the descriptor.
     *
     * @param repoXML
     * @param targetCollection
     * @throws XPathException
     */
    private void storeRepoXML(DocumentImpl repoXML, XmldbURI targetCollection)
            throws PackageException, XPathException {
        // Store repo.xml
        final DateTimeValue time = new DateTimeValue(new Date());
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        final UpdatingDocumentReceiver receiver = new UpdatingDocumentReceiver(builder, time.getStringValue());
        try {
            repoXML.copyTo(broker, receiver);
        } catch (final SAXException e) {
            throw new PackageException("Error while updating repo.xml: " + e.getMessage());
        }
        builder.endDocument();
        final DocumentImpl updatedXML = builder.getDocument();

        final TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        try(final Txn txn = mgr.beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(txn, targetCollection);
            final XmldbURI name = XmldbURI.createInternal("repo.xml");
            final IndexInfo info = collection.validateXMLResource(txn, broker, name, updatedXML);
            final Permission permission = info.getDocument().getPermissions();
            setPermissions(false, MimeType.XML_TYPE, permission);

            collection.store(txn, broker, info, updatedXML, false);

            mgr.commit(txn);
        } catch (final Exception e) {
            LOG.warn(e);
        }
    }

    private void checkUserSettings() throws PackageException {
        final org.exist.security.SecurityManager secman = broker.getBrokerPool().getSecurityManager();
        try {
            if (group != null && !secman.hasGroup(group)) {
                final GroupAider aider = new GroupAider(group);
                secman.addGroup(aider);
            }
            if (user != null && !secman.hasAccount(user)) {
                final UserAider aider = new UserAider(user);
                aider.setPassword(password);
                if (group != null)
                    {aider.addGroup(group);}

                secman.addAccount(aider);
            }
        } catch (final ConfigurationException e) {
            throw new PackageException("Failed to create user: " + user, e);
        } catch (final PermissionDeniedException e) {
            throw new PackageException("Failed to create user: " + user, e);
        } catch (final EXistException e) {
            throw new PackageException("Failed to create user: " + user, e);
        }
    }

    private Sequence runQuery(XmldbURI targetCollection, File tempDir, String fileName, boolean preInstall)
            throws PackageException, IOException, XPathException {
        final File xquery = new File(tempDir, fileName);
        if (!xquery.canRead()) {
            LOG.warn("The XQuery resource specified in the <setup> element was not found");
            return Sequence.EMPTY_SEQUENCE;
        }
        final XQuery xqs = broker.getXQueryService();
        final XQueryContext ctx = xqs.newContext(AccessContext.REST);
        ctx.declareVariable("dir", tempDir.getAbsolutePath());
        final File home = broker.getConfiguration().getExistHome();
        ctx.declareVariable("home", home.getAbsolutePath());
        if (targetCollection != null) {
            ctx.declareVariable("target", targetCollection.toString());
            ctx.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI + targetCollection.toString());
        } else
            {ctx.declareVariable("target", Sequence.EMPTY_SEQUENCE);}
        if (preInstall)
            // when running pre-setup scripts, base path should point to directory
            // because the target collection does not yet exist
            {ctx.setModuleLoadPath(tempDir.getAbsolutePath());}

        CompiledXQuery compiled;
        try {
            compiled = xqs.compile(ctx, new FileSource(xquery, "UTF-8", false));
            return xqs.execute(compiled, null);
        } catch (final PermissionDeniedException e) {
            throw new PackageException(e.getMessage(), e);
        }
    }

    /**
     * Scan a directory and import all files and sub directories into the target
     * collection.
     *
     * @param directory
     * @param target
     */
    private void scanDirectory(File directory, XmldbURI target, boolean inRootDir) {
        final TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        Collection collection = null;
        try(final Txn txn = mgr.beginTransaction()) {
            collection = broker.getOrCreateCollection(txn, target);
            setPermissions(true, null, collection.getPermissionsNoLock());
            broker.saveCollection(txn, collection);
            mgr.commit(txn);
        } catch (final Exception e) {
            LOG.warn(e);
        }

        try {
            // lock the collection while we store the files
            // TODO: could be released after each operation
            collection.getLock().acquire(Lock.WRITE_LOCK);
            storeFiles(directory, collection, inRootDir);
        } catch (final LockException e) {
            e.printStackTrace();
        } finally {
            collection.getLock().release(Lock.WRITE_LOCK);
        }

        // scan sub directories
        final File[] files = directory.listFiles();
        for (final File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, target.append(file.getName()), false);
            }
        }
    }

    /**
     * Import all files in the given directory into the target collection
     *
     * @param directory
     * @param targetCollection
     */
    private void storeFiles(File directory, Collection targetCollection, boolean inRootDir) {
        final File[] files = directory.listFiles();
        final MimeTable mimeTab = MimeTable.getInstance();
        final TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        for (final File file : files) {
            if (inRootDir && "repo.xml".equals(file.getName()))
                {continue;}
            if (!file.isDirectory()) {
                MimeType mime = mimeTab.getContentTypeFor(file.getName());
                if (mime == null)
                    {mime = MimeType.BINARY_TYPE;}
                final XmldbURI name = XmldbURI.create(file.getName());

                try(final Txn txn = mgr.beginTransaction()) {
                    if (mime.isXMLType()) {
                        final InputSource is = new InputSource(file.toURI().toASCIIString());
                        final IndexInfo info = targetCollection.validateXMLResource(txn, broker, name, is);
                        info.getDocument().getMetadata().setMimeType(mime.getName());
                        final Permission permission = info.getDocument().getPermissions();
                        setPermissions(false, mime, permission);

                        targetCollection.store(txn, broker, info, is, false);
                    } else {
                        final long size = file.length();
                        try(final FileInputStream is = new FileInputStream(file)) {
                            final BinaryDocument doc =
                                    targetCollection.addBinaryResource(txn, broker, name, is, mime.getName(), size);

                            final Permission permission = doc.getPermissions();
                            setPermissions(false, mime, permission);
                            doc.getMetadata().setMimeType(mime.getName());
                            broker.storeXMLResource(txn, doc);
                        }
                    }
                    mgr.commit(txn);
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Set owner, group and permissions. For XQuery resources, always set the executable flag.
     * @param mime
     * @param permission
     */
    private void setPermissions(boolean isCollection, MimeType mime, Permission permission) throws PermissionDeniedException {
        if (user != null){
            permission.setOwner(user);
        }
        if (group != null){
            permission.setGroup(group);
        }

        int mode;
        if (permsStr != null) {
            try {
                permission.setMode(permsStr);
                mode = permission.getMode();
            } catch (final SyntaxException e) {
                LOG.warn("Incorrect permissions string: " + permsStr + ". Falling back to default.");
                mode = permission.getMode();
            }
        } else if (perms > -1) {
            mode = perms;
        } else {
            mode = permission.getMode();
        }

        if (isCollection || (mime != null && mime.getName().equals(MimeType.XQUERY_TYPE.getName()))) {
            mode = mode | 0111;
        }
        permission.setMode(mode);
    }

    private ElementImpl findElement(NodeImpl root, QName qname) throws XPathException {
        final InMemoryNodeSet setupNodes = new InMemoryNodeSet();
        root.selectDescendants(false, new NameTest(Type.ELEMENT, qname), setupNodes);
        if (setupNodes.getItemCount() == 0)
            {return null;}
        return (ElementImpl) setupNodes.itemAt(0);
    }

    private InMemoryNodeSet findElements(NodeImpl root, QName qname) throws XPathException {
        final InMemoryNodeSet setupNodes = new InMemoryNodeSet();
        root.selectDescendants(false, new NameTest(Type.ELEMENT, qname), setupNodes);
        return setupNodes;
    }

    public String getNameFromDescriptor(File xar) throws IOException, PackageException {
        final DocumentImpl doc = getDescriptor(xar);
        final Element root = doc.getDocumentElement();
        return root.getAttribute("name");
    }

    public DocumentImpl getDescriptor(File jar) throws IOException, PackageException {
        final InputStream istream = new BufferedInputStream(new FileInputStream(jar));
        final JarInputStream jis = new JarInputStream(istream);
        JarEntry entry;
        DocumentImpl doc = null;
        while ((entry = jis.getNextJarEntry()) != null) {
            if (!entry.isDirectory() && "expath-pkg.xml".equals(entry.getName())) {
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int c;
                final byte[] b = new byte[4096];
                while ((c = jis.read(b)) > 0) {
                    bos.write(b, 0, c);
                }

                bos.close();

                final byte[] data = bos.toByteArray();

                final ByteArrayInputStream bis = new ByteArrayInputStream(data);
                try {
                    doc = DocUtils.parse(broker.getBrokerPool(), null, bis);
                } catch (final XPathException e) {
                    throw new PackageException("Error while parsing expath-pkg.xml: " + e.getMessage(), e);
                }
                break;
            }
        }
        jis.close();
        return doc;
    }

    /**
     * Update repo.xml while copying it. For security reasons, make sure
     * any default password is removed before uploading.
     */
    private class UpdatingDocumentReceiver extends DocumentBuilderReceiver {

        private String time;
        private Stack<String> stack = new Stack<String>();

        public UpdatingDocumentReceiver( MemTreeBuilder builder, String time )
        {
            super( builder, false );
            this.time = time;
        }

        @Override
        public void startElement(QName qname, AttrList attribs) {
            stack.push(qname.getLocalPart());
            AttrList newAttrs = attribs;
            if (attribs != null && "permissions".equals(qname.getLocalPart())) {
                newAttrs = new AttrList();
                for (int i = 0; i < attribs.getLength(); i++) {
                    if (!"password". equals(attribs.getQName(i).getLocalPart())) {
                        newAttrs.addAttribute(attribs.getQName(i), attribs.getValue(i), attribs.getType(i));
                    }
                }
            }

            if (!"deployed".equals(qname.getLocalPart())) {
                super.startElement(qname, newAttrs);
            }
        }

        @Override
        public void startElement(String namespaceURI, String localName,
                                 String qName, Attributes attrs) throws SAXException {
            stack.push(localName);
            if (!"deployed".equals(localName))
                {super.startElement(namespaceURI, localName, qName, attrs);}
        }

        @Override
        public void endElement(QName qname) throws SAXException {
            stack.pop();
            if ("meta".equals(qname.getLocalPart())) {
                addDeployTime();
            }
            if (!"deployed".equals(qname.getLocalPart())) {
                super.endElement(qname);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            stack.pop();
            if ("meta".equals(localName)) {
                addDeployTime();
            }
            if (!"deployed".equals(localName)) {
                super.endElement(uri, localName, qName);
            }
        }

        @Override
        public void attribute(QName qname, String value) throws SAXException {
            final String current = stack.peek();
            if (!("permissions".equals(current) && "password".equals(qname.getLocalPart()))) {
                super.attribute(qname, value);
            }
        }

        @Override
        public void characters(char[] ch, int start, int len)
                throws SAXException {
            final String current = stack.peek();
            if (!"deployed".equals(current))
                {super.characters(ch, start, len);}
        }

        @Override
        public void characters(CharSequence seq) throws SAXException {
            final String current = stack.peek();
            if (!"deployed".equals(current))
                {super.characters(seq);}
        }

        private void addDeployTime() throws SAXException {
            super.startElement(DEPLOYED_ELEMENT, null);
            super.characters(time);
            super.endElement(DEPLOYED_ELEMENT);
        }
    }
}
