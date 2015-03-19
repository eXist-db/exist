package org.exist.xquery.modules.expathrepo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.repo.ExistRepository;
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


/**
 * Remove Function: Remove package from repository
 *
 * @author James Fuller <jim.fuller@exist-db.org>
 * @author cutlass
 * @version 1.0
 */
public class RemoveFunction extends BasicFunction {
    @SuppressWarnings("unused")
	private final static Logger logger = LogManager.getLogger(RemoveFunction.class);

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("remove", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
			"Remove package from repository.",
			new SequenceType[] { new FunctionParameterSequenceType("text", Type.STRING, Cardinality.ZERO_OR_MORE, "package name")},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if successful, false otherwise"));

	public RemoveFunction(XQueryContext context) {
		super(context, signature);
 	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
        Sequence removed = BooleanValue.TRUE;
        boolean force = false;
        UserInteractionStrategy interact = new BatchUserInteraction();
        String pkg = args[0].getStringValue();

        try {
            if ( pkg == null ) {
                System.err.println("Package name required");
            }
            else {
                ExistRepository repo = getContext().getRepository();
                Repository parent_repo = repo.getParentRepo();
                parent_repo.removePackage(pkg, force, interact);
                repo.reportAction(ExistRepository.Action.UNINSTALL, pkg);
                context.getBroker().getBrokerPool().getXQueryPool().clear();
            }
        } catch (PackageException ex ) {
            return removed;
            // /TODO: _repo.removePackage seems to throw PackageException
            // throw new XPathException("Problem removing package " + pkg + " in expath repository, check that eXist-db has access permissions to expath repository file directory  ", ex);
        }
        return removed;
	}
}