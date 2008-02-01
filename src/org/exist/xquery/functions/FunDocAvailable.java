/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.xquery.*;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.value.*;

/**
 * Implements the XQuery's fn:doc-available() function.
 * 
 * @author wolf
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class FunDocAvailable extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("doc-available", Function.BUILTIN_FUNCTION_NS),
			"Returns whether or not the document specified in the input sequence is available. " +
            "The arguments are either document pathes like '" +
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays/hamlet.xml' or " +
			"XMLDB URIs like 'xmldb:exist://localhost:8081/" +
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays/hamlet.xml' or " +  
            "standard URLs, starting with http://, file://, etc.",
			new SequenceType[] { new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));	
	
	public FunDocAvailable(XQueryContext context) {
		super(context, signature);		
	}

	/**
	 * @see org.exist.xquery.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET;
	}

	/**
	 * @see org.exist.xquery.Expression#eval(Sequence, Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {		
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }       
        
        Sequence result;
		Sequence arg = getArgument(0).eval(contextSequence, contextItem);
		if (arg.isEmpty())
            result = BooleanValue.FALSE;
        else {		
    		String path = arg.itemAt(0).getStringValue();    		
    		try {
    			result = BooleanValue.valueOf(DocUtils.isDocumentAvailable(this.context, path));
    		}
    		catch (Exception e) {
    			throw new XPathException(getASTNode(), e.getMessage());			
    		}            
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;        
		
	}	

	/**
	 * @see org.exist.xquery.Expression#resetState(boolean)
     * @param postOptimization
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		getArgument(0).resetState(postOptimization);
	}
}
