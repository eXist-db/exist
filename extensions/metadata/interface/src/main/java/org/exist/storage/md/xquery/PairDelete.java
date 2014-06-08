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

import static org.exist.storage.md.MDStorageManager.*;

/**
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class PairDelete extends BasicFunction {
	
	private static final QName NAME = new QName("delete", NAMESPACE_URI, PREFIX);
	private static final String DESCRIPTION = "Delete document's key/value pair.";
	private static final FunctionReturnSequenceType RETURN = new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ONE, "Key/value pair UUID");
	
    public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			NAME,
			DESCRIPTION,
			new SequenceType[] { 
				 new FunctionParameterSequenceType("document", Type.ITEM, Cardinality.EXACTLY_ONE, "The document."),
				 new FunctionParameterSequenceType("key", Type.STRING, Cardinality.EXACTLY_ONE, "The key. '*' mean delete all."),
			}, 
			RETURN
		),
        new FunctionSignature(
            NAME,
            DESCRIPTION,
            new SequenceType[] { 
                 new FunctionParameterSequenceType("uuid", Type.STRING, Cardinality.EXACTLY_ONE, "The meta UUID.")
            }, 
            RETURN
        )
	};

	/**
	 * @param context
	 */
	public PairDelete(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		Metas metas = null;

        if (args.length == 1) {
            Meta meta = MetaData.get().getMeta(args[0].getStringValue());
            meta.delete();
            return Sequence.EMPTY_SEQUENCE;
        
        } else if (args[0] instanceof DocumentImpl) {
			metas = MetaData.get().getMetas((DocumentImpl)args[0]);
		
        } else {
            metas = MetaData.get().getMetas(XmldbURI.create( args[0].getStringValue() ));

        }
//		} else
//			throw new XPathException(this, "Unsupported type "+args[0].getItemType());
		
		
		if (metas == null)
			throw new XPathException(this, "No metadata found.");
		
		String key = args[1].getStringValue();

		if ("*".equals(key)) {
		    metas.delete();
		} else {
		    metas.delete(key);
		}
		
		return Sequence.EMPTY_SEQUENCE;
	}
}
