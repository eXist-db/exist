/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.md.xquery;

import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.storage.md.MetaData;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

import static org.exist.storage.md.MDStorageManager.*;

/**
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author Casey Jordan <casey.jordan@jorsek.com>
 */
public class DocumentByUUID extends BasicFunction {
	
	private static final QName NAME = new QName("document-by-uuid", NAMESPACE_URI, PREFIX);
	private static final String DESCRIPTION = "Get the document by UUID.";
	private static final FunctionReturnSequenceType RETURN = new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "Resources which have given UUID");
	
    public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			NAME,
			DESCRIPTION,
			new SequenceType[] { 
				 new FunctionParameterSequenceType("UUID", Type.STRING, Cardinality.EXACTLY_ONE, "The UUID of the document you wish to fetch")
			}, 
			RETURN
		)
	};

	/**
	 * @param context
	 */
	public DocumentByUUID(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		try {
			DocumentImpl doc = MetaData.get().getDocument(args[0].getStringValue());
			if (doc == null) return Sequence.EMPTY_SEQUENCE;
			
			return new NodeProxy(doc);
		} catch (Exception e) {
			throw new XPathException(this, e);
		}
	}
}
