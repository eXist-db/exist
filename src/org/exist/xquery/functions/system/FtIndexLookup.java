/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-09 The eXist Project
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
 *  $Id$
 */
package org.exist.xquery.functions.system;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.storage.analysis.Tokenizer;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.DynamicCardinalityCheck;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FtIndexLookup extends Function {

    protected final static Logger logger = Logger.getLogger(FtIndexLookup.class);

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("ft-index-lookup", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Internal function doing old full-text index lookup filtering. Intended to support the query optimizer by allowing restrictive filtering early on.",
            new SequenceType[]{
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE, "The nodes"),
                new FunctionParameterSequenceType("string-filter", Type.STRING, Cardinality.ZERO_OR_ONE, "The string-filter")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the nodes matching the string-filter"));
    
    public FtIndexLookup(XQueryContext context) {
        super(context, signature);
    }

    /**
     * Overwritten: function can process the whole context sequence at once.
     * 
     * @see org.exist.xquery.Expression#getDependencies()
     */
    public int getDependencies() {
        return Dependency.CONTEXT_SET;
    }
    
    /**
     * Overwritten to disable automatic type checks. We check manually.
     * 
     * @see org.exist.xquery.Function#setArguments(java.util.List)
     */
    public void setArguments(List arguments) throws XPathException {
        // wrap arguments into a cardinality check, so an error will be generated if
        // one of the arguments returns an empty sequence
        Expression arg = (Expression) arguments.get(0);
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_MORE, arg,
                new Error(Error.FUNC_PARAM_CARDINALITY, "1", mySignature));
        steps.add(arg);
        
        arg = (Expression) arguments.get(1);
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_MORE, arg,
                new Error(Error.FUNC_PARAM_CARDINALITY, "2", mySignature));
        steps.add(arg);
    }
    
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        // call analyze for each argument
        inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
        for(int i = 0; i < getArgumentCount(); i++) {
            getArgument(i).analyze(contextInfo);
        }
    }
    
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {

        Sequence querySeq = getArgument(1).eval(contextSequence);
        if (querySeq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        String query = querySeq.itemAt(0).getStringValue();
        
        String[] terms = getSearchTerms(query);
        NodeSet hits[] = new NodeSet[terms.length];
        NodeSet contextSet = contextSequence.toNodeSet();
        for (int k = 0; k < terms.length; k++) {
            hits[k] =
                    context.getBroker().getTextEngine().getNodesContaining(
                            context,
                            contextSet.getDocumentSet(),
                            null, NodeSet.DESCENDANT, null,
                            terms[k],
                            DBBroker.MATCH_EXACT);
            hits[k] = getArgument(0).eval(hits[k]).toNodeSet();
        }
        
        NodeSet result = hits[0];
        for(int k = 1; k < hits.length; k++) {
            if(hits[k] != null)
                result = result.deepIntersection(hits[k]);
        }
    	logger.debug("FOUND: " + result.getLength());
        return result;
    }
    
    protected String[] getSearchTerms(String searchString) {
        List tokens = new ArrayList();
        Tokenizer tokenizer =
            context.getBroker().getTextEngine().getTokenizer();
        tokenizer.setText(searchString);
        org.exist.storage.analysis.TextToken token;
        String word;
        while (null != (token = tokenizer.nextToken(true))) {
            word = token.getText();
            tokens.add(word);
        }
        String[] terms = new String[tokens.size()];
        terms = (String[]) tokens.toArray(terms);
        return terms;
    }
}
