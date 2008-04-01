
package org.exist.xquery.functions;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.exist.Namespaces;
import org.exist.dom.ElementImpl;
import org.exist.dom.QName;
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
        
		Map prefixes = new LinkedHashMap();
		NodeValue nodeValue = (NodeValue) args[0].itemAt(0);		
		if (nodeValue.getImplementationType() == NodeValue.PERSISTENT_NODE) {
			//NodeProxy proxy = (NodeProxy) node;
			Node node = nodeValue.getNode();
			if (context.preserveNamespaces()) {
				//Horrible hacks to work-around bad in-scope NS
				if (node.getNodeType() == Node.ELEMENT_NODE && !context.inheritNamespaces()) {
					collectNamespacePrefixes((ElementImpl)node, prefixes);
				} else {
					do {
						collectNamespacePrefixes((ElementImpl)node, prefixes);
						node = node.getParentNode();
					} while (node != null && node.getNodeType() == Node.ELEMENT_NODE);				
					/*
					NodeSet ancestors = nodeValue.getAncestors(contextId, true);
					for (Iterator i = ancestors.iterator(); i.hasNext(); ) {
						proxy = (NodeProxy) i.next();
						collectNamespacePrefixes((ElementImpl)node, prefixes);
					}
					*/
				}
			} else {
				if (context.inheritNamespaces()) {
					node = node.getParentNode();
					do {
						collectNamespacePrefixes((org.exist.memtree.ElementImpl)node, prefixes);
						node = node.getParentNode();
					} while (node != null && node.getNodeType() == Node.ELEMENT_NODE);					
				}
			}
			// Add xmlNS to all in-memory constructs. /ljo
	        prefixes.put("xml", Namespaces.XML_NS);				
		} else { // In-memory node
			//NodeImpl nodeImpl = (NodeImpl) node;
			Node node = nodeValue.getNode();			
			if (context.preserveNamespaces()) {				
				//Horrible hacks to work-around bad in-scope NS
				if (node.getNodeType() == Node.ELEMENT_NODE && !context.inheritNamespaces()) {
					collectNamespacePrefixes((org.exist.memtree.ElementImpl)node, prefixes);
				} else {				
					do {
						collectNamespacePrefixes((org.exist.memtree.ElementImpl)node, prefixes);
						node = node.getParentNode();
					} while (node != null && node.getNodeType() == Node.ELEMENT_NODE);
				}
			} else {
				if (context.inheritNamespaces()) {
					node = node.getParentNode();
					do {
						collectNamespacePrefixes((org.exist.memtree.ElementImpl)node, prefixes);
						node = node.getParentNode();
					} while (node != null && node.getNodeType() == Node.ELEMENT_NODE);					
				}
			}
	        // Add xmlNS to all in-memory constructs. /ljo
	        prefixes.put("xml", Namespaces.XML_NS);	
	
		}

		ValueSequence result = new ValueSequence();
		String prefix;
		for (Iterator i = prefixes.keySet().iterator(); i.hasNext(); ) {
			prefix = (String) i.next();
			//The predefined namespaces (e.g. "exist" for temporary nodes) could have been removed from the static context
			if (!(context.getURIForPrefix(prefix) == null && 
					("exist".equals(prefix) || "xs".equals(prefix) || "xsi".equals(prefix) ||
						"wdt".equals(prefix) || "fn".equals(prefix) || "local".equals(prefix))))
				result.add(new StringValue(prefix)); 
		}
		
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;          
	}
	
	public static void collectNamespacePrefixes(Element element, Map prefixes) {
		String namespaceURI = element.getNamespaceURI();
		String prefix;
		if (namespaceURI != null && namespaceURI.length() > 0) {
			prefix = element.getPrefix();
			prefixes.put(prefix == null ? "" : prefix, namespaceURI);
		}		
		//TODO : more complicated (see XQTS copynamespace-16)
		if (element instanceof org.exist.memtree.ElementImpl) {
			prefixes.putAll(((org.exist.memtree.ElementImpl)element).getNamespaceMap());
		}		
    }

}