package org.exist.xquery.modules.expathrepo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.config.ConfigurationException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.ElementImpl;
import org.exist.memtree.InMemoryNodeSet;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.repo.ExistRepository;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.UUIDGenerator;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.security.xacml.AccessContext;
import org.exist.source.FileSource;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.NameTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.expath.pkg.repo.FileSystemStorage.FileSystemResolver;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.Packages;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.AttributesImpl;

public class Deploy extends BasicFunction {

	protected static final Logger logger = Logger.getLogger(Deploy.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("deploy", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
			"Deploy an application package. Installs package contents to the specified target collection, using the permissions " +
			"defined by the &lt;permissions&gt; element in repo.xml. Pre- and post-install XQuery scripts can be specified " +
			"via the &lt;prepare&gt; and &lt;finish&gt; elements.",
			new SequenceType[] { new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name")},
			new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, 
					"<status result=\"ok\"/> if deployment was ok. Throws an error otherwise."));
	
	private final static QName SETUP_ELEMENT = new QName("setup", ExpathPackageModule.NAMESPACE_URI);
	private static final QName PRE_SETUP_ELEMENT = new QName("prepare", ExpathPackageModule.NAMESPACE_URI);
	private static final QName POST_SETUP_ELEMENT = new QName("finish", ExpathPackageModule.NAMESPACE_URI);
	private static final QName TARGET_COLL_ELEMENT = new QName("target", ExpathPackageModule.NAMESPACE_URI);
	private static final QName PERMISSIONS_ELEMENT = new QName("permissions", ExpathPackageModule.NAMESPACE_URI);

	private static final QName STATUS_ELEMENT = new QName("status", ExpathPackageModule.NAMESPACE_URI);

	private String user = null;
	private String password = null;
	private String group = null;
	private int perms = -1;
	
	public Deploy(XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		String pkgName = args[0].getStringValue();
		
		try {
			File packageDir = null;
			
			ExistRepository repo = context.getRepository();
			for (Packages pp : repo.getParentRepo().listPackages()) {
				Package pkg = pp.latest();
				if (pkg.getName().equals(pkgName)) {
					FileSystemResolver resolver = (FileSystemResolver) pkg.getResolver();
					packageDir = resolver.resolveResourceAsFile(".");
				}
			}
            if (packageDir == null)
            	throw new XPathException(this, "Package " + pkgName + " not found");
			
			// find and parse the repo.xml descriptor
			File repoFile = new File(packageDir, "repo.xml");
			if (!repoFile.canRead())
				return Sequence.EMPTY_SEQUENCE;
			DocumentImpl repoXML = DocUtils.parse(context, new FileInputStream(repoFile));
			
			// if there's a <setup> element, run the query it points to
			ElementImpl setup = findElement(repoXML, SETUP_ELEMENT);
			if (setup != null) {
				runQuery(packageDir, setup.getStringValue());
				return statusReport(null);
			} else {
				// otherwise copy all child directories to the target collection
				XmldbURI targetCollection = XmldbURI.ROOT_COLLECTION_URI;
				
				ElementImpl target = findElement(repoXML, TARGET_COLL_ELEMENT);
				if (target != null) {
					// determine target collection
					try {
						targetCollection = XmldbURI.create(target.getStringValue());
					} catch (Exception e) {
						throw new XPathException(this, "Bad collection URI for <target> element: " +
								target.getStringValue());
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
						throw new XPathException(this, "Bad format for mode attribute in <permissions>: " + mode);
					}
				}
				
				// run the pre-setup query if present
				ElementImpl preSetup = findElement(repoXML, PRE_SETUP_ELEMENT);
				if (preSetup != null)
					runQuery(packageDir, preSetup.getStringValue());
				
				// any required users and group should have been created by the pre-setup query.
				// check for invalid users now.
				checkUserSettings();
				
				// install
				scanDirectory(packageDir, targetCollection);
				
				// run the post-setup query if present
				ElementImpl postSetup = findElement(repoXML, POST_SETUP_ELEMENT);
				if (postSetup != null)
					runQuery(packageDir, postSetup.getStringValue());
				
				return statusReport(targetCollection.toString());
			}
		} catch (IOException e) {
			throw new XPathException(this, ErrorCodes.FOER0000, "Caught IO error while deploying expath archive");
		}
	}

	private Sequence statusReport(String target) {
		context.pushDocumentContext();
		try {
			MemTreeBuilder builder = context.getDocumentBuilder();
			AttributesImpl attrs = new AttributesImpl();
			attrs.addAttribute("", "result", "result", "CDATA", "ok");
			if (target != null)
				attrs.addAttribute("", "target", "target", "CDATA", target);
			builder.startElement(STATUS_ELEMENT, attrs);
			builder.endElement();
			
			return builder.getDocument().getNode(1);
		} finally {
			context.popDocumentContext();
		}
		
	}
	
	private void checkUserSettings() throws XPathException {
		SecurityManager secman = context.getBroker().getBrokerPool().getSecurityManager();
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
			throw new XPathException(this, "Failed to create user: " + user, e);
		} catch (PermissionDeniedException e) {
			throw new XPathException(this, "Failed to create user: " + user, e);
		} catch (EXistException e) {
			throw new XPathException(this, "Failed to create user: " + user, e);
		}
	}

