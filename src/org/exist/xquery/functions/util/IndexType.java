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
package org.exist.xquery.functions.util;

import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 * 
 */
public class IndexType extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("index-type", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                "Returns the range index type for a set of nodes or an empty sequence if no index is defined. ", 
                new SequenceType[] {
                    new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE) 
                    },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));

    /**
     * @param context
     */
    public IndexType(XQueryContext context) {
        super(context, signature);
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.addFlag(NEED_INDEX_INFO);
        super.analyze(contextInfo);
        contextInfo.removeFlag(NEED_INDEX_INFO);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
     *      org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
         }  

        Sequence result;

    	if (args[0].isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
    	else {
	        NodeSet nodes = args[0].toNodeSet();
	        //Remember it is the default value when no index is defined
	        if (nodes.getIndexType() == Type.ANY_TYPE)
	        	result = Sequence.EMPTY_SEQUENCE;
	        else
	        	result = new StringValue(Type.getTypeName(nodes.getIndexType()));  
    	}
    	//TODO : consider modularized indexes. We should thus return a * sequence...
    	
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 

        return result;

    }

}
