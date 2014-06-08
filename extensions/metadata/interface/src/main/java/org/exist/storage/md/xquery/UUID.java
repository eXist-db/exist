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
import org.exist.dom.QName;
import org.exist.storage.md.MetaData;
import org.exist.storage.md.Metas;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.exist.xquery.value.StringValue;

import static org.exist.storage.md.MDStorageManager.*;

/**
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author Casey Jordan <casey.jordan@jorsek.com>
 */
public class UUID extends BasicFunction {
	
	private static final QName NAME = new QName("uuid", NAMESPACE_URI, PREFIX);
	private static final QName NAME_URL = new QName("uuid-by-url", NAMESPACE_URI, PREFIX);
	private static final String DESCRIPTION = "Get the UUID of document.";
	private static final FunctionReturnSequenceType RETURN = new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE, "UUID of given document.");
	
    public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			NAME,
			DESCRIPTION,
			new SequenceType[] { 
				 new FunctionParameterSequenceType("document", Type.ITEM, Cardinality.EXACTLY_ONE, "Document")
			}, 
			RETURN
		),
		new FunctionSignature(
			NAME_URL,
			DESCRIPTION,
			new SequenceType[] { 
				 new FunctionParameterSequenceType("document", Type.STRING, Cardinality.EXACTLY_ONE, "Document's URL")
			}, 
			RETURN
		)
	};

	/**
	 * @param context
	 */
	public UUID(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		Metas metas = null;
		if (getSignature().getName().equals(NAME))
			if (args[0] instanceof DocumentImpl) {
				metas = MetaData.get().getMetas(((DocumentImpl)args[0]));
				
			} else
				throw new XPathException(this, "Unsupported type "+args[0].getItemType());
		
		else if (getSignature().getName().equals(NAME_URL))
			metas = MetaData.get().getMetas(XmldbURI.create(args[0].getStringValue()));
		
		if (metas == null)
			throw new XPathException(this, "No metadata found.");

		ValueSequence returnSeq = new ValueSequence();
		
		returnSeq.add(new StringValue(metas.getUUID()));
		return returnSeq;
	}
}
