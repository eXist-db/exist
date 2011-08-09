package org.exist.xquery.modules.expathrepo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.config.ConfigurationException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.QName;
import org.exist.memtree.DocumentBuilderReceiver;
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
import org.exist.util.serializer.AttrList;
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
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.expath.pkg.repo.FileSystemStorage.FileSystemResolver;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.Packages;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class Deploy extends BasicFunction {

	protected static final Logger logger = Logger.getLogger(Deploy.class);
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("deploy", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
			"Deploy an application package. Installs package contents to the specified target collection, using the permissions " +
			"defined by the &lt;permissions&gt; element in repo.xml. Pre- and post-install XQuery scripts can be specified " +
			"via the &lt;prepare&gt; and &lt;finish&gt; elements.",
			new SequenceType[] { new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name")},
			new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, 
					"<status result=\"ok\"/> if deployment was ok. Throws an error otherwise.")),
		new FunctionSignature(
				new QName("deploy", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
				"Deploy an application package. Installs package contents to the specified target collection, using the permissions " +
				"defined by the &lt;permissions&gt; element in repo.xml. Pre- and post-install XQuery scripts can be specified " +
				"via the &lt;prepare&gt; and &lt;finish&gt; elements.",
				new SequenceType[] { 
					new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name"),
					new FunctionParameterSequenceType("targetCollection", Type.STRING, Cardinality.EXACTLY_ONE, "the target " +
							"collection into which the package will be stored")
				},
				new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, 
						"<status result=\"ok\"/> if deployment was ok. Throws an error otherwise.")),
		new FunctionSignature(
				new QName("undeploy", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
				"Deploy an application package. Installs package contents to the specified target collection, using the permissions " +
				"defined by the &lt;permissions&gt; element in repo.xml. Pre- and post-install XQuery scripts can be specified " +
				"via the &lt;prepare&gt; and &lt;finish&gt; elements.",
				new SequenceType[] { new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name")},
				new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, 
						"<status result=\"ok\"/> if deployment was ok. Throws an error otherwise."))
	};
	
	private final static QName SETUP_ELEMENT = new QName("setup", ExpathPackageModule.NAMESPACE_URI);
	private static final QName PRE_SETUP_ELEMENT = new QName("prepare", ExpathPackageModule.NAMESPACE_URI);
	private static final QName POST_SETUP_ELEMENT = new QName("finish", ExpathPackageModule.NAMESPACE_URI);
	private static final QName TARGET_COLL_ELEMENT = new QName("target", ExpathPackageModule.NAMESPACE_URI);
	private static final QName PERMISSIONS_ELEMENT = new QName("permissions", ExpathPackageModule.NAMESPACE_URI);

	private static final QName STATUS_ELEMENT = new QName("status", ExpathPackageModule.NAMESPACE_URI);

	private static final QName CLEANUP_ELEMENT = new QName("cleanup", ExpathPackageModule.NAMESPACE_URI);

	private String user = null;
	private String password = null;
	private String group = null;
	private int perms = -1;
	
	public Deploy(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		if (!context.getSubject().hasDbaRole())
			throw new XPathException(this, EXPathErrorCode.EXPDY003, "Permission denied. You need to be a member " +
					"of the dba group to use repo:deploy/undeploy");
		
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
            	throw new XPathException(this, EXPathErrorCode.EXPDY001, "Package " + pkgName + " not found");
			
			// find and parse the repo.xml descriptor
			File repoFile = new File(packageDir, "repo.xml");
			if (!repoFile.canRead())
				return Sequence.EMPTY_SEQUENCE;
			DocumentImpl repoXML = DocUtils.parse(context, new FileInputStream(repoFile));
			
			if (isCalledAs("undeploy")) {
				ElementImpl target = findElement(repoXML, TARGET_COLL_ELEMENT);
				ElementImpl cleanup = findElement(repoXML, CLEANUP_ELEMENT);
				if (cleanup != null) {
					runQuery(null, packageDir, cleanup.getStringValue());
				}
				if (target != null) {
					uninstall(args, target);
				}
				return statusReport(null);
			} else {
				// if there's a <setup> element, run the query it points to
				ElementImpl setup = findElement(repoXML, SETUP_ELEMENT);
				String path = setup == null ? null : setup.getStringValue();
				if (path != null && path.length() > 0) {
					runQuery(null, packageDir, path);
					return statusReport(null);
				} else {
					// otherwise copy all child directories to the target collection
					XmldbURI targetCollection = XmldbURI.ROOT_COLLECTION_URI;
					if (args.length == 2) {
						try {
							targetCollection = XmldbURI.create(args[1].getStringValue());
						} catch (Exception e) {
							throw new XPathException(this, EXPathErrorCode.EXPDY002, "Bad collection URI: " + args[1].getStringValue(), args[1], e);
						}
					}

					ElementImpl target = findElement(repoXML, TARGET_COLL_ELEMENT);
					if (target != null) {
						// determine target collection
						try {
							targetCollection = XmldbURI.create(target.getStringValue());
						} catch (Exception e) {
							throw new XPathException(this, EXPathErrorCode.EXPDY002, "Bad collection URI for <target> element: " + target.getStringValue(), args[0], e);
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
					if (preSetup != null) {
						path = preSetup.getStringValue();
						if (path.length() > 0)
							runQuery(targetCollection, packageDir, path);
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
							runQuery(targetCollection, packageDir, path);
					}
					
					storeRepoXML(repoXML, targetCollection);
					
					return statusReport(targetCollection.toString());
				}
				
			}
		} catch (IOException e) {
			throw new XPathException(this, ErrorCodes.FOER0000, "Caught IO error while deploying expath archive");
		}
	}

	private void uninstall(Sequence[] args, ElementImpl target)
			throws XPathException {
		// determine target collection
		XmldbURI targetCollection;
		try {
			targetCollection = XmldbURI.create(target.getStringValue());
		} catch (Exception e) {
			throw new XPathException(this, EXPathErrorCode.EXPDY002, "Bad collection URI for <target> element: " + target.getStringValue(), args[0], e);
		}
		TransactionManager mgr = context.getBroker().getBrokerPool().getTransactionManager();
		Txn txn = mgr.beginTransaction();
		try {
			Collection collection = context.getBroker().getOrCreateCollection(txn, targetCollection);
			if (collection != null)
				context.getBroker().removeCollection(txn, collection);
			
			XmldbURI configCollection = XmldbURI.CONFIG_COLLECTION_URI.append(targetCollection);
			collection = context.getBroker().getOrCreateCollection(txn, configCollection);
			if (collection != null)
				context.getBroker().removeCollection(txn, collection);
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
			throws XPathException {
		// Store repo.xml
		DateTimeValue time = new DateTimeValue(new Date());
		MemTreeBuilder builder = new MemTreeBuilder(context);
		builder.startDocument();
		UpdatingDocumentReceiver receiver = new UpdatingDocumentReceiver(builder, time.getStringValue());
		try {
			repoXML.copyTo(context.getBroker(), receiver);
		} catch (SAXException e) {
			throw new XPathException(this, "Error while updating repo.xml: " + e.getMessage());
		}
		builder.endDocument();
		DocumentImpl updatedXML = builder.getDocument();
		
		TransactionManager mgr = context.getBroker().getBrokerPool().getTransactionManager();
		Txn txn = mgr.beginTransaction();
		try {
			Collection collection = context.getBroker().getOrCreateCollection(txn, targetCollection);
			XmldbURI name = XmldbURI.createInternal("repo.xml");
			IndexInfo info = collection.validateXMLResource(txn, context.getBroker(), name, updatedXML);
			Permission permission = info.getDocument().getPermissions();
			setPermissions(MimeType.XML_TYPE, permission);
			
			collection.store(txn, context.getBroker(), info, updatedXML, false);
			
			mgr.commit(txn);
		} catch (Exception e) {
			mgr.abort(txn);
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

	private Sequence runQuery(XmldbURI targetCollection, File tempDir, String fileName)
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
		if (targetCollection != null)
			ctx.declareVariable("target", targetCollection.toString());
		else
			ctx.declareVariable("target", Sequence.EMPTY_SEQUENCE);
		ctx.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI + targetCollection.toString());
		CompiledXQuery compiled;
		try {
			compiled = xqs.compile(ctx, new FileSource(xquery, "UTF-8", false));
		} catch (PermissionDeniedException e) {
			throw new XPathException(this, e);
		}
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
			setPermissions(null, collection.getPermissions());
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
			if (!file.isDirectory() && !file.getName().equals("repo.xml")) {
				MimeType mime = mimeTab.getContentTypeFor(file.getName());
				if (mime == null)
					mime = MimeType.BINARY_TYPE;
				XmldbURI name = XmldbURI.create(file.getName());
				
				Txn txn = mgr.beginTransaction();	
				try {
					if (mime.isXMLType()) {
						InputSource is = new InputSource(file.toURI().toASCIIString());
						IndexInfo info = targetCollection.validateXMLResource(txn, context.getBroker(), name, is);
						info.getDocument().getMetadata().setMimeType(mime.getName());
						Permission permission = info.getDocument().getPermissions();
						setPermissions(mime, permission);
						
						targetCollection.store(txn, context.getBroker(), info, is, false);
					} else {
						long size = file.length();
						FileInputStream is = new FileInputStream(file);
						BinaryDocument doc = 
							targetCollection.addBinaryResource(txn, context.getBroker(), name, is, mime.getName(), size);
						is.close();
						
						Permission permission = doc.getPermissions();
						setPermissions(mime, permission);
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

	/**
	 * Set owner, group and permissions. For XQuery resources, always set the executable flag.
	 * @param mime
	 * @param permission
	 */
	private void setPermissions(MimeType mime, Permission permission) throws PermissionDeniedException {
            if (user != null){
                permission.setOwner(user);
            }
            if (group != null){
                permission.setGroup(group);
            }

            int mode;
            if (perms > -1) {
                mode = perms;
            } else {
                mode = permission.getMode();
            }
            
            if (mime != null && mime.getName().equals(MimeType.XQUERY_TYPE.getName())){
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
			super.startElement(qname, attribs);
			stack.push(qname.getLocalName());
		}
		
		@Override
		public void startElement(String namespaceURI, String localName,
				String qName, Attributes attrs) throws SAXException {
			super.startElement(namespaceURI, localName, qName, attrs);
			stack.push(localName);
		}
		
		@Override
		public void endElement(QName qname) throws SAXException {
			stack.pop();
			super.endElement(qname);
		}
		
		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			stack.pop();
			super.endElement(uri, localName, qName);
		}
		
		@Override
		public void characters(char[] ch, int start, int len)
				throws SAXException {
			if (!deployTime())
				super.characters(ch, start, len);
		}
		
		@Override
		public void characters(CharSequence seq) throws SAXException {
			if (!deployTime())
				super.characters(seq);
		}
		
		private boolean deployTime() throws SAXException {
			String current = stack.peek();
			if (current.equals("deployed")) {
				super.characters(time);
				return true;
			}
			return false;
		}
	}
}