	private Sequence runQuery(File tempDir, String fileName)
			throws XPathException, IOException {
		File xquery = new File(tempDir, fileName);
		if (!xquery.canRead())
			throw new XPathException(this, "The XQuery resource specified in the <setup> element was not found");
		LOG.debug("Calling XQuery " + xquery.getAbsolutePath());
		XQuery xqs = context.getBroker().getXQueryService();
		XQueryContext ctx = xqs.newContext(AccessContext.REST);
		ctx.declareVariable("dir", tempDir.getAbsolutePath());
		File home = context.getBroker().getConfiguration().getExistHome();
		ctx.declareVariable("home", home.getAbsolutePath());
		CompiledXQuery compiled = xqs.compile(ctx, new FileSource(xquery, "UTF-8", false));
		Sequence setupResult = xqs.execute(compiled, null);
		return setupResult;
	}
 
	/**
	 * Scan a directory and import all files and sub directories into the target
	 * collection.
	 * 
	 * @param directory
	 * @param target
	 * @param includeFiles
	 */
	private void scanDirectory(File directory, XmldbURI target) {
		TransactionManager mgr = context.getBroker().getBrokerPool().getTransactionManager();
		Txn txn = mgr.beginTransaction();
		Collection collection = null;
		try {
			collection = context.getBroker().getOrCreateCollection(txn, target);
			setPermissions(collection.getPermissions());
			context.getBroker().saveCollection(txn, collection);
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
		TransactionManager mgr = context.getBroker().getBrokerPool().getTransactionManager();
		for (File file : files) {
			if ("repo.xml".equals(file.getName()) || "expath-pkg.xml".equals(file.getName()))
				continue;
			if (!file.isDirectory()) {
				MimeType mime = mimeTab.getContentTypeFor(file.getName());
				XmldbURI name = XmldbURI.create(file.getName());
				
				Txn txn = mgr.beginTransaction();	
				try {
					if (mime.isXMLType()) {
						InputSource is = new InputSource(file.toURI().toASCIIString());
						IndexInfo info = targetCollection.validateXMLResource(txn, context.getBroker(), name, is);
						info.getDocument().getMetadata().setMimeType(mime.getName());
						Permission permission = info.getDocument().getPermissions();
						setPermissions(permission);
						
						targetCollection.store(txn, context.getBroker(), info, is, false);
					} else {
						long size = file.length();
						FileInputStream is = new FileInputStream(file);
						BinaryDocument doc = 
							targetCollection.addBinaryResource(txn, context.getBroker(), name, is, mime.getName(), size);
						is.close();
						
						Permission permission = doc.getPermissions();
						setPermissions(permission);
						doc.getMetadata().setMimeType(mime.getName());
						context.getBroker().storeXMLResource(txn, doc);
					}
					mgr.commit(txn);
				} catch (Exception e) {
					mgr.abort(txn);
					e.printStackTrace();
				}
			}
		}
	}

	private void setPermissions(Permission permission) {
		if (user != null)
			permission.setOwner(user);
		if (group != null)
			permission.setGroup(group);
		if (perms > -1)
			permission.setPermissions(perms);
	}
	
	private ElementImpl findElement(NodeImpl root, QName qname) throws XPathException {
		InMemoryNodeSet setupNodes = new InMemoryNodeSet();
		root.selectDescendants(false, new NameTest(Type.ELEMENT, qname), setupNodes);
		if (setupNodes.getItemCount() == 0)
			return null;
		return (ElementImpl) setupNodes.itemAt(0);
	}
	
	// Unused
	private void unpack(File outputDir, InputStream istream) throws IOException {
		JarInputStream jis = new JarInputStream(istream);
		JarEntry entry;
		while ((entry = jis.getNextJarEntry()) != null) {
			File targetFile = new File(outputDir, entry.getName());
			if (entry.isDirectory()) {
				targetFile.mkdirs();
			} else {
				FileOutputStream os = new FileOutputStream(targetFile);
				
				int c;
				byte[] b = new byte[4096];
				while ((c = jis.read(b)) > 0) {
					os.write(b, 0, c);
				}
				
				os.close();
			}
		}
		jis.close(); 
	}
	
	// Unused
	private File createTempDir() throws XPathException {
		File sysTempDir = new File(System.getProperty("java.io.tmpdir"));
		String uuid = UUIDGenerator.getUUID();
		File tempDir = null;
		final int maxAttempts = 9;
	    int attemptCount = 0;
	    do
	    {
	        attemptCount++;
	        if(attemptCount > maxAttempts)
	        {
	            throw new XPathException(this, "Failed to create a unique temporary directory. Giving up.");
	        }
	        String dirName = UUIDGenerator.getUUID();
	        tempDir = new File(sysTempDir, dirName);
	    } while(tempDir.exists());
	    return tempDir;
	}

	@Override
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		if (!postOptimization) {
			user = null;
			group = null;
			perms = -1;
		}
	}
}
