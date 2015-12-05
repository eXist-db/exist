/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2006-2009 The eXist Project
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.custommonkey.xmlunit.Diff;

import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.util.Properties;

/**
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class Compare extends Function {
	
    private static final Logger logger = LogManager.getLogger(Compare.class);
    private final static Properties OUTPUT_PROPERTIES = new Properties();

    static {
        OUTPUT_PROPERTIES.setProperty(OutputKeys.INDENT, "no");
        OUTPUT_PROPERTIES.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    }
    
    public final static FunctionSignature signature = new FunctionSignature(
			new QName("compare", XmlDiffModule.NAMESPACE_URI,
					XmlDiffModule.PREFIX),
			"Returns true() if the two node sets $node-set-1 and $node-set-2 are equal, otherwise false()",
			new SequenceType[] {
					new FunctionParameterSequenceType("node-set-1", Type.NODE, Cardinality.ZERO_OR_MORE, "the first node set"),
					new FunctionParameterSequenceType("node-set-2", Type.NODE, Cardinality.ZERO_OR_MORE, "the second node set") },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "true() if the two node sets $node-set-1 and $node-set-2 are equal, otherwise false()"));

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
		
	if (s1.isEmpty()) {
	    return BooleanValue.valueOf(s2.isEmpty());
	}
	else if (s2.isEmpty()) {
	    return BooleanValue.valueOf(s1.isEmpty());
	}

	Sequence result = null;
        StringBuilder v1 = new StringBuilder();
        StringBuilder v2 = new StringBuilder();
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
            boolean identical = d.identical();
            if (!identical) {
                logger.warn("Diff result: " + d.toString());
            }
            result = new BooleanValue(identical);
        } catch (Exception e) {
	    throw new XPathException(this, "An exception occurred while serializing node " +
				     "for comparison: " + e.getMessage(), e);
	}

	if (context.getProfiler().isEnabled())
	    context.getProfiler().end(this, "", result);

	return result;
    }
	
    private String serialize(NodeValue node) throws SAXException {
	Serializer serializer = context.getBroker().getSerializer();
        serializer.reset();
        serializer.setProperties(OUTPUT_PROPERTIES);
        return serializer.serialize(node);
    }
}
