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
import org.exist.storage.md.Meta;
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
 */
public class PairGet extends BasicFunction {
	
	private static final QName NAME = new QName("get-value", NAMESPACE_URI, PREFIX);
	private static final QName NAME_URL = new QName("get-value-by-url", NAMESPACE_URI, PREFIX);
	private static final String DESCRIPTION = "Get document value by key.";
	private static final String DESCRIPTION_UUID = "Get document value by UUID.";
	private static final FunctionReturnSequenceType RETURN = new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "Value.");
	
    public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			NAME,
			DESCRIPTION,
			new SequenceType[] { 
				 new FunctionParameterSequenceType("resource", Type.ITEM, Cardinality.EXACTLY_ONE, "The resource or resource's url."),
				 new FunctionParameterSequenceType("key", Type.STRING, Cardinality.EXACTLY_ONE, "The key."),
			}, 
			RETURN
		),
		new FunctionSignature(
			NAME,
			DESCRIPTION_UUID,
			new SequenceType[] { 
				 new FunctionParameterSequenceType("uuid", Type.STRING, Cardinality.EXACTLY_ONE, "The key-value pair ID."),
			}, 
			RETURN
		),
		new FunctionSignature(
			NAME_URL,
			DESCRIPTION,
			new SequenceType[] { 
				 new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "The resource's URL."),
				 new FunctionParameterSequenceType("key", Type.STRING, Cardinality.EXACTLY_ONE, "The key."),
			}, 
			RETURN,
            MetadataModule.DEPRECATED_AFTER_2_2
		)
	};

	/**
	 * @param context
	 */
	public PairGet(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		Metas metas = null;
		Meta meta = null;
		
		if (getSignature().getName().equals(NAME))
			if (args.length == 1) {
				meta = MetaData.get().getMeta(args[0].getStringValue());

			} else {
				if (args[0] instanceof DocumentImpl) {
                    metas = MetaData.get().getMetas(((DocumentImpl) args[0]));

                } else if (args[0] instanceof XmldbURI) {
                        metas = MetaData.get().getMetas(((DocumentImpl)args[0]));
                } else
					throw new XPathException(this, "Unsupported type "+args[0].getItemType());
			}
		
		else if (getSignature().getName().equals(NAME_URL))
			metas = MetaData.get().getMetas(XmldbURI.create(args[0].getStringValue()));
		
		if (metas == null && meta == null)
			throw new XPathException(this, "No metadata found.");

		if (meta == null)
			meta = metas.get(args[1].getStringValue());
		
		if (meta == null)
		    return Sequence.EMPTY_SEQUENCE;

		ValueSequence returnSeq = new ValueSequence();
		
		if (meta.getValue() instanceof DocumentImpl) {
			returnSeq.add( new NodeProxy( (DocumentImpl) meta.getValue() ) );
		
		} else {
			returnSeq.add(new StringValue(meta.getValue().toString()));
		}

		return returnSeq;
	}
}