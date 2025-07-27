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
package org.exist.xquery.modules.expathrepo;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.SystemProperties;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.persistent.LockedDocument;
import org.exist.repo.Deployment;
import org.exist.repo.PackageLoader;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.io.TemporaryFileManager;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.XarFileSource;
import org.expath.pkg.repo.XarSource;
import org.xml.sax.helpers.AttributesImpl;

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
	
	public Deploy(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence)
			throws XPathException {
		if (!context.getSubject().hasDbaRole())
			throw new XPathException(this, EXPathErrorCode.EXPDY003, "Permission denied. You need to be a member " +
					"of the dba group to use repo:deploy/undeploy");
		
		final String pkgName = args[0].getStringValue();
        try {
            Deployment deployment = new Deployment();
            final Optional<String> target;
            if (isCalledAs("deploy")) {
                String userTarget = null;
                if (getArgumentCount() == 2) {
                    userTarget = args[1].getStringValue();
                }
                try (final Txn transaction = context.getBroker().continueOrBeginTransaction()) {
                    target = deployment.deploy(context.getBroker(), transaction, pkgName, context.getRepository(), userTarget);
                    transaction.commit();
                }
            } else if (isCalledAs("install-and-deploy")) {
                String version = null;
                final String repoURI;
                if (getArgumentCount() == 3) {
                    version = args[1].getStringValue();
                    repoURI = args[2].getStringValue();
                } else {
                    repoURI = args[1].getStringValue();
                }
                try (final Txn transaction = context.getBroker().continueOrBeginTransaction()) {
                    target = installAndDeploy(transaction, pkgName, version, repoURI);
                    transaction.commit();
                }
            } else if (isCalledAs("install-and-deploy-from-db")) {
                String repoURI = null;
                if (getArgumentCount() == 2) {
                    repoURI = args[1].getStringValue();
                }
                try (final Txn transaction = context.getBroker().continueOrBeginTransaction()) {
                    target = installAndDeployFromDb(transaction, pkgName, repoURI);
                    transaction.commit();
                }
            } else {
                try (final Txn transaction = context.getBroker().continueOrBeginTransaction()) {
                    target = deployment.undeploy(context.getBroker(), transaction, pkgName, context.getRepository());
                    transaction.commit();
                }
	        }
	        target.orElseThrow(() -> new XPathException(this, "expath repository is not available."));
            return statusReport(target);
        } catch (PackageException e) {
            throw new XPathException(this, EXPathErrorCode.EXPDY001, e.getMessage(), args[0], e);
        } catch (IOException e) {
            throw new XPathException(this, ErrorCodes.FOER0000, "Caught IO error while deploying expath archive", args[0], e);
        } catch (TransactionException e) {
            throw new XPathException(this, ErrorCodes.FOER0000, "Caught transaction error while deploying expath archive", args[0], e);
        }
    }

    private Optional<String> installAndDeploy(final Txn transaction, final String pkgName, final String version, final String repoURI) throws XPathException {
        try {
            final RepoPackageLoader loader = new RepoPackageLoader(repoURI);
            final Deployment deployment = new Deployment();
            final XarSource xar = loader.load(pkgName, new PackageLoader.Version(version, false));
            if (xar != null) {
                return deployment.installAndDeploy(context.getBroker(), transaction, xar, loader);
            }
            return Optional.empty();
        } catch (final MalformedURLException e) {
            throw new XPathException(this, EXPathErrorCode.EXPDY005, "Malformed URL: " + repoURI);
        } catch (final PackageException | IOException e) {
            LOG.error(e.getMessage(), e);
            throw new XPathException(this, EXPathErrorCode.EXPDY007, e.getMessage());
        }
    }

    private Optional<String> installAndDeployFromDb(final Txn transaction, final String path, final String repoURI) throws XPathException {
        final XmldbURI docPath = XmldbURI.createInternal(path);
        try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(docPath, LockMode.READ_LOCK)) {
            if(lockedDoc == null) {
                throw new XPathException(this, EXPathErrorCode.EXPDY001, path + " no such .xar", new StringValue(this, path));
            }

            final DocumentImpl doc = lockedDoc.getDocument();
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                throw new XPathException(this, EXPathErrorCode.EXPDY001, path + " is not a valid .xar", new StringValue(this, path));
            }

            RepoPackageLoader loader = null;
            if (repoURI != null) {
                loader = new RepoPackageLoader(repoURI);
            }

            final XarSource xarSource =  new BinaryDocumentXarSource(context.getBroker().getBrokerPool(), transaction, (BinaryDocument)doc);
            final Deployment deployment = new Deployment();
            return deployment.installAndDeploy(context.getBroker(), transaction, xarSource, loader);
        } catch (PackageException | IOException | PermissionDeniedException e) {
            LOG.error(e.getMessage(), e);
            throw new XPathException(this, EXPathErrorCode.EXPDY007, "Package installation failed: " + e.getMessage(), new StringValue(this, e.getMessage()));
        }
    }

	private Sequence statusReport(final Optional<String> target) {
		context.pushDocumentContext();
		try {
			final MemTreeBuilder builder = context.getDocumentBuilder();
			final AttributesImpl attrs = new AttributesImpl();
			if (target.isPresent()) {
			    attrs.addAttribute("", "result", "result", "CDATA", "ok");
			    attrs.addAttribute("", "target", "target", "CDATA", target.get());
			} else {
			    attrs.addAttribute("", "result", "result", "CDATA", "fail");
			}
			builder.startElement(STATUS_ELEMENT, attrs);
			builder.endElement();
			
			return builder.getDocument().getNode(1);
		} finally {
			context.popDocumentContext();
		}
		
	}

	@Override
	public void resetState(final boolean postOptimization) {
		super.resetState(postOptimization);
	}

    private record RepoPackageLoader(String repoURL) implements PackageLoader {

        @Override
            public XarSource load(final String name, final Version version) throws IOException {
                String pkgURL = repoURL + "?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) +
                        "&processor=" + SystemProperties.getInstance().getSystemProperty("product-version", "2.2.0");
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
                        pkgURL += "&version=" + URLEncoder.encode(version.getVersion(), StandardCharsets.UTF_8);
                    }
                }
                LOG.info("Retrieving package from {}", pkgURL);
                final HttpURLConnection connection = (HttpURLConnection) URI.create(pkgURL).toURL().openConnection();
                connection.setConnectTimeout(15 * 1000);
                connection.setReadTimeout(15 * 1000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 6.0; en-US; rv:1.9.1.2) " +
                        "Gecko/20090729 Firefox/3.5.2 (.NET CLR 3.5.30729)");
                connection.connect();

                // TODO(AR) we likely don't need temporary caching here! could just use UriXarSource
                try (final InputStream is = connection.getInputStream()) {
                    final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
                    final Path outFile = temporaryFileManager.getTemporaryFile();
                    Files.copy(is, outFile, StandardCopyOption.REPLACE_EXISTING);
                    return new XarFileSource(outFile);
                } catch (IOException e) {
                    throw new IOException("Failed to install dependency from " + pkgURL + ": " + e.getMessage());
                }
            }
        }
}
