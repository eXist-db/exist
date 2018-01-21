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
package org.exist.repo;

import com.evolvedbinary.j8fu.Either;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.SystemProperties;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.memtree.*;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.source.FileSource;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final QName RESOURCES_ELEMENT = new QName("resources", REPO_NAMESPACE);
    private static final String RESOURCES_PATH_ATTRIBUTE = "path";

    private final DBBroker broker;

    private static class RequestedPerms {
        final String user;
        final String password;
        final Optional<String> group;
        final Either<Integer, String> permissions;

        private RequestedPerms(final String user, final String password, final Optional<String> group, final Either<Integer, String> permissions) {
            this.user = user;
            this.password = password;
            this.group = group;
            this.permissions = permissions;
        }
    }

//    private Optional<RequestedPerms> requestedPerms = Optional.empty();

    public Deployment(final DBBroker broker) {
        this.broker = broker;
    }

    protected Optional<Path> getPackageDir(final String pkgName, final Optional<ExistRepository> repo) throws PackageException {
        Optional<Path> packageDir = Optional.empty();

        if (repo.isPresent()) {
            for (final Packages pp : repo.get().getParentRepo().listPackages()) {
                final org.expath.pkg.repo.Package pkg = pp.latest();
                if (pkg.getName().equals(pkgName)) {
                    packageDir = Optional.of(getPackageDir(pkg));
                }
            }
        }
        return packageDir;
    }

    protected Path getPackageDir(final Package pkg) {
        final FileSystemStorage.FileSystemResolver resolver = (FileSystemStorage.FileSystemResolver) pkg.getResolver();
        return resolver.resolveResourceAsFile("").toPath();
    }

    protected Optional<org.expath.pkg.repo.Package> getPackage(final String pkgName, final Optional<ExistRepository> repo) throws PackageException {
        if (repo.isPresent()) {
            for (final Packages pp : repo.get().getParentRepo().listPackages()) {
                final org.expath.pkg.repo.Package pkg = pp.latest();
                if (pkg.getName().equals(pkgName)) {
                    return Optional.ofNullable(pkg);
                }
            }
        }
        return Optional.empty();
    }

    protected DocumentImpl getRepoXML(final Path packageDir) throws PackageException {
        // find and parse the repo.xml descriptor
        final Path repoFile = packageDir.resolve("repo.xml");
        if (!Files.isReadable(repoFile)) {
            return null;
        }
        try(final InputStream is = Files.newInputStream(repoFile)) {
            return DocUtils.parse(broker.getBrokerPool(), null, is);
        } catch (final XPathException | IOException e) {
            throw new PackageException("Failed to parse repo.xml: " + e.getMessage(), e);
        }
    }

    public Optional<String> installAndDeploy(final Path xar, final PackageLoader loader) throws PackageException, IOException {
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
    public Optional<String> installAndDeploy(final Path xar, final PackageLoader loader, boolean enforceDeps) throws PackageException, IOException {
        final Optional<DocumentImpl> descriptor = getDescriptor(xar);
        if(!descriptor.isPresent()) {
            throw new PackageException("Missing descriptor from package: " + xar.toAbsolutePath());
        }
        final DocumentImpl document = descriptor.get();

        final ElementImpl root = (ElementImpl) document.getDocumentElement();
        final String name = root.getAttribute("name");
        final String pkgVersion = root.getAttribute("version");

        final Optional<ExistRepository> repo = broker.getBrokerPool().getExpathRepo();
	    if (repo.isPresent()) {
            final Packages packages = repo.get().getParentRepo().getPackages(name);

            if (packages != null && (!enforceDeps || pkgVersion.equals(packages.latest().getVersion()))) {
                LOG.info("Application package " + name + " already installed. Skipping.");
                final Package pkg = packages.latest();
                return Optional.of(getTargetCollection(pkg, getPackageDir(pkg)));
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
                        if (repo.get().getParentRepo().getPackages(pkgName) != null) {
                            LOG.debug("Package " + pkgName + " already installed");
                            Packages pkgs = repo.get().getParentRepo().getPackages(pkgName);
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
                            final Path depFile = loader.load(pkgName, version);
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
                throw new PackageException("Invalid descriptor found in " + xar.toAbsolutePath().toString());
            }

            // installing the xar into the expath repo
            LOG.info("Installing package " + xar.toAbsolutePath().toString());
            final UserInteractionStrategy interact = new BatchUserInteraction();
            final org.expath.pkg.repo.Package pkg = repo.get().getParentRepo().installPackage(xar, true, interact);
            final ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
            if (info != null && !info.getJars().isEmpty()) {
                ClasspathHelper.updateClasspath(broker.getBrokerPool(), pkg);
            }
            broker.getBrokerPool().getXQueryPool().clear();
            final String pkgName = pkg.getName();
            // signal status
            broker.getBrokerPool().reportStatus("Installing app: " + pkg.getAbbrev());
            repo.get().reportAction(ExistRepository.Action.INSTALL, pkg.getName());

            LOG.info("Deploying package " + pkgName);
            return deploy(pkgName, repo, null);
        }

	    // Totally unneccessary to do the above if repo is unavailable.
	    return Optional.empty();
    }

    private void checkProcessorVersion(final PackageLoader.Version version) throws PackageException {
        final String procVersion = SystemProperties.getInstance().getSystemProperty("product-version", "1.0");

        final DependencyVersion depVersion = version.getDependencyVersion();
        if (!depVersion.isCompatible(procVersion)) {
            throw new PackageException("Package requires eXistdb version " + version.toString() + ". " +
                "Installed version is " + procVersion);
        }
    }

    public Optional<String> undeploy(final String pkgName, final Optional<ExistRepository> repo) throws PackageException {
        final Optional<Path> maybePackageDir = getPackageDir(pkgName, repo);
        if (!maybePackageDir.isPresent()) {
            // fails silently if package dir is not found?
            return Optional.empty();
        }

        final Path packageDir = maybePackageDir.get();
        final Optional<Package> pkg = getPackage(pkgName, repo);
        final DocumentImpl repoXML;
        try {
            repoXML = getRepoXML(packageDir);
        } catch (PackageException e) {
            if (pkg.isPresent()) {
                uninstall(pkg.get(), Optional.empty());
            }
            throw new PackageException("Failed to remove package from database " +
                    "due to error in repo.xml: " + e.getMessage(), e);
        }

        if (repoXML != null) {
            try {
                final Optional<ElementImpl> cleanup = findElement(repoXML, CLEANUP_ELEMENT);
                if(cleanup.isPresent()) {
                    runQuery(null, packageDir, cleanup.get().getStringValue(), false);
                }

                final Optional<ElementImpl> target = findElement(repoXML, TARGET_COLL_ELEMENT);
                if (pkg.isPresent()) {
                    uninstall(pkg.get(), target);
                }

                return target.map(e -> Optional.ofNullable(e.getStringValue())).orElseGet(() -> Optional.of(getTargetFallback(pkg.get()).getCollectionPath()));
            } catch (final XPathException e) {
                throw new PackageException("Error found while processing repo.xml: " + e.getMessage(), e);
            } catch (final IOException e) {
                throw new PackageException("Error found while processing repo.xml: " + e.getMessage(), e);
            }
        } else {
            // we still may need to remove the copy of the package from /db/system/repo
            if (pkg.isPresent()) {
		        uninstall(pkg.get(), Optional.empty());
	        }
        }
        return Optional.empty();
    }

    public Optional<String> deploy(final String pkgName, final Optional<ExistRepository> repo, final String userTarget) throws PackageException, IOException {
        final Optional<Path> maybePackageDir = getPackageDir(pkgName, repo);
        if (!maybePackageDir.isPresent()) {
            throw new PackageException("Package not found: " + pkgName);
        }

        final Path packageDir = maybePackageDir.get();

        final DocumentImpl repoXML = getRepoXML(packageDir);
        if (repoXML == null) {
            return Optional.empty();
        }
        try {
            // if there's a <setup> element, run the query it points to
            final Optional<ElementImpl> setup = findElement(repoXML, SETUP_ELEMENT);
            final Optional<String> setupPath = setup.map(ElementImpl::getStringValue).filter(s -> !s.isEmpty());

            if (setupPath.isPresent()) {
                runQuery(null, packageDir, setupPath.get(), true);
                return Optional.empty();
            } else {
                // otherwise copy all child directories to the target collection
                XmldbURI targetCollection = null;
                if (userTarget != null) {
                    try {
                        targetCollection = XmldbURI.create(userTarget);
                    } catch (final IllegalArgumentException e) {
                        throw new PackageException("Bad collection URI: " + userTarget, e);
                    }
                } else {
                    final Optional<ElementImpl> target = findElement(repoXML, TARGET_COLL_ELEMENT);
                    final Optional<String> targetPath = target.map(ElementImpl::getStringValue).filter(s -> !s.isEmpty());

                    if (targetPath.isPresent()) {
                        // determine target collection
                        try {
                            targetCollection = XmldbURI.create(getTargetCollection(targetPath.get()));
                        } catch (final IllegalArgumentException e) {
                            throw new PackageException("Bad collection URI for <target> element: " + targetPath.get(), e);
                        }
                    } else {
                        LOG.warn("EXPath Package '" + pkgName + "' does not contain a <target> in its repo.xml, no files will be deployed to /apps");
                    }
                }

                if (targetCollection == null) {
                    // no target means: package does not need to be deployed into database
                    // however, we need to preserve a copy for backup purposes
                    final Optional<Package> pkg = getPackage(pkgName, repo);
		            pkg.orElseThrow(() -> new XPathException("expath repository is not available so the package was not stored."));
                    final String pkgColl = pkg.get().getAbbrev() + "-" + pkg.get().getVersion();
                    targetCollection = XmldbURI.SYSTEM.append("repo/" + pkgColl);
                }

                // extract the permissions (if any)
                final Optional<ElementImpl> permissions = findElement(repoXML, PERMISSIONS_ELEMENT);
                final Optional<RequestedPerms> requestedPerms = permissions.flatMap(elem -> {
                    final Optional<Either<Integer, String>> perms = Optional.ofNullable(elem.getAttribute("mode")).flatMap(mode -> {
                        try {
                            return Optional.of(Either.Left(Integer.parseInt(mode, 8)));
                        } catch(final NumberFormatException e) {
                            if(mode.matches("^[rwx-]{9}")) {
                                return Optional.of(Either.Right(mode));
                            } else {
                                return Optional.empty();
                            }
                        }
                    });

                    return perms.map(p -> new RequestedPerms(
                        elem.getAttribute("user"),
                        elem.getAttribute("password"),
                        Optional.ofNullable(elem.getAttribute("group")),
                        p
                    ));
                });

                //check that if there were permissions then we were able to parse them, a failure would be related to the mode string
                if(permissions.isPresent() && !requestedPerms.isPresent()) {
                    final String mode = permissions.map(elem -> elem.getAttribute("mode")).orElse(null);
                    throw new PackageException("Bad format for mode attribute in <permissions>: " + mode);
                }

                // run the pre-setup query if present
                final Optional<ElementImpl> preSetup = findElement(repoXML, PRE_SETUP_ELEMENT);
                final Optional<String> preSetupPath = preSetup.map(ElementImpl::getStringValue).filter(s -> !s.isEmpty());

                if(preSetupPath.isPresent()) {
                    runQuery(targetCollection, packageDir, preSetupPath.get(), true);
                }

                // any required users and group should have been created by the pre-setup query.
                // check for invalid users now.
                if(requestedPerms.isPresent()) {
                    checkUserSettings(requestedPerms.get());
                }

                final InMemoryNodeSet resources = findElements(repoXML,RESOURCES_ELEMENT);

                // install
                final List<String> errors = scanDirectory(packageDir, targetCollection, resources, true, false,
                        requestedPerms);

                // run the post-setup query if present
                final Optional<ElementImpl> postSetup = findElement(repoXML, POST_SETUP_ELEMENT);
                final Optional<String> postSetupPath = postSetup.map(ElementImpl::getStringValue).filter(s -> !s.isEmpty());

                if(postSetupPath.isPresent()) {
                    runQuery(targetCollection, packageDir, postSetupPath.get(), false);
                }

                storeRepoXML(repoXML, targetCollection, requestedPerms);

                // TODO: it should be safe to clean up the file system after a package
                // has been deployed. Might be enabled after 2.0
                //cleanup(pkgName, repo);

                if (!errors.isEmpty()) {
                    throw new PackageException("Deployment incomplete, " + errors.size() + " issues found: " +
                        errors.stream().collect(Collectors.joining("; ")));
                }
                return Optional.ofNullable(targetCollection.getCollectionPath());
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
    private void cleanup(final String pkgName, final Optional<ExistRepository> repo) throws PackageException {
        if (repo.isPresent()) {
            final Optional<Package> pkg = getPackage(pkgName, repo);
            final Optional<Path> maybePackageDir = pkg.map(this::getPackageDir);
            if (!maybePackageDir.isPresent()) {
                throw new PackageException("Cleanup: package dir for package " + pkgName + " not found");
            }

            final Path packageDir = maybePackageDir.get();
            final String abbrev = pkg.get().getAbbrev();

            try(final Stream<Path> filesToDelete = Files.find(packageDir, 1, (path, attrs) -> {
                    if(path.equals(packageDir)) {
                        return false;
                    }
                    final String name = FileUtils.fileName(path);
                    if (attrs.isDirectory()) {
                        return !(name.equals(abbrev) || name.equals("content"));
                    } else {
                        return !(name.equals("expath-pkg.xml") || name.equals("repo.xml") ||
                                "exist.xml".equals(name) || name.startsWith("icon"));
                    }
            })) {

                filesToDelete.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch(final IOException ioe) {
                        LOG.warn("Cleanup: failed to delete file " + path.toAbsolutePath().toString() + " in package " + pkgName);
                    }
                });
            } catch (final IOException ioe) {
                LOG.warn("Cleanup: failed to delete files", ioe);
            }
        }
    }

    /**
     * Get the target collection for the given package, which resides in pkgDir.
     * Returns path to cached .xar for library packages.
     *
     * @param pkg
     * @param pkgDir
     * @return
     * @throws PackageException
     */
    private String getTargetCollection(final Package pkg, final Path pkgDir) throws PackageException {
        final DocumentImpl repoXML = getRepoXML(pkgDir);
        if (repoXML != null) {
            try {
                final Optional<ElementImpl> target = findElement(repoXML, TARGET_COLL_ELEMENT);
                return target.map(ElementImpl::getStringValue).map(this::getTargetCollection).map(XmldbURI::create).map(XmldbURI::getCollectionPath)
                        .orElseGet(() -> getTargetFallback(pkg).getCollectionPath());
            } catch (XPathException e) {
                throw new PackageException("Failed to determine target collection");
            }
        } else {
            return getTargetFallback(pkg).getCollectionPath();
        }
    }

    private XmldbURI getTargetFallback(final Package pkg) {
        final String pkgColl = pkg.getAbbrev() + "-" + pkg.getVersion();
        return XmldbURI.SYSTEM.append("repo/" + pkgColl);
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
    private void uninstall(final Package pkg, final Optional<ElementImpl> target)
            throws PackageException {
        // determine target collection
        final Optional<String> targetPath = target.map(ElementImpl::getStringValue).filter(s -> !s.isEmpty());
        final XmldbURI targetCollection = targetPath.map(s -> XmldbURI.create(getTargetCollection(s)))
                .orElseGet(() -> getTargetFallback(pkg));

        final TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        try(final Txn transaction = mgr.beginTransaction()) {
            Collection collection = broker.getOrCreateCollection(transaction, targetCollection);
            if (collection != null) {
                broker.removeCollection(transaction, collection);
            }
            if (target != null) {
                final XmldbURI configCollection = XmldbURI.CONFIG_COLLECTION_URI.append(targetCollection);
                collection = broker.getOrCreateCollection(transaction, configCollection);
                if (collection != null) {
                    broker.removeCollection(transaction, collection);
                }
            }
            mgr.commit(transaction);
        } catch (final PermissionDeniedException | IOException | TriggerException | TransactionException e) {
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
    private void storeRepoXML(final DocumentImpl repoXML, final XmldbURI targetCollection, final Optional<RequestedPerms> requestedPerms)
            throws PackageException, XPathException {
        // Store repo.xml
        final DateTimeValue time = new DateTimeValue(new Date());
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        final UpdatingDocumentReceiver receiver = new UpdatingDocumentReceiver(builder, time.getStringValue());
        try {
            repoXML.copyTo(broker, receiver);
        } catch (final SAXException e) {
            throw new PackageException("Error while updating repo.xml in-memory: " + e.getMessage(), e);
        }
        builder.endDocument();
        final DocumentImpl updatedXML = builder.getDocument();

        final TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        try(final Txn transaction = mgr.beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(transaction, targetCollection);
            final XmldbURI name = XmldbURI.createInternal("repo.xml");
            final IndexInfo info = collection.validateXMLResource(transaction, broker, name, updatedXML);
            final Permission permission = info.getDocument().getPermissions();
            setPermissions(requestedPerms, false, MimeType.XML_TYPE, permission);

            collection.store(transaction, broker, info, updatedXML);

            mgr.commit(transaction);
        } catch (final PermissionDeniedException | IOException | SAXException | LockException | EXistException e) {
            throw new PackageException("Error while storing updated repo.xml: " + e.getMessage(), e);
        }
    }

    private void checkUserSettings(final RequestedPerms requestedPerms) throws PackageException {
        Objects.requireNonNull(requestedPerms);
        final org.exist.security.SecurityManager secman = broker.getBrokerPool().getSecurityManager();
        try {
            if (requestedPerms.group.filter(g -> !secman.hasGroup(g)).isPresent()) {
                secman.addGroup(broker, new GroupAider(requestedPerms.group.get()));
            }

            if (!secman.hasAccount(requestedPerms.user)) {
                final UserAider aider = new UserAider(requestedPerms.user);
                aider.setPassword(requestedPerms.password);
                requestedPerms.group.ifPresent(groupName -> aider.addGroup(groupName));
                secman.addAccount(broker, aider);
            }
        } catch (final PermissionDeniedException | EXistException e) {
            throw new PackageException("Failed to create user: " + requestedPerms.user, e);
        }
    }

    private Sequence runQuery(final XmldbURI targetCollection, final Path tempDir, final String fileName, final boolean preInstall)
            throws PackageException, IOException, XPathException {
        final Path xquery = tempDir.resolve(fileName);
        if (!Files.isReadable(xquery)) {
            LOG.warn("The XQuery resource specified in the <setup> element was not found");
            return Sequence.EMPTY_SEQUENCE;
        }
        final XQuery xqs = broker.getBrokerPool().getXQueryService();
        final XQueryContext ctx = new XQueryContext(broker.getBrokerPool());
        ctx.declareVariable("dir", tempDir.toAbsolutePath().toString());
        final Optional<Path> home = broker.getConfiguration().getExistHome();
        if(home.isPresent()) {
            ctx.declareVariable("home", home.get().toAbsolutePath().toString());
        }

        if (targetCollection != null) {
            ctx.declareVariable("target", targetCollection.toString());
            ctx.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI + targetCollection.toString());
        } else
            {ctx.declareVariable("target", Sequence.EMPTY_SEQUENCE);}
        if (preInstall) {
            // when running pre-setup scripts, base path should point to directory
            // because the target collection does not yet exist
            ctx.setModuleLoadPath(tempDir.toAbsolutePath().toString());
        }

        CompiledXQuery compiled;
        try {
            compiled = xqs.compile(broker, ctx, new FileSource(xquery, false));
            return xqs.execute(broker, compiled, null);
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
    private List<String> scanDirectory(final Path directory, final XmldbURI target, final InMemoryNodeSet resources,
                               final boolean inRootDir, final boolean isResourcesDir, final Optional<RequestedPerms> requestedPerms) {
        return scanDirectory(directory, target, resources, inRootDir, isResourcesDir, requestedPerms, new ArrayList<>());
    }

    private List<String> scanDirectory(final Path directory, final XmldbURI target, final InMemoryNodeSet resources,
                                       final boolean inRootDir, final boolean isResourcesDir, final
                                       Optional<RequestedPerms> requestedPerms, final List<String> errors) {
        final TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        Collection collection = null;
        try(final Txn transaction = mgr.beginTransaction()) {
            collection = broker.getOrCreateCollection(transaction, target);
            setPermissions(requestedPerms, true, null, collection.getPermissionsNoLock());
            broker.saveCollection(transaction, collection);
            mgr.commit(transaction);
        } catch (final PermissionDeniedException | TriggerException | IOException | TransactionException e) {
            LOG.warn(e);
            errors.add(e.getMessage());
        }

        final boolean isResources = isResourcesDir || isResourceDir(target, resources);

        // the root dir is not allowed to be a resources directory
        if (!inRootDir && isResources) {
            try {
                storeBinaryResources(directory, collection, requestedPerms, errors);
            } catch (Exception e) {
                LOG.error(e.getMessage(), e); 
            }
        } else {
            storeFiles(directory, collection, inRootDir, requestedPerms, errors);
        }

        // scan sub directories
        try(final Stream<Path> subDirs = Files.find(directory, 1, (path, attrs) -> (!path.equals(directory)) && attrs.isDirectory())) {
            subDirs.forEach(path -> scanDirectory(path, target.append(FileUtils.fileName(path)), resources, false,
                    isResources, requestedPerms, errors));
        } catch(final IOException ioe) {
            LOG.warn("Unable to scan sub-directories", ioe);
        }
        return errors;
    }

    private boolean isResourceDir(XmldbURI target, InMemoryNodeSet resources) {
        // iterate here or pass into scandirectory directly or even save as class property???
        try {
            for (final SequenceIterator i = resources.iterate(); i.hasNext(); ) {
                final ElementImpl child = (ElementImpl) i.nextItem();
                final String resourcePath = child.getAttribute(RESOURCES_PATH_ATTRIBUTE);
                if (target.toString().endsWith(resourcePath)) {
                    return true;
                }
            }
        } catch (XPathException e) {
            LOG.warn("Caught exception while reading resource list in repo.xml: " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * Import all files in the given directory into the target collection
     *
     * @param directory
     * @param targetCollection
     */
    private void storeFiles(final Path directory, final Collection targetCollection, final boolean inRootDir, final
        Optional<RequestedPerms> requestedPerms, final List<String> errors) {
        List<Path> files;
        try {
            files = FileUtils.list(directory);
        } catch(final IOException ioe) {
            LOG.error(ioe);
            errors.add(FileUtils.fileName(directory) + ": " + ioe.getMessage());
            files = Collections.EMPTY_LIST;
        }

        final MimeTable mimeTab = MimeTable.getInstance();
        final TransactionManager mgr = broker.getBrokerPool().getTransactionManager();

        for (final Path file : files) {
            if (inRootDir && FileUtils.fileName(file).equals("repo.xml")) {
                continue;
            }
            if (!Files.isDirectory(file)) {
                MimeType mime = mimeTab.getContentTypeFor(FileUtils.fileName(file));
                if (mime == null) {
                    mime = MimeType.BINARY_TYPE;
                }
                final XmldbURI name = XmldbURI.create(FileUtils.fileName(file));

                try(final Txn transaction = mgr.beginTransaction()) {
                    if (mime.isXMLType()) {
                        try {
                            final InputSource is = new InputSource(file.toUri().toASCIIString());
                            final IndexInfo info = targetCollection.validateXMLResource(transaction, broker, name, is);
                            info.getDocument().getMetadata().setMimeType(mime.getName());
                            final Permission permission = info.getDocument().getPermissions();
                            setPermissions(requestedPerms, false, mime, permission);

                            targetCollection.store(transaction, broker, info, is);
                        } catch (Exception e) {
                            //check for .html ending
                            if(mime.getName().equals(MimeType.HTML_TYPE.getName())){
                                //store it
                                storeBinary(targetCollection, file, mime, name, requestedPerms, transaction);
                            } else {
                                errors.add(FileUtils.fileName(file) + ": " + e.getMessage());
                            }
                        }
                    } else {
                        final long size = Files.size(file);
                        try(final InputStream is = Files.newInputStream(file)) {
                            final BinaryDocument doc =
                                    targetCollection.addBinaryResource(transaction, broker, name, is, mime.getName(), size);

                            final Permission permission = doc.getPermissions();
                            setPermissions(requestedPerms, false, mime, permission);
                            doc.getMetadata().setMimeType(mime.getName());
                            broker.storeXMLResource(transaction, doc);
                        }
                    }
                    mgr.commit(transaction);
                } catch (final EXistException | PermissionDeniedException | LockException | TriggerException | IOException e) {
                    LOG.error(e.getMessage(), e);
                    errors.add(FileUtils.fileName(file) + ": " + e.getMessage());
                }
            }
        }
    }

    private void storeBinary(final Collection targetCollection, final Path file, final MimeType mime, final XmldbURI name, final Optional<RequestedPerms> requestedPerms, final Txn transaction) throws
            IOException, EXistException, PermissionDeniedException, LockException, TriggerException {
        final long size = Files.size(file);
        try (final InputStream is = Files.newInputStream(file)) {
            final BinaryDocument doc =
                    targetCollection.addBinaryResource(transaction, broker, name, is, mime.getName(), size);

            final Permission permission = doc.getPermissions();
            setPermissions(requestedPerms, false, mime, permission);
            doc.getMetadata().setMimeType(mime.getName());
            broker.storeXMLResource(transaction, doc);
        }
    }

    private void storeBinaryResources(final Path directory, final Collection targetCollection, final
    Optional<RequestedPerms> requestedPerms, final List<String> errors) throws IOException, EXistException,
            PermissionDeniedException, LockException, TriggerException {
        final TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        try(DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (final Path entry: stream) {
                if (!Files.isDirectory(entry)) {
                    final XmldbURI name = XmldbURI.create(FileUtils.fileName(entry));
                    try(final Txn transaction = mgr.beginTransaction()) {
                        storeBinary(targetCollection, entry, MimeType.BINARY_TYPE, name, requestedPerms, transaction);
                        mgr.commit(transaction);
                    } catch (final Exception e) {
                        LOG.error(e.getMessage(), e);
                        errors.add(e.getMessage());
                    }
                }  
            }
        }
    }

    /**
     * Set owner, group and permissions. For XQuery resources, always set the executable flag.
     * @param mime
     * @param permission
     */
    private void setPermissions(final Optional<RequestedPerms> requestedPerms, final boolean isCollection, final MimeType mime, final Permission permission) throws PermissionDeniedException {
        int mode = permission.getMode();
        if(requestedPerms.isPresent()) {
            final RequestedPerms perms = requestedPerms.get();
            permission.setOwner(perms.user);

            if(perms.group.isPresent()) {
                permission.setGroup(perms.group.get());
            }

            mode = perms.permissions.map(permStr -> {
                try {
                    permission.setMode(permStr);
                    return permission.getMode();
                } catch (final PermissionDeniedException | SyntaxException e) {
                    LOG.warn("Unable to set permissions string: " + permStr + ". Falling back to default.");
                    return permission.getMode();
                }
            }).fold(l -> l, r -> r);
        }

        if (isCollection || (mime != null && mime.getName().equals(MimeType.XQUERY_TYPE.getName()))) {
            mode = mode | 0111;     //TODO(AR) Whoever did this - this is a really bad idea. You are circumventing the security of the system
        }
        permission.setMode(mode);
    }

    private Optional<ElementImpl> findElement(final NodeImpl root, final QName qname) throws XPathException {
        final InMemoryNodeSet setupNodes = new InMemoryNodeSet();
        root.selectDescendants(false, new NameTest(Type.ELEMENT, qname), setupNodes);
        if (setupNodes.getItemCount() == 0) {
            return Optional.empty();
        }
        return Optional.of((ElementImpl) setupNodes.itemAt(0));
    }

    private InMemoryNodeSet findElements(final NodeImpl root, final QName qname) throws XPathException {
        final InMemoryNodeSet setupNodes = new InMemoryNodeSet();
        root.selectDescendants(false, new NameTest(Type.ELEMENT, qname), setupNodes);
        return setupNodes;
    }

    public Optional<String> getNameFromDescriptor(final Path xar) throws IOException, PackageException {
        final Optional<DocumentImpl> doc = getDescriptor(xar);
        return doc.map(DocumentImpl::getDocumentElement).map(root -> root.getAttribute("name"));
    }

    public Optional<DocumentImpl> getDescriptor(final Path jar) throws IOException, PackageException {
        try(final JarInputStream jis = new JarInputStream(Files.newInputStream(jar))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (!entry.isDirectory() && "expath-pkg.xml".equals(entry.getName())) {
                    try {
                        return Optional.of(DocUtils.parse(broker.getBrokerPool(), null, jis));
                    } catch (final XPathException e) {
                        throw new PackageException("Error while parsing expath-pkg.xml: " + e.getMessage(), e);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Update repo.xml while copying it. For security reasons, make sure
     * any default password is removed before uploading.
     */
    private static class UpdatingDocumentReceiver extends DocumentBuilderReceiver {
        private final String time;
        private final Deque<String> stack = new ArrayDeque<>();

        public UpdatingDocumentReceiver(final MemTreeBuilder builder, final String time) {
            super(builder, false);
            this.time = time;
        }

        @Override
        public void startElement(final QName qname, final AttrList attribs) {
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
        public void startElement(final String namespaceURI, final String localName,
                                 final String qName, final Attributes attrs) throws SAXException {
            stack.push(localName);
            if (!"deployed".equals(localName)) {
                super.startElement(namespaceURI, localName, qName, attrs);
            }
        }

        @Override
        public void endElement(final QName qname) throws SAXException {
            stack.pop();
            if ("meta".equals(qname.getLocalPart())) {
                addDeployTime();
            }
            if (!"deployed".equals(qname.getLocalPart())) {
                super.endElement(qname);
            }
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) throws SAXException {
            stack.pop();
            if ("meta".equals(localName)) {
                addDeployTime();
            }
            if (!"deployed".equals(localName)) {
                super.endElement(uri, localName, qName);
            }
        }

        @Override
        public void attribute(final QName qname, final String value) throws SAXException {
            final String current = stack.peek();
            if (!("permissions".equals(current) && "password".equals(qname.getLocalPart()))) {
                super.attribute(qname, value);
            }
        }

        @Override
        public void characters(final char[] ch, final int start, final int len) throws SAXException {
            final String current = stack.peek();
            if (!"deployed".equals(current)) {
                super.characters(ch, start, len);
            }
        }

        @Override
        public void characters(final CharSequence seq) throws SAXException {
            final String current = stack.peek();
            if (!"deployed".equals(current)) {
                super.characters(seq);
            }
        }

        private void addDeployTime() throws SAXException {
            super.startElement(DEPLOYED_ELEMENT, null);
            super.characters(time);
            super.endElement(DEPLOYED_ELEMENT);
        }
    }
}
