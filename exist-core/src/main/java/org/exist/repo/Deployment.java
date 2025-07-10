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

import com.evolvedbinary.j8fu.Either;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.SystemProperties;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.memtree.*;
import org.exist.security.*;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.source.FileSource;
import org.exist.storage.DBBroker;
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
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.deps.DependencyVersion;
import org.expath.pkg.repo.tui.BatchUserInteraction;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
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
        return resolver.resolveResourceAsFile("");
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

    protected DocumentImpl getRepoXML(final DBBroker broker, final Path packageDir) throws PackageException {
        // find and parse the repo.xml descriptor
        final Path repoFile = packageDir.resolve("repo.xml");
        if (!Files.isReadable(repoFile)) {
            return null;
        }
        try(final InputStream is = new BufferedInputStream(Files.newInputStream(repoFile))) {
            return DocUtils.parse(broker.getBrokerPool(), null, is, null);
        } catch (final XPathException | IOException e) {
            throw new PackageException("Failed to parse repo.xml: " + e.getMessage(), e);
        }
    }

    public Optional<String> installAndDeploy(final DBBroker broker, final Txn transaction, final XarSource xar, final PackageLoader loader) throws PackageException, IOException {
        return installAndDeploy(broker, transaction, xar, loader, true);
    }

    /**
     * Install and deploy a give xar archive. Dependencies are installed from
     * the PackageLoader.
     *
     * @param broker the broker to use
     * @param transaction the transaction for this deployment task
     * @param xar the .xar file to install
     * @param loader package loader to use
     * @param enforceDeps when set to true, the method will throw an exception if a dependency could not be resolved
     *                    or an older version of the required dependency is installed and needs to be replaced.
     * @return the collection path to which the package was deployed or Optional.empty if not deployed
     * @throws PackageException if package installation failed
     * @throws IOException in case of an IO error
     */
    public Optional<String> installAndDeploy(final DBBroker broker, final Txn transaction, final XarSource xar, final PackageLoader loader, boolean enforceDeps) throws PackageException, IOException {
        final Optional<DocumentImpl> descriptor = getDescriptor(broker, xar);
        if(!descriptor.isPresent()) {
            throw new PackageException("Missing descriptor from package: " + xar.getURI());
        }
        final DocumentImpl document = descriptor.get();

        final ElementImpl root = (ElementImpl) document.getDocumentElement();
        final String name = root.getAttribute("name");
        final String pkgVersion = root.getAttribute("version");

        final Optional<ExistRepository> repo = broker.getBrokerPool().getExpathRepo();
	    if (repo.isPresent()) {
            final Packages packages = repo.get().getParentRepo().getPackages(name);

            if (packages != null && (!enforceDeps || pkgVersion.equals(packages.latest().getVersion()))) {
                LOG.info("Application package {} already installed. Skipping.", name);
                final Package pkg = packages.latest();
                return Optional.of(getTargetCollection(broker, pkg, getPackageDir(pkg)));
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
                    if (!semVer.isEmpty()) {
                        version = new PackageLoader.Version(semVer, true);
                    } else if (!semVerMax.isEmpty() || !semVerMin.isEmpty()) {
                        version = new PackageLoader.Version(semVerMin.isEmpty() ? null: semVerMin, semVerMax.isEmpty() ? null: semVerMax);
                    } else if (!versionStr.isEmpty()) {
                        version = new PackageLoader.Version(versionStr, false);
                    }

                    if (processor.equals(PROCESSOR_NAME) && version != null) {
                        checkProcessorVersion(version);
                    } else if (!pkgName.isEmpty()) {
                        LOG.info("Package {} depends on {}", name, pkgName);
                        boolean isInstalled = false;
                        if (repo.get().getParentRepo().getPackages(pkgName) != null) {
                            LOG.debug("Package {} already installed", pkgName);
                            Packages pkgs = repo.get().getParentRepo().getPackages(pkgName);
                            // check if installed package matches required version
                            if (pkgs != null) {
                                if (version != null) {
                                    Package latest = pkgs.latest();
                                    DependencyVersion depVersion = version.getDependencyVersion();
                                    if (depVersion.isCompatible(latest.getVersion())) {
                                        isInstalled = true;
                                    } else {
                                        LOG.debug("Package {} needs to be upgraded", pkgName);
                                        if (enforceDeps) {
                                            throw new PackageException("Package requires version " + version +
                                                " of package " + pkgName +
                                                ". Installed version is " + latest.getVersion() + ". Please upgrade!");
                                        }
                                    }
                                } else {
                                    isInstalled = true;
                                }
                                if (isInstalled) {
                                    LOG.debug("Package {} already installed", pkgName);
                                }
                            }
                        }
                        if (!isInstalled && loader != null) {
                            final XarSource depFile = loader.load(pkgName, version);
                            if (depFile != null) {
                                installAndDeploy(broker, transaction, depFile, loader);
                            } else {
                                if (enforceDeps) {
                                    LOG.warn("Missing dependency: package {} could not be resolved. This error is not fatal, but the package may not work as expected", pkgName);
                                } else {
                                    throw new PackageException("Missing dependency: package " + pkgName + " could not be resolved.");
                                }
                            }
                        }
                    }
                }
            } catch (final XPathException e) {
                throw new PackageException("Invalid descriptor found in " + xar.getURI());
            }

            // installing the xar into the expath repo
            LOG.info("Installing package {}", xar.getURI());
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

            LOG.info("Deploying package {}", pkgName);
            return deploy(broker, transaction, pkgName, repo, null);
        }

	    // Totally unnecessary to do the above if repo is unavailable.
	    return Optional.empty();
    }

    private void checkProcessorVersion(final PackageLoader.Version version) throws PackageException {
        final String procVersion = SystemProperties.getInstance().getSystemProperty("product-version", "1.0");

        final DependencyVersion depVersion = version.getDependencyVersion();
        if (!depVersion.isCompatible(procVersion)) {
            throw new PackageException("Package requires eXist-db version " + version + ". " +
                "Installed version is " + procVersion);
        }
    }

    public Optional<String> undeploy(final DBBroker broker, final Txn transaction, final String pkgName, final Optional<ExistRepository> repo) throws PackageException {
        final Optional<Path> maybePackageDir = getPackageDir(pkgName, repo);
        if (!maybePackageDir.isPresent()) {
            // fails silently if package dir is not found?
            return Optional.empty();
        }

        final Path packageDir = maybePackageDir.get();
        final Optional<Package> pkg = getPackage(pkgName, repo);
        final DocumentImpl repoXML;
        try {
            repoXML = getRepoXML(broker, packageDir);
        } catch (PackageException e) {
            if (pkg.isPresent()) {
                uninstall(broker, transaction, pkg.get(), Optional.empty());
            }
            throw new PackageException("Failed to remove package from database " +
                    "due to error in repo.xml: " + e.getMessage(), e);
        }
        if (repoXML != null) {
            try {
                final Optional<ElementImpl> cleanup = findElement(repoXML, CLEANUP_ELEMENT);
                if(cleanup.isPresent()) {
                    runQuery(broker, null, packageDir, cleanup.get().getStringValue(), pkgName, QueryPurpose.UNDEPLOY);
                }

                final Optional<ElementImpl> target = findElement(repoXML, TARGET_COLL_ELEMENT);
                if (pkg.isPresent()) {
                    uninstall(broker, transaction, pkg.get(), target);
                }

                return target.map(e -> Optional.ofNullable(e.getStringValue())).orElseGet(() -> Optional.of(getTargetFallback(pkg.get()).getCollectionPath()));
            } catch (final XPathException | IOException e) {
                throw new PackageException("Error found while processing repo.xml: " + e.getMessage(), e);
            }
        } else {
            // we still may need to remove the copy of the package from /db/system/repo
            if (pkg.isPresent()) {
		        uninstall(broker, transaction, pkg.get(), Optional.empty());
	        }
        }
        return Optional.empty();
    }

    public Optional<String> deploy(final DBBroker broker, final Txn transaction, final String pkgName, final Optional<ExistRepository> repo, final String userTarget) throws PackageException, IOException {
        final Optional<Path> maybePackageDir = getPackageDir(pkgName, repo);
        if (!maybePackageDir.isPresent()) {
            throw new PackageException("Package not found: " + pkgName);
        }

        final Path packageDir = maybePackageDir.get();

        final DocumentImpl repoXML = getRepoXML(broker, packageDir);
        if (repoXML == null) {
            return Optional.empty();
        }
        try {
            // if there's a <setup> element, run the query it points to
            final Optional<ElementImpl> setup = findElement(repoXML, SETUP_ELEMENT);
            final Optional<String> setupPath = setup.map(ElementImpl::getStringValue).filter(s -> !s.isEmpty());

            if (setupPath.isPresent()) {
                runQuery(broker, null, packageDir, setupPath.get(), pkgName, QueryPurpose.SETUP);
                return Optional.empty();
            } else {
                // otherwise create the target collection
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
                            targetCollection = XmldbURI.create(getTargetCollection(broker, targetPath.get()));
                        } catch (final IllegalArgumentException e) {
                            throw new PackageException("Bad collection URI for <target> element: " + targetPath.get(), e);
                        }
                    } else {
                        LOG.warn("EXPath Package '{}' does not contain a <target> in its repo.xml, no files will be deployed to /apps", pkgName);
                    }
                }
                if (targetCollection == null) {
                    // no target means: package does not need to be deployed into database
                    // however, we need to preserve a copy for backup purposes
                    final Optional<Package> pkg = getPackage(pkgName, repo);
		            pkg.orElseThrow(() -> new XPathException((Expression) null, "expath repository is not available so the package was not stored."));
                    final String pkgColl = pkg.get().getAbbrev() + "-" + pkg.get().getVersion();
                    targetCollection = XmldbURI.SYSTEM.append("repo/" + pkgColl);
                }

                // extract the permissions (if any)
                final Optional<ElementImpl> permissions = findElement(repoXML, PERMISSIONS_ELEMENT);
                final Optional<RequestedPerms> requestedPerms = permissions.flatMap(elem -> {
                    final Optional<Either<Integer, String>> perms = Optional.of(elem.getAttribute("mode")).flatMap(mode -> {
                        try {
                            return Optional.of(Either.Left(Integer.parseInt(mode, 8)));
                        } catch(final NumberFormatException e) {
                            if (mode.matches("^[rwx-]{9}")) {
                                return Optional.of(Either.Right(mode));
                            } else {
                                return Optional.empty();
                            }
                        }
                    });

                    return perms.map(p -> new RequestedPerms(
                        elem.getAttribute("user"),
                        elem.getAttribute("password"),
                        Optional.of(elem.getAttribute("group")),
                        p
                    ));
                });

                //check that if there were permissions then we were able to parse them, a failure would be related to the mode string
                if (permissions.isPresent() && requestedPerms.isEmpty()) {
                    final String mode = permissions.map(elem -> elem.getAttribute("mode")).orElse(null);
                    throw new PackageException("Bad format for mode attribute in <permissions>: " + mode);
                }

                // run the pre-setup query if present
                final Optional<ElementImpl> preSetup = findElement(repoXML, PRE_SETUP_ELEMENT);
                final Optional<String> preSetupPath = preSetup.map(ElementImpl::getStringValue).filter(s -> !s.isEmpty());

                if(preSetupPath.isPresent()) {
                    runQuery(broker, targetCollection, packageDir, preSetupPath.get(), pkgName, QueryPurpose.PREINSTALL);
                }

                // create the group specified in the permissions element if needed
                // create the user specified in the permissions element if needed; assign it to the specified group
                // TODO: if the user already exists, check and ensure the user is assigned to the specified group
                if(requestedPerms.isPresent()) {
                    checkUserSettings(broker, requestedPerms.get());
                }

                final InMemoryNodeSet resources = findElements(repoXML,RESOURCES_ELEMENT);

                // store all package contents into database, using the user/group/mode in the permissions element. however:
                // 1. repo.xml is excluded for now, since it may contain the default user's password in the clear
                // 2. contents of directories identified in the path attribute of any <resource path=""/> element are stored as binary
                final List<String> errors = scanDirectory(broker, transaction, packageDir, targetCollection, resources, true, false,
                        requestedPerms);
                
                // store repo.xml, filtering out the default user's password
                storeRepoXML(broker, transaction, repoXML, targetCollection, requestedPerms);

                // run the post-setup query if present
                final Optional<ElementImpl> postSetup = findElement(repoXML, POST_SETUP_ELEMENT);
                final Optional<String> postSetupPath = postSetup.map(ElementImpl::getStringValue).filter(s -> !s.isEmpty());

                if(postSetupPath.isPresent()) {
                    runQuery(broker, targetCollection, packageDir, postSetupPath.get(), pkgName, QueryPurpose.POSTINSTALL);
                }

                // TODO: it should be safe to clean up the file system after a package
                // has been deployed. Might be enabled after 2.0
                //cleanup(pkgName, repo);

                if (!errors.isEmpty()) {
                    throw new PackageException("Deployment incomplete, " + errors.size() + " issues found: " +
                            String.join("; ", errors));
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
            if (maybePackageDir.isEmpty()) {
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
                        return !(name.equals(abbrev) || "content".equals(name));
                    } else {
                        return !("expath-pkg.xml".equals(name) || "repo.xml".equals(name) ||
                                "exist.xml".equals(name) || name.startsWith("icon"));
                    }
            })) {

                filesToDelete.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch(final IOException ioe) {
                        LOG.warn("Cleanup: failed to delete file {} in package {}", path.toAbsolutePath().toString(), pkgName);
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
     * @param broker
     * @param pkg
     * @param pkgDir
     * @return
     * @throws PackageException
     */
    private String getTargetCollection(final DBBroker broker, final Package pkg, final Path pkgDir) throws PackageException {
        final DocumentImpl repoXML = getRepoXML(broker, pkgDir);
        if (repoXML != null) {
            try {
                final Optional<ElementImpl> target = findElement(repoXML, TARGET_COLL_ELEMENT);
                return target.map(ElementImpl::getStringValue).map(s -> getTargetCollection(broker, s)).map(XmldbURI::create).map(XmldbURI::getCollectionPath)
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

    private String getTargetCollection(final DBBroker broker, String targetFromRepo) {
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
    private void uninstall(final DBBroker broker, final Txn transaction, final Package pkg, final Optional<ElementImpl> target)
            throws PackageException {
        // determine target collection
        final Optional<String> targetPath = target.map(ElementImpl::getStringValue).filter(s -> !s.isEmpty());
        final XmldbURI targetCollection = targetPath.map(s -> XmldbURI.create(getTargetCollection(broker, s)))
                .orElseGet(() -> getTargetFallback(pkg));

        try {
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
        } catch (final PermissionDeniedException | IOException | TriggerException e) {
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
    private void storeRepoXML(final DBBroker broker, final Txn transaction, final DocumentImpl repoXML, final XmldbURI targetCollection, final Optional<RequestedPerms> requestedPerms)
            throws PackageException, XPathException {
        // Store repo.xml
        final DateTimeValue time = new DateTimeValue(new Date());
        final MemTreeBuilder builder = new MemTreeBuilder((Expression) null);
        builder.startDocument();
        final UpdatingDocumentReceiver receiver = new UpdatingDocumentReceiver(null, builder, time.getStringValue());
        try {
            repoXML.copyTo(broker, receiver);
        } catch (final SAXException e) {
            throw new PackageException("Error while updating repo.xml in-memory: " + e.getMessage(), e);
        }
        builder.endDocument();
        final DocumentImpl updatedXML = builder.getDocument();

        try {
            final Collection collection = broker.getOrCreateCollection(transaction, targetCollection);
            final XmldbURI name = XmldbURI.createInternal("repo.xml");

            final Permission permission = PermissionFactory.getDefaultResourcePermission(broker.getBrokerPool().getSecurityManager());
            setPermissions(broker, requestedPerms, false, MimeType.XML_TYPE, permission);

            collection.storeDocument(transaction, broker, name, updatedXML, MimeType.XML_TYPE, null, null, permission, null, null);

        } catch (final PermissionDeniedException | IOException | SAXException | LockException | EXistException e) {
            throw new PackageException("Error while storing updated repo.xml: " + e.getMessage(), e);
        }
    }

    private void checkUserSettings(final DBBroker broker, final RequestedPerms requestedPerms) throws PackageException {
        final org.exist.security.SecurityManager secman = broker.getBrokerPool().getSecurityManager();
        try {
            if (requestedPerms.group.filter(g -> !secman.hasGroup(g)).isPresent()) {
                secman.addGroup(broker, new GroupAider(requestedPerms.group.get()));
            }

            if (!secman.hasAccount(requestedPerms.user)) {
                final UserAider aider = new UserAider(requestedPerms.user);
                aider.setPassword(requestedPerms.password);
                requestedPerms.group.ifPresent(aider::addGroup);
                secman.addAccount(broker, aider);
            }
        } catch (final PermissionDeniedException | EXistException e) {
            throw new PackageException("Failed to create user: " + requestedPerms.user, e);
        }
    }

    private enum QueryPurpose {
        SETUP("<setup> element"),
        PREINSTALL("<prepare> element"),
        POSTINSTALL("<finish> element"),
        UNDEPLOY("undeploy");

        private final String purpose;

        QueryPurpose(final String purpose) {
            this.purpose = purpose;
        }

        public String getPurposeString() {
            return purpose;
        }
    }

    private Sequence runQuery(final DBBroker broker, final XmldbURI targetCollection, final Path tempDir,
            final String fileName, final String pkgName, final QueryPurpose purpose)
            throws PackageException, IOException, XPathException {
        final Path xquery = tempDir.resolve(fileName);
        if (!Files.isReadable(xquery)) {
            LOG.warn("The XQuery resource specified in the {} was not found for EXPath Package: '{}'", purpose.getPurposeString(), pkgName);
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
        if (QueryPurpose.PREINSTALL == purpose) {
            // when running pre-setup scripts, base path should point to directory
            // because the target collection does not yet exist
            ctx.setModuleLoadPath(tempDir.toAbsolutePath().toString());
        }

        CompiledXQuery compiled;
        try {
            compiled = xqs.compile(ctx, new FileSource(xquery, false));
            return xqs.execute(broker, compiled, null);
        } catch (final PermissionDeniedException e) {
            throw new PackageException(e.getMessage(), e);
        } finally {
            ctx.runCleanupTasks();
        }
    }

    /**
     * Scan a directory and import all files and sub directories into the target
     * collection.
     *
     * @param broker
     * @param transaction
     * @param directory
     * @param target
     */
    private List<String> scanDirectory(final DBBroker broker, final Txn transaction, final Path directory, final XmldbURI target, final InMemoryNodeSet resources,
                               final boolean inRootDir, final boolean isResourcesDir, final Optional<RequestedPerms> requestedPerms) {
        return scanDirectory(broker, transaction, directory, target, resources, inRootDir, isResourcesDir, requestedPerms, new ArrayList<>());
    }

    private List<String> scanDirectory(final DBBroker broker, final Txn transaction, final Path directory, final XmldbURI target, final InMemoryNodeSet resources,
                                       final boolean inRootDir, final boolean isResourcesDir, final
                                       Optional<RequestedPerms> requestedPerms, final List<String> errors) {
        Collection collection = null;
        try {
            collection = broker.getOrCreateCollection(transaction, target);
            setPermissions(broker, requestedPerms, true, null, collection.getPermissionsNoLock());
            broker.saveCollection(transaction, collection);
        } catch (final PermissionDeniedException | TriggerException | IOException e) {
            LOG.warn(e);
            errors.add(e.getMessage());
        }

        final boolean isResources = isResourcesDir || isResourceDir(target, resources);

        // the root dir is not allowed to be a resources directory
        if (!inRootDir && isResources) {
            try {
                storeBinaryResources(broker, transaction, directory, collection, requestedPerms, errors);
            } catch (Exception e) {
                LOG.error(e.getMessage(), e); 
            }
        } else {
            storeFiles(broker, transaction, directory, collection, inRootDir, requestedPerms, errors);
        }

        // scan sub directories
        try(final Stream<Path> subDirs = Files.find(directory, 1, (path, attrs) -> (!path.equals(directory)) && attrs.isDirectory())) {
            subDirs.forEach(path -> scanDirectory(broker, transaction, path, target.append(FileUtils.fileName(path)), resources, false,
                    isResources, requestedPerms, errors));
        } catch(final IOException ioe) {
            LOG.warn("Unable to scan sub-directories", ioe);
        }
        return errors;
    }

    private boolean isResourceDir(final XmldbURI target, final InMemoryNodeSet resources) {
        // iterate here or pass into scandirectory directly or even save as class property???
        for (final SequenceIterator i = resources.iterate(); i.hasNext(); ) {
            final ElementImpl child = (ElementImpl) i.nextItem();
            final String resourcePath = child.getAttribute(RESOURCES_PATH_ATTRIBUTE);
            if (target.toString().endsWith(resourcePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Import all files in the given directory into the target collection
     *
     * @param broker
     * @param transaction
     * @param directory
     * @param targetCollection
     */
    private void storeFiles(final DBBroker broker, final Txn transaction, final Path directory, final Collection targetCollection, final boolean inRootDir,
            final Optional<RequestedPerms> requestedPerms, final List<String> errors) {
        List<Path> files;
        try {
            files = FileUtils.list(directory);
        } catch(final IOException ioe) {
            LOG.error(ioe);
            errors.add(FileUtils.fileName(directory) + ": " + ioe.getMessage());
            files = Collections.EMPTY_LIST;
        }

        final MimeTable mimeTab = MimeTable.getInstance();

        for (final Path file : files) {
            if (inRootDir && "repo.xml".equals(FileUtils.fileName(file))) {
                continue;
            }
            if (!Files.isDirectory(file)) {
                MimeType mime = mimeTab.getContentTypeFor(FileUtils.fileName(file));
                if (mime == null) {
                    mime = MimeType.BINARY_TYPE;
                }
                final XmldbURI name = XmldbURI.create(FileUtils.fileName(file));

                try {
                    final Permission permission = PermissionFactory.getDefaultResourcePermission(broker.getBrokerPool().getSecurityManager());
                    setPermissions(broker, requestedPerms, false, mime, permission);

                    try (final FileInputSource is = new FileInputSource(file)) {

                        broker.storeDocument(transaction, name, is, mime, null, null, permission, null, null, targetCollection);

                    } catch (final EXistException | PermissionDeniedException | LockException | SAXException | IOException e) {
                        //check for .html ending
                        if (mime.getName().equals(MimeType.HTML_TYPE.getName())) {
                            //store it as binary resource
                            storeBinary(broker, transaction, targetCollection, file, mime, name, permission);
                        } else {
                            // could neither store as xml nor binary: give up and report failure in outer catch
                            throw new EXistException(FileUtils.fileName(file) + " cannot be stored");
                        }
                    }
                } catch (final SAXException | EXistException | PermissionDeniedException | LockException | IOException e) {
                    LOG.error(e.getMessage(), e);
                    errors.add(FileUtils.fileName(file) + ": " + e.getMessage());
                }
            }
        }
    }

    private void storeBinary(final DBBroker broker, final Txn transaction, final Collection targetCollection, final Path file, final MimeType mime, final XmldbURI name, @Nullable final Permission permission) throws
            IOException, EXistException, PermissionDeniedException, LockException, SAXException {

        final InputSource is = new FileInputSource(file);
        broker.storeDocument(transaction, name, is, new MimeType(mime.getName(), MimeType.BINARY), null, null, permission, null, null, targetCollection);
    }

    private void storeBinaryResources(final DBBroker broker, final Txn transaction, final Path directory, final Collection targetCollection,
            final Optional<RequestedPerms> requestedPerms, final List<String> errors) throws IOException, EXistException,
            PermissionDeniedException, LockException, TriggerException {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (final Path entry: stream) {
                if (!Files.isDirectory(entry)) {
                    final XmldbURI name = XmldbURI.create(FileUtils.fileName(entry));
                    try {
                        final Permission permission = PermissionFactory.getDefaultResourcePermission(broker.getBrokerPool().getSecurityManager());
                        setPermissions(broker, requestedPerms, false, MimeType.BINARY_TYPE, permission);

                        storeBinary(broker, transaction, targetCollection, entry, MimeType.BINARY_TYPE, name, permission);
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
    private void setPermissions(final DBBroker broker, final Optional<RequestedPerms> requestedPerms, final boolean isCollection, final MimeType mime, final Permission permission) throws PermissionDeniedException {
        int mode = permission.getMode();
        if (requestedPerms.isPresent()) {
            final RequestedPerms perms = requestedPerms.get();

            PermissionFactory.chown(broker, permission, Optional.of(perms.user), perms.group);

            mode = perms.permissions.map(permStr -> {
                try {
                    final UnixStylePermission other = new UnixStylePermission(broker.getBrokerPool().getSecurityManager());
                    other.setMode(permStr);
                    return other.getMode();
                } catch (final PermissionDeniedException | SyntaxException e) {
                    LOG.warn("Unable to set permissions string: {}. Falling back to default.", permStr);
                    return permission.getMode();
                }
            }).fold(l -> l, r -> r);
        }

        if (isCollection || (mime != null && mime.getName().equals(MimeType.XQUERY_TYPE.getName()))) {
            mode = AbstractUnixStylePermission.safeSetExecutable(mode);
        }

        PermissionFactory.chmod(broker, permission, Optional.of(mode), Optional.empty());
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

    public Optional<String> getNameFromDescriptor(final DBBroker broker, final XarSource xar) throws IOException, PackageException {
        final Optional<DocumentImpl> doc = getDescriptor(broker, xar);
        return doc.map(DocumentImpl::getDocumentElement).map(root -> root.getAttribute("name"));
    }

    public Optional<DocumentImpl> getDescriptor(final DBBroker broker, final XarSource xar) throws IOException, PackageException {
        try(final JarInputStream jis = new JarInputStream(xar.newInputStream())) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (!entry.isDirectory() && "expath-pkg.xml".equals(entry.getName())) {
                    try {
                        return Optional.of(DocUtils.parse(broker.getBrokerPool(), null, jis, null));
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
            this(null, builder, time);
        }

        public UpdatingDocumentReceiver(final Expression expression, final MemTreeBuilder builder, final String time) {
            super(expression, builder, false);
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
