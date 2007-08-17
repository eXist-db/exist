
package org.exist.xquery.functions;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.exist.Namespaces;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.memtree.NodeImpl;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class FunInScopePrefixes extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("in-scope-prefixes", Function.BUILTIN_FUNCTION_NS),
			"Returns the prefixes of the in-scope namespaces for $a. For namespaces that have " +
			"a prefix, it returns the prefix as an xs:NCName. For the default namespace, which has " +
			"no prefix, it returns the zero-length string.",
			new SequenceType[] { new SequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE) },
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));
	
	public FunInScopePrefixes(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)	throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
        }
        
		Map prefixes = new HashMap();
		NodeValue node = (NodeValue) args[0].itemAt(0);
		if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
			NodeProxy proxy = (NodeProxy) node;
			NodeSet ancestors = proxy.getAncestors(contextId, true);
			for (Iterator i = ancestors.iterator(); i.hasNext(); ) {
				proxy = (NodeProxy) i.next();
				collectNamespacePrefixes((ElementImpl) proxy.getNode(), prefixes);
			}
		} else { // In-memory node
			NodeImpl next = (NodeImpl) node;
            // Add xmlNS to all in-memory construcs. /ljo
            prefixes.put("xml", Namespaces.XML_NS);
			do {
				collectNamespacePrefixes((org.exist.memtree.ElementImpl) next, prefixes);
				next = (NodeImpl) next.getParentNode();
			} while (next != null && next.getNodeType() == Node.ELEMENT_NODE);
		}
		
		ValueSequence result = new ValueSequence();
		String prefix;
		for (Iterator i = prefixes.keySet().iterator(); i.hasNext(); ) {
			prefix = (String) i.next();
			result.add(new StringValue(prefix));
		}
		
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;          
	}

	public static void collectNamespacePrefixes(ElementImpl element, Map prefixes) {
		String namespaceURI = element.getNamespaceURI();
		String prefix;
        if (namespaceURI != null && namespaceURI.length() > 0) {
			prefix = element.getPrefix();
			prefixes.put(prefix == null ? "" : prefix, namespaceURI);
		}
		if (element.declaresNamespacePrefixes()) {
			for (Iterator i = element.getPrefixes(); i.hasNext(); ) {
				prefix = (String) i.next();
				prefixes.put(prefix, element.getNamespaceForPrefix(prefix));
			}
		}
	}
	
	public static void collectNamespacePrefixes(Element element, Map prefixes) {
		String namespaceURI = element.getNamespaceURI();
		String prefix;
        System.out.println("FunInScopePrefixes::collectNamespacePrefixes Element 1:" + element+ "/" + namespaceURI);
		if (namespaceURI != null && namespaceURI.length() > 0) {
			prefix = element.getPrefix();
            System.out.println("FunInScopePrefixes::collectNamespacePrefixes Element 2:" + element+ "/" + prefix);
			prefixes.put(prefix == null ? "" : prefix, namespaceURI);
		}
    }

	public static void collectNamespacePrefixes(org.exist.memtree.ElementImpl element, Map prefixes) {
		String namespaceURI = element.getNamespaceURI();
		String prefix;
		if (namespaceURI != null && namespaceURI.length() > 0) {
			prefix = element.getPrefix();
			prefixes.put(prefix == null ? "" : prefix, namespaceURI);
		}
		if (element.declaresNamespacePrefixes()) {
            prefixes.putAll(element.getNamespaceMap());
        } 
    }
}