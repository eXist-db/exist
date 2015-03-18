package org.exist.xquery.modules.expathrepo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.repo.Deployment;
import org.exist.repo.PackageLoader;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.expath.pkg.repo.PackageException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;

public class Deploy extends BasicFunction {

	protected static final Logger logger = LogManager.getLogger(Deploy.class);
	
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
            new QName("install-and-deploy", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
            "Downloads, installs and deploys a package from the public repository at $publicRepoURL. Dependencies are resolved " +
            "automatically. For downloading the package, the package name is appended to the repository URL as " +
            "parameter 'name'.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE,
                            "Unique name of the package to install."),
                    new FunctionParameterSequenceType("publicRepoURL", Type.STRING, Cardinality.EXACTLY_ONE,
                            "The URL of the public repo.")
            },
            new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE,
                    "<status result=\"ok\"/> if deployment was ok. Throws an error otherwise.")),
        new FunctionSignature(
            new QName("install-and-deploy", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
            "Downloads, installs and deploys a package from the public repository at $publicRepoURL. Dependencies are resolved " +
            "automatically. For downloading the package, the package name and version are appended to the repository URL as " +
            "parameters 'name' and 'version'.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE,
                            "Unique name of the package to install."),
                    new FunctionParameterSequenceType("version", Type.STRING, Cardinality.ZERO_OR_ONE,
                            "Version to install."),
                    new FunctionParameterSequenceType("publicRepoURL", Type.STRING, Cardinality.EXACTLY_ONE,
                            "The URL of the public repo.")
            },
            new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE,
                    "<status result=\"ok\"/> if deployment was ok. Throws an error otherwise.")),
        new FunctionSignature(
                new QName("install-and-deploy-from-db", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
                "Installs and deploys a package from a .xar archive file stored in the database. Dependencies are not " +
                "resolved and will just be ignored.",
                new SequenceType[] {
                    new FunctionParameterSequenceType("path", Type.STRING, Cardinality.EXACTLY_ONE,
                        "Database path to the package archive (.xar file)")
                },
                new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE,
                        "<status result=\"ok\"/> if deployment was ok. Throws an error otherwise.")),
        new FunctionSignature(
            new QName("install-and-deploy-from-db", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
            "Installs and deploys a package from a .xar archive file stored in the database. Dependencies will be downloaded " +
            "from the public repo and installed automatically.",
            new SequenceType[] {
                new FunctionParameterSequenceType("path", Type.STRING, Cardinality.EXACTLY_ONE,
                        "Database path to the package archive (.xar file)"),
                new FunctionParameterSequenceType("publicRepoURL", Type.STRING, Cardinality.EXACTLY_ONE,
                        "The URL of the public repo.")
            },
            new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE,
                    "<status result=\"ok\"/> if deployment was ok. Throws an error otherwise.")),
		new FunctionSignature(
				new QName("undeploy", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
				"Uninstall the resources belonging to a package from the db. Calls cleanup scripts if defined.",
				new SequenceType[] { new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name")},
				new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, 
						"<status result=\"ok\"/> if deployment was ok. Throws an error otherwise."))
	};

	private static final QName STATUS_ELEMENT = new QName("status", ExpathPackageModule.NAMESPACE_URI);
	
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
            Deployment deployment = new Deployment(context.getBroker());
            String target;
            if (isCalledAs("deploy")) {
                String userTarget = null;
                if (getArgumentCount() == 2)
                    userTarget = args[1].getStringValue();
                target = deployment.deploy(pkgName, context.getRepository(), userTarget);
            } else if (isCalledAs("install-and-deploy")) {
                String version = null;
                String repoURI;
                if (getArgumentCount() == 3) {
                    version = args[1].getStringValue();
                    repoURI = args[2].getStringValue();
                } else {
                    repoURI = args[1].getStringValue();
                }
                target = installAndDeploy(pkgName, version, repoURI);
            } else if (isCalledAs("install-and-deploy-from-db")) {
                String repoURI = null;
                if (getArgumentCount() == 2)
                    repoURI = args[1].getStringValue();
                target = installAndDeployFromDb(pkgName, repoURI);
            } else
                target = deployment.undeploy(pkgName, context.getRepository());
            return statusReport(target);
        } catch (PackageException e) {
            throw new XPathException(this, EXPathErrorCode.EXPDY001, e.getMessage());
        } catch (IOException e) {
            throw new XPathException(this, ErrorCodes.FOER0000, "Caught IO error while deploying expath archive");
        }
    }

    private String installAndDeploy(String pkgName, String version, String repoURI) throws XPathException {
        try {
            RepoPackageLoader loader = new RepoPackageLoader(repoURI);
            Deployment deployment = new Deployment(context.getBroker());
            File xar = loader.load(pkgName, new PackageLoader.Version(version, false));
            if (xar != null)
                return deployment.installAndDeploy(xar, loader);
            return null;
        } catch (MalformedURLException e) {
            throw new XPathException(this, EXPathErrorCode.EXPDY005, "Malformed URL: " + repoURI);
        } catch (PackageException e) {
            throw new XPathException(this, EXPathErrorCode.EXPDY007, e.getMessage());
        } catch (IOException e) {
            throw new XPathException(this, EXPathErrorCode.EXPDY007, e.getMessage());
        }
    }

    private String installAndDeployFromDb(String path, String repoURI) throws XPathException {
        XmldbURI docPath = XmldbURI.createInternal(path);
        DocumentImpl doc = null;
        try {
            doc = context.getBroker().getXMLResource(docPath, Lock.READ_LOCK);
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
                throw new XPathException(this, EXPathErrorCode.EXPDY001, path + " is not a valid .xar", new StringValue(path));

            File file = ((NativeBroker)context.getBroker()).getCollectionBinaryFileFsPath(doc.getURI());
            RepoPackageLoader loader = null;
            if (repoURI != null)
                loader = new RepoPackageLoader(repoURI);
            Deployment deployment = new Deployment(context.getBroker());
            return deployment.installAndDeploy(file, loader);
        } catch (PackageException e) {
            throw new XPathException(this, EXPathErrorCode.EXPDY007, e.getMessage());
        } catch (IOException e) {
            throw new XPathException(this, EXPathErrorCode.EXPDY007, e.getMessage());
        } catch (PermissionDeniedException e) {
            throw new XPathException(this, EXPathErrorCode.EXPDY007, e.getMessage());
        } finally {
            if (doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
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

	@Override
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
	}

    private class RepoPackageLoader implements PackageLoader {

        private String repoURL;

        public RepoPackageLoader(String repoURL) {
            this.repoURL = repoURL;
        }

        public File load(String name, Version version) throws IOException {
            String pkgURL = repoURL + "?name=" + URLEncoder.encode(name, "UTF-8");
            if (version != null) {
                if (version.getMin() != null) {
                    pkgURL += "&semver-min=" + version.getMin();
                }
                if (version.getMax() != null) {
                    pkgURL += "&semver-max=" + version.getMax();
                }
                if (version.getSemVer() != null) {
                    pkgURL += "&semver=" + version.getSemVer();
                }
                if (version.getVersion() != null) {
                    pkgURL += "&version=" + URLEncoder.encode(version.getVersion(), "UTF-8");
                }
            }
            LOG.info("Retrieving package from " + pkgURL);
            HttpURLConnection connection = (HttpURLConnection) new URL(pkgURL).openConnection();
            connection.setConnectTimeout(15 * 1000);
            connection.setReadTimeout(15 * 1000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 6.0; en-US; rv:1.9.1.2) " +
                    "Gecko/20090729 Firefox/3.5.2 (.NET CLR 3.5.30729)");
            connection.connect();
            InputStream is = connection.getInputStream();

            File outFile = File.createTempFile("deploy", "xar");
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(outFile);

                byte[] buf = new byte[512];
                int count;
                while ((count = is.read(buf)) > 0) {
                    fos.write(buf, 0, count);
                }
                return outFile;
            } finally {
                if (fos != null)
                    fos.close();
            }
        }
    }
}
