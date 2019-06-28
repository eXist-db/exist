/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2010-2015 The eXist-db Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */
package org.exist.xquery.modules.expathrepo;

import java.util.Optional;

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
 * @author <a href="mailto:jim.fuller@exist-db.org">James Fuller</a>
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
            Optional<ExistRepository> repo = getContext().getRepository();
	    if (repo.isPresent()) {
		try {
		    Repository parent_repo = repo.get().getParentRepo();
		    for ( Packages pkg :  parent_repo.listPackages() ) {
			String name = pkg.name();
			result.add(new StringValue(name));
		    }
		} catch (Exception ex) {
		    throw new XPathException("Problem listing packages in expath repository ", ex);
		}
		return result;
	    } else {
		throw new XPathException("expath repository not available");
	    }

	}
}
