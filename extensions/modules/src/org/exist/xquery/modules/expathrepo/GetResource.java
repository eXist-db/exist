package org.exist.xquery.modules.expathrepo;

import java.io.File;

import javax.xml.transform.stream.StreamSource;

import org.exist.dom.QName;
import org.exist.repo.ExistRepository;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryDocument;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Packages;
import org.expath.pkg.repo.Storage;

public class GetResource extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-resource", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
			"Retrieves the specified resource from an installed expath application package.",
			new SequenceType[] {
				new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name"),
				new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "resource path")
			},
			new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, 
					"<status result=\"ok\"/> if deployment was ok. Throws an error otherwise."));
	
	public GetResource(XQueryContext context) {
		super(context, signature);
	}
	
	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		String pkgName = args[0].getStringValue();
		String path = args[1].getStringValue();
		try {
			File packageDir = null;
			
			ExistRepository repo = context.getRepository();
			Package pkg = null;
			for (Packages pp : repo.getParentRepo().listPackages()) {
				pkg = pp.latest();
				if (pkg.getName().equals(pkgName)) {
					try {
						StreamSource source = pkg.getResolver().resolveResource(path);
						return Base64BinaryDocument.getInstance(context, source.getInputStream());
					} catch (Storage.NotExistException ex) {
						// nothing
					}
				}
			}
			return Sequence.EMPTY_SEQUENCE;
		} catch (PackageException e) {
			throw new XPathException(this, ErrorCodes.FOER0000, "Caught package error while reading expath package");
		}
	}

}
