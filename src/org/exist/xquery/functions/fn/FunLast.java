/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2000-2009 The eXist team
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
 * $Id$
 */

package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Built-in function fn:last().
 * 
 * @author wolf
 */
public class FunLast extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("last", Function.BUILTIN_FUNCTION_NS),
			"Returns the context size from the dynamic context. " + 
			"If the context item is undefined, an error is raised.",
			null,
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "the context size from the dynamic context"));

	public FunLast(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		if (inPredicate)
			{return Dependency.CONTEXT_SET;}
		else
			{return Dependency.CONTEXT_SET +
			Dependency.CONTEXT_POSITION;}				
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
        Sequence inSequence = context.getContextSequence();
        if (inSequence == null)
        	{inSequence = contextSequence;}
        
        Sequence result;
		if (inSequence == null)
			{throw new XPathException(this, ErrorCodes.XPDY0002, "undefined context item");}
        else if (inSequence.isEmpty())
        	{result = Sequence.EMPTY_SEQUENCE;}
        else
        	{result = new IntegerValue(inSequence.getItemCount());}
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;           
		
	}

	/*public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence,
		Item contextItem) throws XPathException {
			if(!Type.subTypeOf(contextItem.getType(), Type.NODE))
				throw new XPathException(this, "last() can only be applied to nodes");
			NodeProxy contextNode = (NodeProxy)contextItem;
	   DocumentImpl doc = contextNode.getDoc();
	   int level = doc.getTreeLevel(contextNode.getGID());
	   long pid = (contextNode.getGID() - doc.getLevelStartPoint(level)) /
	     doc.getTreeLevelOrder(level)
	     + doc.getLevelStartPoint(level - 1);
	   long f_gid = (pid - doc.getLevelStartPoint(level -1)) *
	     doc.getTreeLevelOrder(level) +
	     doc.getLevelStartPoint(level);
	   long e_gid = f_gid + doc.getTreeLevelOrder(level);
	   NodeSet set = ((NodeSet)contextSequence).getRange(doc, f_gid, e_gid);
	   int len = set.getLength();
	   return new IntegerValue(len);
	}*/
}
