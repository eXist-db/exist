package org.exist.xquery.modules.ngram;

import java.io.IOException;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramMatch;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
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
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		if (args[0].isEmpty())
			return args[0];
		
		NodeValue nv = (NodeValue) args[0].itemAt(0);
		if (!nv.isPersistentSet())
			return nv;
		NodeProxy node = (NodeProxy) nv;
		
		String matchStr = null;
		NodeId nodeId = null;
		try {
			for (EmbeddedXMLStreamReader reader = context.getBroker().getXMLStreamReader(node, true); reader.hasNext(); ) {
			    int status = reader.next();
			    if (status == XMLStreamConstants.CHARACTERS) {
			    	matchStr = reader.getText();
			    	nodeId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
			    	break;
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
