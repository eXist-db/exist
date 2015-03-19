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
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import org.expath.pkg.repo.Packages;
import org.expath.pkg.repo.Repository;

/**
 * List function: Lists out repository packages
 *
 * @author James Fuller <jim.fuller@exist-db.org>
 * @author cutlass
 * @version 1.0
 */
public class ListFunction extends BasicFunction {
    @SuppressWarnings("unused")
	private final static Logger logger = LogManager.getLogger(ListFunction.class);

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("list", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
			"List repository packages.",
			null,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "sequence of strings"));

	public ListFunction(XQueryContext context) {
		super(context, signature);
 	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
        ValueSequence result = new ValueSequence();
        try {
            ExistRepository repo = getContext().getRepository();
            Repository parent_repo = repo.getParentRepo();
            for ( Packages pkg :  parent_repo.listPackages() ) {
                String name = pkg.name();
                result.add(new StringValue(name));
            }
        } catch (Exception ex ) {
            throw new XPathException("Problem listing packages in expath repository ", ex);
        }
        return result;
	}
}