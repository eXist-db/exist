/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Team *  
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
package org.exist.xquery.modules.xmldiff;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.custommonkey.xmlunit.Diff;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

/**
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class Compare extends Function {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("compare", XmlDiffModule.NAMESPACE_URI,
					XmlDiffModule.PREFIX),
			"Returns the differences between XML documents",
			new SequenceType[] {
					new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE) },
			new SequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE));

	public Compare(XQueryContext context) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
	 *      org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		if (context.getProfiler().isEnabled()) {
			context.getProfiler().start(this);
			context.getProfiler().message(this, Profiler.DEPENDENCIES,
					"DEPENDENCIES",
					Dependency.getDependenciesName(this.getDependencies()));
			if (contextSequence != null)
				context.getProfiler().message(this, Profiler.START_SEQUENCES,
						"CONTEXT SEQUENCE", contextSequence);
			if (contextItem != null)
				context.getProfiler().message(this, Profiler.START_SEQUENCES,
						"CONTEXT ITEM", contextItem.toSequence());
		}

		Expression arg1 = getArgument(0);
		Sequence s1 = arg1.eval(contextSequence, contextItem);
		
		Expression arg2 = getArgument(1);
		context.pushDocumentContext();
		Sequence s2 = arg2.eval(contextSequence, contextItem);
		context.popDocumentContext();
		
		if (s1.isEmpty())
			return BooleanValue.valueOf(s2.isEmpty());
		else if (s2.isEmpty())
			return BooleanValue.valueOf(s1.isEmpty());

		if (s1.hasMany() || s2.hasMany())
			return BooleanValue.valueOf(s1.getItemCount() == s2.getItemCount());

		NodeValue node1 = (NodeValue) s1.itemAt(0);
		NodeValue node2 = (NodeValue) s2.itemAt(0);
        
		Sequence result = null;
		try {
			Diff d = new Diff(serialize(node1), serialize(node2));
			result = new BooleanValue(d.identical());
		} catch (Exception e) {
			throw new XPathException(getASTNode(), "An exception occurred while serializing node " +
					"for comparison: " + e.getMessage(), e);
		}

		if (context.getProfiler().isEnabled())
			context.getProfiler().end(this, "", result);

		return result;
	}
	
	private String serialize(NodeValue node) throws SAXException {
		Serializer serializer = context.getBroker().getSerializer();
		serializer.reset();
		return serializer.serialize(node);
	}
}
