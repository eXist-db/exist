/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.text;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.util.Occurrences;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author wolf
 */
public class IndexTerms extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("index-terms", TextModule.NAMESPACE_URI, TextModule.PREFIX),
            "This function can be used to collect some information on the distribution " +
            "of index terms within a set of collections. For each distinct collection in $a " +
            "and every term in the collection that starts with substring $b, " +
            "the function $c is called with three arguments: 1) the term as found in the index, " +
            "2) the overall frequency of the term within the collection, 3) the number of documents " +
            "the term occurs in. The function pointer to be passed in the third argument can be " +
            "created with the util:function function. The functions should have an arity of 3.",
            new SequenceType[]{
                    new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                    new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.INT, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));
    
    public IndexTerms(XQueryContext context) {
        super(context, signature);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        if(args[0].getLength() == 0)
            return Sequence.EMPTY_SEQUENCE;
        NodeSet nodes = args[0].toNodeSet();
        DocumentSet docs = nodes.getDocumentSet();
        String start = args[1].getStringValue();
        FunctionReference ref = (FunctionReference) args[2].itemAt(0);
        int max = ((IntegerValue) args[3].itemAt(0)).getInt();
        FunctionCall call = ref.getFunctionCall();
        Sequence result = new ValueSequence();
        try {
            Occurrences occur[] = 
                context.getBroker().getTextEngine().scanIndexTerms(docs, nodes, start, null);
            int len = (occur.length > max ? max : occur.length);
            Sequence params[] = new Sequence[2];
            ValueSequence data = new ValueSequence();
            for (int j = 0; j < len; j++) {
                params[0] = new StringValue(occur[j].getTerm().toString());
                data.add(new IntegerValue(occur[j].getOccurrences(), Type.UNSIGNED_INT));
                data.add(new IntegerValue(occur[j].getDocuments(), Type.UNSIGNED_INT));
                data.add(new IntegerValue(j + 1, Type.UNSIGNED_INT));
                params[1] = data;
                
                result.addAll(call.evalFunction(contextSequence, null, params));
                data.clear();
            }
            LOG.debug("Returning: " + result.getLength());
            return result;
        } catch (PermissionDeniedException e) {
        	throw new XPathException(getASTNode(), e.getMessage(), e);
        }
    }

}
