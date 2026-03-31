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
import org.expath.pkg.repo.Packages;
import org.expath.pkg.repo.Repository;

import java.util.Optional;

/**
 * List function: Lists out repository packages
 *
 * @author <a href="mailto:jim.fuller@exist-db.org">James Fuller</a>
 * @author cutlass
 * @version 1.0
 */
public class ListFunction extends BasicFunction {
    @SuppressWarnings("unused")
	private static final Logger logger = LogManager.getLogger(ListFunction.class);

    public static final FunctionSignature signature =
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
            Optional<ExistRepository> repo = getContext().getRepository();
	    if (repo.isPresent()) {
		try {
		    Repository parent_repo = repo.get().getParentRepo();
		    for ( Packages pkg :  parent_repo.listPackages() ) {
			String name = pkg.name();
			result.add(new StringValue(this, name));
		    }
		} catch (Exception ex) {
		    throw new XPathException(this, "Problem listing packages in expath repository ", ex);
		}
		return result;
	    } else {
		throw new XPathException(this, "expath repository not available");
	    }

	}
}
