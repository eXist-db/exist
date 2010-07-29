package org.exist.xquery.modules.expathrepo;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
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

import java.io.File;
import org.expath.pkg.repo.PackageException;
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
	private final static Logger logger = Logger.getLogger(ListFunction.class);

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("list", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
			"List repository packages.",
			null,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "sequence of strings"));

    private static Repository _repo = null;

	public ListFunction(XQueryContext context) {
		super(context, signature);
 	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
        ValueSequence result = new ValueSequence();
        try {
            String existHome = System.getProperty("exist.home");            
            if (existHome != null){
                new File( existHome + "/webapp/WEB-INF/expathrepo").mkdir();
                _repo = new Repository(new File( existHome + "/webapp/WEB-INF/expathrepo"));

            }else{
                new File( System.getProperty("java.io.tmpdir") + "/expathrepo").mkdir();
                _repo = new Repository(new File( System.getProperty("java.io.tmpdir") + "/expathrepo"));
            }

            for ( File p : _repo.listPackages() ) {
                System.out.println(p);
                result.add(new StringValue(p.getName()));
            }
        } catch (PackageException ex ) {
            throw new XPathException("Problem listing packages in expath repository ", ex);
        }
        return result;
	}
}