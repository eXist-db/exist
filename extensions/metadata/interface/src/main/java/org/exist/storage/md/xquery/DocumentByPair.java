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

import org.exist.Resource;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.storage.md.MetaData;
import org.exist.util.function.Consumer;
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
public class DocumentByPair extends BasicFunction {
	
	private static final QName RES = new QName("resource-by-pair", NAMESPACE_URI, PREFIX);
    private static final QName COL = new QName("collection-by-pair", NAMESPACE_URI, PREFIX);
    private static final QName DOC = new QName("document-by-pair", NAMESPACE_URI, PREFIX);

    private static final SequenceType[] PARAMS = new SequenceType[] {
        new FunctionParameterSequenceType("key", Type.STRING, Cardinality.EXACTLY_ONE, "The key to match"),
        new FunctionParameterSequenceType("value", Type.STRING, Cardinality.EXACTLY_ONE, "The value to match")
    };

    private static final FunctionReturnSequenceType RETURN = new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "Resources which have this key/value pair in their metadata");
	
    public final static FunctionSignature signatures[] = {
//		new FunctionSignature(
//			RES,
//            "Get the resources by match key/value pair.",
//            PARAMS,
//			RETURN
//		),
//        new FunctionSignature(
//            COL,
//            "Get the collections by match key/value pair.",
//            PARAMS,
//            RETURN
//        ),
        new FunctionSignature(
            DOC,
            "Get the documents by match key/value pair.",
            PARAMS,
            RETURN
        )
	};

	/**
	 * @param context
	 */
	public DocumentByPair(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        MetaData md = MetaData.get();
		
		final ValueSequence returnSeq = new ValueSequence();

		try {

            md.resources(
                args[0].getStringValue(),
                args[1].getStringValue(),

                new Consumer<Resource>() {
                    @Override
                    public void accept(Resource resource) {
                        if (resource instanceof DocumentImpl) {
                            returnSeq.add(new NodeProxy((DocumentImpl)resource));
                        }
                    }
                }
            );

		} catch (Exception e) {
			throw new XPathException(this, e);
		}
		
		return returnSeq;
	}
}
