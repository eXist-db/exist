
/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Built-in function fn:last().
 * 
 * @author wolf
 */
public class FunLast extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("last", BUILTIN_FUNCTION_NS),
			null,
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));

	public FunLast(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_ITEM + Dependency.CONTEXT_SET;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if(contextSequence == null || contextSequence.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		return new IntegerValue(contextSequence.getLength());
	}

	/*public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence,
		Item contextItem) throws XPathException {
			if(!Type.subTypeOf(contextItem.getType(), Type.NODE))
				throw new XPathException("last() can only be applied to nodes");
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

	public String pprint() {
		return "last()";
	}
}
