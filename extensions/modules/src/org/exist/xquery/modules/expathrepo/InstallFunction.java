package org.exist.xquery.modules.expathrepo;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Repository;
import org.expath.pkg.repo.UserInteractionStrategy;
import org.expath.pkg.repo.tui.BatchUserInteraction;

import java.net.URI;
import java.net.URISyntaxException;
import org.exist.repo.ExistRepository;


/**
 * Install Function: Install package into repository
 *
 * @author James Fuller <jim.fuller@exist-db.org>
 * @author cutlass
 * @version 1.0
 */
public class InstallFunction extends BasicFunction {

	private final static Logger logger = Logger.getLogger(InstallFunction.class);

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("install", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
			"Install package from repository.",
			new SequenceType[] { new FunctionParameterSequenceType("text", Type.STRING, Cardinality.ZERO_OR_MORE, "package name")},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if successful, false otherwise"));

	public InstallFunction(XQueryContext context) {
		super(context, signature);
 	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
        Sequence removed = BooleanValue.FALSE;
        boolean force = true;
        UserInteractionStrategy interact = new BatchUserInteraction();
        String pkg = args[0].getStringValue();
        URI uri = _getURI(pkg);
        try {

            if ( pkg == null ) {
                System.err.println("Package name required");
            }
            else {
                ExistRepository repo = getContext().getRepository();
                Repository parent_repo = repo.getParentRepo();
                parent_repo.installPackage(uri,force,interact);
            }
            removed = BooleanValue.TRUE;
        } catch (PackageException ex ) {
        	logger.debug(ex.getMessage(), ex);
            return removed;
            // /TODO: _repo.removePackage seems to throw PackageException
            //throw new XPathException("Problem installing package " + pkg + " in expath repository, check that eXist-db has access permissions to expath repository file directory  ", ex);
        }
        return removed;
	}

    private URI _getURI(String s)
    {
        URI uri;
        try {
            uri = new URI(s);
        }
        catch ( URISyntaxException ex ) {
            return null;
        }
        if ( uri.isAbsolute() ) {
            return uri;
        }
        else {
            return null;
        }
    }

}