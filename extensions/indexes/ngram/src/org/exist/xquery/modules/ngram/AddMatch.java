/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.xquery.modules.ngram;

import java.io.IOException;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramMatch;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.ErrorCodes;

public class AddMatch extends BasicFunction {

	public final static FunctionSignature signature = new FunctionSignature(
    	new QName("add-match", NGramModule.NAMESPACE_URI, NGramModule.PREFIX),
    		"For each of the nodes in the argument sequence, mark the entire first text descendant as a " +
    		"text match, just as if it had been found through a search operation. At serialization time, " +
    		"the text node will be enclosed in an &lt;exist:match&gt; tag, which facilitates further processing " +
    		"by the kwic module or match highlighting. The function is not directly related to the NGram index" +
    		"and works without an index; " +
    		"it just uses the NGram module's match processor.",
            new SequenceType[]{
    			new FunctionParameterSequenceType("node-set", Type.NODE, Cardinality.ZERO_OR_ONE, "The node set")
			},
    		new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "a node set containing nodes that do not have descendent nodes."));
	
	public AddMatch(XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence)
			throws XPathException {
		if (args[0].isEmpty()) {
			return args[0];
		}
		
		final NodeValue nv = (NodeValue) args[0].itemAt(0);
		if (!nv.isPersistentSet()) {
			return nv;
		}
		final NodeProxy node = (NodeProxy) nv;
		final int thisLevel = node.getNodeId().getTreeLevel();
		
		String matchStr = null;
		NodeId nodeId = null;
		try {
			for (final XMLStreamReader reader = context.getBroker().getXMLStreamReader(node, true); reader.hasNext(); ) {
			    final int status = reader.next();
			    if (status == XMLStreamConstants.CHARACTERS) {
			    	matchStr = reader.getText();
			    	nodeId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
			    	break;
			    }

				final NodeId otherId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
				final int otherLevel = otherId.getTreeLevel();

				if (status == XMLStreamConstants.END_ELEMENT && otherLevel == thisLevel) {
					// finished the level...
					break;  // exit-for
				}
			}
		} catch (IOException e) {
			throw new XPathException(this, ErrorCodes.FOER0000, "Exception caught while reading document");
		} catch (XMLStreamException e) {
			throw new XPathException(this, ErrorCodes.FOER0000, "Exception caught while reading document");
		}
		
		if (nodeId != null) {
			Match match = new NGramMatch(getContextId(), node.getNodeId(), matchStr);
			match.addOffset(0, matchStr.length());
			node.addMatch(match);
		}
		return node;
	}
}
