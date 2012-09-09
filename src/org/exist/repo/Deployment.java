package org.exist.repo;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.config.ConfigurationException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.QName;
import org.exist.memtree.*;
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
import org.exist.xquery.value.Type;
import org.expath.pkg.repo.FileSystemStorage;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Packages;
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

    private final static Logger LOG = Logger.getLogger(Deployment.class);

    private final static String REPO_NAMESPACE = "http://exist-db.org/xquery/repo";

    private final static QName SETUP_ELEMENT = new QName("setup", REPO_NAMESPACE);
    private static final QName PRE_SETUP_ELEMENT = new QName("prepare", REPO_NAMESPACE);
    private static final QName POST_SETUP_ELEMENT = new QName("finish", REPO_NAMESPACE);
    private static final QName TARGET_COLL_ELEMENT = new QName("target", REPO_NAMESPACE);
    private static final QName PERMISSIONS_ELEMENT = new QName("permissions", REPO_NAMESPACE);
    private static final QName CLEANUP_ELEMENT = new QName("cleanup", REPO_NAMESPACE);
    private static final QName DEPLOYED_ELEMENT = new QName("deployed", REPO_NAMESPACE);

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

        for (Packages pp : repo.getParentRepo().listPackages()) {
            org.expath.pkg.repo.Package pkg = pp.latest();
            if (pkg.getName().equals(pkgName)) {
                FileSystemStorage.FileSystemResolver resolver = (FileSystemStorage.FileSystemResolver) pkg.getResolver();
                packageDir = resolver.resolveResourceAsFile(".");
            }
        }
        return packageDir;
    }

    protected DocumentImpl getRepoXML(File packageDir) throws PackageException {
        // find and parse the repo.xml descriptor
        File repoFile = new File(packageDir, "repo.xml");
        if (!repoFile.canRead())
            return null;
        try {
            return DocUtils.parse(broker.getBrokerPool(), null, new FileInputStream(repoFile));
        } catch (XPathException e) {
            throw new PackageException("Failed to parse repo.xml: " + e.getMessage(), e);
        } catch (FileNotFoundException e) {
            throw new PackageException("Failed to read repo.xml: " + e.getMessage(), e);
        }
    }

    public String undeploy(String pkgName, ExistRepository repo) throws PackageException {
        File packageDir = getPackageDir(pkgName, repo);
        if (packageDir == null)
            // fails silently if package dir is not found?
            return null;
        DocumentImpl repoXML = getRepoXML(packageDir);
        ElementImpl target = null;
        try {
            target = findElement(repoXML, TARGET_COLL_ELEMENT);
            ElementImpl cleanup = findElement(repoXML, CLEANUP_ELEMENT);
            if (cleanup != null) {
                runQuery(null, packageDir, cleanup.getStringValue(), false);
            }
            if (target != null) {
                uninstall(target);
            }
            return target.getStringValue();
        } catch (XPathException e) {
            throw new PackageException("Error found while processing repo.xml: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new PackageException("Error found while processing repo.xml: " + e.getMessage(), e);
        }
    }

    public String deploy(String pkgName, ExistRepository repo, String userTarget) throws PackageException, IOException {
        File packageDir = getPackageDir(pkgName, repo);
        if (packageDir == null)
            throw new PackageException("Package not found: " + pkgName);
        DocumentImpl repoXML = getRepoXML(packageDir);
        try {
            // if there's a <setup> element, run the query it points to
            ElementImpl setup = findElement(repoXML, SETUP_ELEMENT);
            String path = setup == null ? null : setup.getStringValue();
            if (path != null && path.length() > 0) {
                runQuery(null, packageDir, path, true);
                return null;
            } else {
                // otherwise copy all child directories to the target collection
                XmldbURI targetCollection = XmldbURI.ROOT_COLLECTION_URI;
                if (userTarget != null) {
                    try {
                        targetCollection = XmldbURI.create(userTarget);
                    } catch (Exception e) {
                        throw new PackageException("Bad collection URI: " + userTarget, e);
                    }
                } else {
                    ElementImpl target = findElement(repoXML, TARGET_COLL_ELEMENT);
                    if (target != null) {
                        // determine target collection
                        try {
                            targetCollection = XmldbURI.create(target.getStringValue());
                        } catch (Exception e) {
                            throw new PackageException("Bad collection URI for <target> element: " + target.getStringValue(), e);
                        }
                    }
                }

                ElementImpl permissions = findElement(repoXML, PERMISSIONS_ELEMENT);
                if (permissions != null) {
                    // get user, group and default permissions
                    user = permissions.getAttribute("user");
                    group = permissions.getAttribute("group");
                    password = permissions.getAttribute("password");
                    String mode = permissions.getAttribute("mode");
                    try {
                        perms = Integer.parseInt(mode, 8);
                    } catch (NumberFormatException e) {
                        permsStr = mode;
                        if (!permsStr.matches("^[rwx-]{9}"))
                            throw new PackageException("Bad format for mode attribute in <permissions>: " + mode);
                    }
                }

                // run the pre-setup query if present
                ElementImpl preSetup = findElement(repoXML, PRE_SETUP_ELEMENT);
                if (preSetup != null) {
                    path = preSetup.getStringValue();
                    if (path.length() > 0)
                        runQuery(targetCollection, packageDir, path, true);
                }

                // any required users and group should have been created by the pre-setup query.
                // check for invalid users now.
                checkUserSettings();

                // install
                scanDirectory(packageDir, targetCollection);

                // run the post-setup query if present
                ElementImpl postSetup = findElement(repoXML, POST_SETUP_ELEMENT);
                if (postSetup != null) {
                    path = postSetup.getStringValue();
                    if (path.length() > 0)
                        runQuery(targetCollection, packageDir, path, false);
                }

                storeRepoXML(repoXML, targetCollection);

                return targetCollection.getCollectionPath();
            }
        } catch (XPathException e) {
            throw new PackageException("Error found while processing repo.xml: " + e.getMessage(), e);
        }
    }

    private void uninstall(ElementImpl target)
            throws PackageException {
        // determine target collection
        XmldbURI targetCollection;
        try {
            targetCollection = XmldbURI.create(target.getStringValue());
        } catch (Exception e) {
            throw new PackageException("Bad collection URI for <target> element: " + target.getStringValue());
        }
        TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        Txn txn = mgr.beginTransaction();
        try {
            Collection collection = broker.getOrCreateCollection(txn, targetCollection);
            if (collection != null)
                broker.removeCollection(txn, collection);

            XmldbURI configCollection = XmldbURI.CONFIG_COLLECTION_URI.append(targetCollection);
            collection = broker.getOrCreateCollection(txn, configCollection);
            if (collection != null)
                broker.removeCollection(txn, collection);
            mgr.commit(txn);
        } catch (Exception e) {
            mgr.abort(txn);
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
        DateTimeValue time = new DateTimeValue(new Date());
        MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        UpdatingDocumentReceiver receiver = new UpdatingDocumentReceiver(builder, time.getStringValue());
        try {
            repoXML.copyTo(broker, receiver);
        } catch (SAXException e) {
            throw new PackageException("Error while updating repo.xml: " + e.getMessage());
        }
        builder.endDocument();
        DocumentImpl updatedXML = builder.getDocument();

        TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        Txn txn = mgr.beginTransaction();
        try {
            Collection collection = broker.getOrCreateCollection(txn, targetCollection);
            XmldbURI name = XmldbURI.createInternal("repo.xml");
            IndexInfo info = collection.validateXMLResource(txn, broker, name, updatedXML);
            Permission permission = info.getDocument().getPermissions();
            setPermissions(false, MimeType.XML_TYPE, permission);

            collection.store(txn, broker, info, updatedXML, false);

            mgr.commit(txn);
        } catch (Exception e) {
            mgr.abort(txn);
        }
    }

    private void checkUserSettings() throws PackageException {
        org.exist.security.SecurityManager secman = broker.getBrokerPool().getSecurityManager();
        try {
            if (group != null && !secman.hasGroup(group)) {
                GroupAider aider = new GroupAider(group);
                secman.addGroup(aider);
            }
            if (user != null && !secman.hasAccount(user)) {
                UserAider aider = new UserAider(user);
                aider.setPassword(password);
                if (group != null)
                    aider.addGroup(group);

                secman.addAccount(aider);
            }
        } catch (ConfigurationException e) {
            throw new PackageException("Failed to create user: " + user, e);
        } catch (PermissionDeniedException e) {
            throw new PackageException("Failed to create user: " + user, e);
        } catch (EXistException e) {
            throw new PackageException("Failed to create user: " + user, e);
        }
    }

    private Sequence runQuery(XmldbURI targetCollection, File tempDir, String fileName, boolean preInstall)
            throws PackageException, IOException, XPathException {
        File xquery = new File(tempDir, fileName);
        if (!xquery.canRead())
            throw new PackageException("The XQuery resource specified in the <setup> element was not found");
        XQuery xqs = broker.getXQueryService();
        XQueryContext ctx = xqs.newContext(AccessContext.REST);
        ctx.declareVariable("dir", tempDir.getAbsolutePath());
        File home = broker.getConfiguration().getExistHome();
        ctx.declareVariable("home", home.getAbsolutePath());
        if (targetCollection != null) {
            ctx.declareVariable("target", targetCollection.toString());
            ctx.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI + targetCollection.toString());
        } else
            ctx.declareVariable("target", Sequence.EMPTY_SEQUENCE);
        if (preInstall)
            // when running pre-setup scripts, base path should point to directory
            // because the target collection does not yet exist
            ctx.setModuleLoadPath(tempDir.getAbsolutePath());

        CompiledXQuery compiled;
        try {
            compiled = xqs.compile(ctx, new FileSource(xquery, "UTF-8", false));
            return xqs.execute(compiled, null);
        } catch (PermissionDeniedException e) {
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
    private void scanDirectory(File directory, XmldbURI target) {
        TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        Txn txn = mgr.beginTransaction();
        Collection collection = null;
        try {
            collection = broker.getOrCreateCollection(txn, target);
            setPermissions(true, null, collection.getPermissions());
            broker.saveCollection(txn, collection);
            mgr.commit(txn);
        } catch (Exception e) {
            mgr.abort(txn);
        }

        try {
            // lock the collection while we store the files
            // TODO: could be released after each operation
            collection.getLock().acquire(Lock.WRITE_LOCK);
            storeFiles(directory, collection);
        } catch (LockException e) {
            e.printStackTrace();
        } finally {
            collection.getLock().release(Lock.WRITE_LOCK);
        }

        // scan sub directories
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, target.append(file.getName()));
            }
        }
    }

    /**
     * Import all files in the given directory into the target collection
     *
     * @param directory
     * @param targetCollection
     */
    private void storeFiles(File directory, Collection targetCollection) {
        File[] files = directory.listFiles();
        MimeTable mimeTab = MimeTable.getInstance();
        TransactionManager mgr = broker.getBrokerPool().getTransactionManager();
        for (File file : files) {
            if (!file.isDirectory() && !file.getName().equals("repo.xml")) {
                MimeType mime = mimeTab.getContentTypeFor(file.getName());
                if (mime == null)
                    mime = MimeType.BINARY_TYPE;
                XmldbURI name = XmldbURI.create(file.getName());

                Txn txn = mgr.beginTransaction();
                try {
                    if (mime.isXMLType()) {
                        InputSource is = new InputSource(file.toURI().toASCIIString());
                        IndexInfo info = targetCollection.validateXMLResource(txn, broker, name, is);
                        info.getDocument().getMetadata().setMimeType(mime.getName());
                        Permission permission = info.getDocument().getPermissions();
                        setPermissions(false, mime, permission);

                        targetCollection.store(txn, broker, info, is, false);
                    } else {
                        long size = file.length();
                        FileInputStream is = new FileInputStream(file);
                        BinaryDocument doc =
                                targetCollection.addBinaryResource(txn, broker, name, is, mime.getName(), size);
                        is.close();

                        Permission permission = doc.getPermissions();
                        setPermissions(false, mime, permission);
                        doc.getMetadata().setMimeType(mime.getName());
                        broker.storeXMLResource(txn, doc);
                    }
                    mgr.commit(txn);
                } catch (Exception e) {
                    mgr.abort(txn);
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
            } catch (SyntaxException e) {
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
        InMemoryNodeSet setupNodes = new InMemoryNodeSet();
        root.selectDescendants(false, new NameTest(Type.ELEMENT, qname), setupNodes);
        if (setupNodes.getItemCount() == 0)
            return null;
        return (ElementImpl) setupNodes.itemAt(0);
    }

    public String getNameFromDescriptor(File xar) throws IOException, PackageException {
        DocumentImpl doc = getDescriptor(xar);
        Element root = doc.getDocumentElement();
        return root.getAttribute("name");
    }

    public DocumentImpl getDescriptor(File jar) throws IOException, PackageException {
        InputStream istream = new BufferedInputStream(new FileInputStream(jar));
        JarInputStream jis = new JarInputStream(istream);
        JarEntry entry;
        DocumentImpl doc = null;
        while ((entry = jis.getNextJarEntry()) != null) {
            if (!entry.isDirectory() && entry.getName().equals("expath-pkg.xml")) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int c;
                byte[] b = new byte[4096];
                while ((c = jis.read(b)) > 0) {
                    bos.write(b, 0, c);
                }

                bos.close();

                byte[] data = bos.toByteArray();

                ByteArrayInputStream bis = new ByteArrayInputStream(data);
                try {
                    doc = DocUtils.parse(broker.getBrokerPool(), null, bis);
                } catch (XPathException e) {
                    throw new PackageException("Error while parsing expath-pkg.xml: " + e.getMessage(), e);
                }
                break;
            }
        }
        jis.close();
        return doc;
    }

    /**
     * Update repo.xml while copying it.
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
            stack.push(qname.getLocalName());
            if (!"deployed".equals(qname.getLocalName()))
                super.startElement(qname, attribs);
        }

        @Override
        public void startElement(String namespaceURI, String localName,
                                 String qName, Attributes attrs) throws SAXException {
            stack.push(localName);
            if (!"deployed".equals(localName))
                super.startElement(namespaceURI, localName, qName, attrs);
        }

        @Override
        public void endElement(QName qname) throws SAXException {
            stack.pop();
            if ("meta".equals(qname.getLocalName())) {
                addDeployTime();
            }
            if (!"deployed".equals(qname.getLocalName()))
                super.endElement(qname);
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            stack.pop();
            if ("meta".equals(localName)) {
                addDeployTime();
            }
            if (!"deployed".equals(localName))
                super.endElement(uri, localName, qName);
        }

        @Override
        public void characters(char[] ch, int start, int len)
                throws SAXException {
            String current = stack.peek();
            if (!current.equals("deployed"))
                super.characters(ch, start, len);
        }

        @Override
        public void characters(CharSequence seq) throws SAXException {
            String current = stack.peek();
            if (!current.equals("deployed"))
                super.characters(seq);
        }

        private void addDeployTime() throws SAXException {
            super.startElement(DEPLOYED_ELEMENT, null);
            super.characters(time);
            super.endElement(DEPLOYED_ELEMENT);
        }
    }
}
