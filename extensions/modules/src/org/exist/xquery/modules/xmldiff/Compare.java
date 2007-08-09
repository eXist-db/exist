/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
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

		Sequence result = null;
        StringBuffer v1 = new StringBuffer();
        StringBuffer v2 = new StringBuffer(); 
		try {
            if (s1.hasMany()){
                for (int i = 0; i < s1.getItemCount(); i++) {
                    v1.append(serialize((NodeValue) s1.itemAt(i)));
                }
            } else {
                v1.append(serialize((NodeValue) s1.itemAt(0)));
            }
            if (s2.hasMany()) {
                for (int i = 0; i < s2.getItemCount(); i++) {
                    v2.append(serialize((NodeValue) s2.itemAt(i)));
                }
            } else {
                v2.append(serialize((NodeValue) s2.itemAt(0)));
            }

			Diff d = new Diff(v1.toString(), v2.toString());
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
