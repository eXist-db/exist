package org.exist.xquery.modules.expathrepo;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.repo.ExistPkgInfo;
import org.exist.repo.ExistRepository;
import org.exist.security.PermissionDeniedException;
import org.exist.repo.ClasspathHelper;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.tui.BatchUserInteraction;


/**
 * Install Function: Install package into repository
 *
 * @author James Fuller <jim.fuller@exist-db.org>
 * @author Wolfgang Meier
 * @version 1.0
 */
public class InstallFunction extends BasicFunction {

	private final static Logger logger = LogManager.getLogger(InstallFunction.class);

    public final static FunctionSignature signatureInstall =
		new FunctionSignature(
			new QName("install", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
			"Install package from repository.",
			new SequenceType[] { new FunctionParameterSequenceType("text", Type.STRING, Cardinality.EXACTLY_ONE, "package name")},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if successful, false otherwise"));

    public final static FunctionSignature signatureInstallFromDB =
		new FunctionSignature(
			new QName("install-from-db", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
			"Install package stored in database.",
			new SequenceType[] { new FunctionParameterSequenceType("path", Type.STRING, Cardinality.EXACTLY_ONE, "database path to the package archive (.xar file)")},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if successful, false otherwise"));
    
	public InstallFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
 	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
        Sequence removed = BooleanValue.FALSE;
        boolean force = true;
        UserInteractionStrategy interact = new BatchUserInteraction();
        String pkgOrPath = args[0].getStringValue();
        
        ExistRepository repo = getContext().getRepository();
        Repository parent_repo = repo.getParentRepo();

        try {
            Package pkg;
            if (isCalledAs("install")) {
        		// download .xar from a URI
        		URI uri = _getURI(pkgOrPath);
                pkg = parent_repo.installPackage(uri, force, interact);
                repo.reportAction(ExistRepository.Action.INSTALL, pkg.getName());
        	} else {
        		// .xar is stored as a binary resource
        		BinaryDocument doc = null;
        		try {
        			doc = _getDocument(pkgOrPath);
        			File file = ((NativeBroker)context.getBroker()).getCollectionBinaryFileFsPath(doc.getURI());
        			LOG.debug("Installing file: " + file.getAbsolutePath());
        			pkg = parent_repo.installPackage(file, force, interact);
                    repo.reportAction(ExistRepository.Action.INSTALL, pkg.getName());
        		} finally {
        			if (doc != null)
        				doc.getUpdateLock().release(Lock.READ_LOCK);
        		}
        	}
            ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
            if (info != null && !info.getJars().isEmpty())
                ClasspathHelper.updateClasspath(context.getBroker().getBrokerPool(), pkg);
            // TODO: expath libs do not provide a way to see if there were any XQuery modules installed at all
            context.getBroker().getBrokerPool().getXQueryPool().clear();
            removed = BooleanValue.TRUE;
        } catch (PackageException ex ) {
        	logger.debug(ex.getMessage(), ex);
            return removed;
            // /TODO: _repo.removePackage seems to throw PackageException
            //throw new XPathException("Problem installing package " + pkg + " in expath repository, check that eXist-db has access permissions to expath repository file directory  ", ex);
        }
        return removed;
	}

    private URI _getURI(String s) throws XPathException
    {
        URI uri;
        try {
            uri = new URI(s);
        }
        catch ( URISyntaxException ex ) {
        	throw new XPathException(this, EXPathErrorCode.EXPDY001, s + " is not a valid URI: " + ex.getMessage(), new StringValue(s), ex);
        }
        if ( uri.isAbsolute() ) {
            return uri;
        } else {
        	throw new XPathException(this, EXPathErrorCode.EXPDY001, s + " must be an absolute URI", new StringValue(s));
        }
    }

    private BinaryDocument _getDocument(String path) throws XPathException {
    	try {
			XmldbURI uri = XmldbURI.createInternal(path);
			DocumentImpl doc = context.getBroker().getXMLResource(uri, Lock.READ_LOCK);
			if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
				throw new XPathException(this, EXPathErrorCode.EXPDY001, path + " is not a valid .xar", new StringValue(path));
			return (BinaryDocument) doc;
		} catch (PermissionDeniedException e) {
			throw new XPathException(this, EXPathErrorCode.EXPDY003, e.getMessage(), new StringValue(path), e);
		}
    }
}