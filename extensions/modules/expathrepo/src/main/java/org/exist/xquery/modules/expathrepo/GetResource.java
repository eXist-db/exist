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

import java.util.Optional;

import javax.xml.transform.stream.StreamSource;

import org.exist.dom.QName;
import org.exist.repo.ExistRepository;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
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

		Optional<ExistRepository> repo = context.getRepository();
		if (repo.isPresent()) {
		    try {
                for (Packages pp : repo.get().getParentRepo().listPackages()) {
                    final Package pkg = pp.latest();
                    if (pkg.getName().equals(pkgName)) {
                        try {
                            // FileSystemResolver already returns an input stream
                            StreamSource source = (StreamSource) pkg.getResolver().resolveResource(path);
                            return Base64BinaryDocument.getInstance(context, source.getInputStream());
                        } catch (Storage.NotExistException e) {
                            throw new XPathException(ErrorCodes.FODC0002, "Resource " + path + " does not exist.", e);
                        }
                    }
                }
		    } catch (PackageException e) {
			    throw new XPathException(this, ErrorCodes.FOER0000, "Caught package error while reading expath package");
		    }
		} else {
		    throw new XPathException("expath repository not available");
		}
        return Sequence.EMPTY_SEQUENCE;
	}
}
