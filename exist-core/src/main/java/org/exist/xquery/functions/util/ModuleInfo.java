/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2015 The eXist-db Project
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
 *
 */
package org.exist.xquery.functions.util;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.source.Source;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.inspect.InspectModule;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import javax.xml.XMLConstants;

/**
 * @author wolf
 */
public class ModuleInfo extends BasicFunction {
	
	protected static final FunctionParameterSequenceType NAMESPACE_URI_PARAMETER = new FunctionParameterSequenceType("namespace-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The namespace URI of the module");
	protected static final FunctionParameterSequenceType LOCATION_URI_PARAMETER = new FunctionParameterSequenceType("location-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The location URI of the module");

	protected static final Logger logger = LogManager.getLogger(ModuleInfo.class);

	public final static FunctionSignature registeredModulesSig =
		new FunctionSignature(
			new QName("registered-modules", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a sequence containing the namespace URIs of all modules " +
			"currently known to the system, including built in and imported modules.",
			null,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "the sequence of all of the active function modules namespace URIs"));
	
	public final static FunctionSignature registeredModuleSig =
		new FunctionSignature(
			new QName("is-module-registered", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a Boolean value if the module identified by the namespace URI is registered.",
			new SequenceType[] { NAMESPACE_URI_PARAMETER },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the namespace URI is registered as an active function module"));

    public final static FunctionSignature mappedModulesSig =
		new FunctionSignature(
			new QName("mapped-modules", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a sequence containing the namespace URIs of all XQuery modules " +
			"which are statically mapped to a source location in the configuration file. " +
            "This does not include any built in modules.",
			null,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "the sequence of all of the active function modules namespace URIs"));

	public final static FunctionSignature mappedModuleSig =
		new FunctionSignature(
			new QName("is-module-mapped", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a Boolean value if the module statically mapped to a source location in the configuration file.",
			new SequenceType[] { NAMESPACE_URI_PARAMETER },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the namespace URI is mapped as an active function module"));

	public final static FunctionSignature mapModuleSig =
		new FunctionSignature(
			new QName("map-module", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Map the module to a source location. This function is only available to the DBA role.",
			new SequenceType[] { NAMESPACE_URI_PARAMETER, LOCATION_URI_PARAMETER },
			new FunctionReturnSequenceType( Type.ITEM, Cardinality.EMPTY, "Returns an empty sequence" ));

	public final static FunctionSignature unmapModuleSig =
		new FunctionSignature(
			new QName("unmap-module", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Remove relation between module namespace and source location. This function is only available to the DBA role.",
			new SequenceType[] { NAMESPACE_URI_PARAMETER },
			new FunctionReturnSequenceType( Type.ITEM, Cardinality.EMPTY, "Returns an empty sequence" ));

	public final static FunctionSignature moduleDescriptionSig =
		new FunctionSignature(
			new QName("get-module-description", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a short description of the module identified by the namespace URI.",
			new SequenceType[] { NAMESPACE_URI_PARAMETER },
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the description of the active function module identified by the namespace URI"),
                        InspectModule.FNS_INSPECT_MODULE_URI
        );
	
	public final static FunctionSignature moduleInfoSig =
		new FunctionSignature(
			new QName("get-module-info", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns an XML fragment providing additional information about the module identified by the " +
			"namespace URI.",
			null,
			new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, 
					"the description of the active function module identified by the namespace URI")
        );
	
	public final static FunctionSignature moduleInfoWithURISig =
            new FunctionSignature(
                new QName("get-module-info", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                "Returns an XML fragment providing additional information about the module identified by the " +
                "namespace URI.",
                new SequenceType[] { NAMESPACE_URI_PARAMETER },
                new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, 
                "the description of the active function module identified by the namespace URI"),
                InspectModule.FNS_INSPECT_MODULE_URI
        );
	
	private static final QName MODULE_QNAME = new QName("module", XMLConstants.NULL_NS_URI);
	private static final QName MODULE_URI_ATTR = new QName("uri", XMLConstants.NULL_NS_URI);
	private static final QName MODULE_PREFIX_ATTR = new QName("prefix", XMLConstants.NULL_NS_URI);
	private static final QName MODULE_SOURCE_ATTR = new QName("source", XMLConstants.NULL_NS_URI);
	private static final QName MODULE_DESC_QNAME = new QName("description", XMLConstants.NULL_NS_URI);
	private static final QName MODULES_QNAME = new QName("modules", XMLConstants.NULL_NS_URI);

	public ModuleInfo(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	@SuppressWarnings("unchecked")
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
		if("get-module-description".equals(getSignature().getName().getLocalPart())) {
			final String uri = args[0].getStringValue();
			final Module module = context.getModule(uri);
			if(module == null)
				{throw new XPathException(this, "No module found matching namespace URI: " + uri);}
			return new StringValue(module.getDescription());
		} else if ("is-module-registered".equals(getSignature().getName().getLocalPart())) {
			final String uri = args[0].getStringValue();
			final Module module = context.getModule(uri);
			return new BooleanValue(module != null);
        } else if ("mapped-modules".equals(getSignature().getName().getLocalPart())) {
            final ValueSequence resultSeq = new ValueSequence();
            for (final Iterator<String> i = context.getMappedModuleURIs(); i.hasNext();) {
                resultSeq.add(new StringValue(i.next()));
            }
            return resultSeq;
		} else if ("is-module-mapped".equals(getSignature().getName().getLocalPart())) {
			final String uri = args[0].getStringValue();
			return new BooleanValue(((Map<String, String>)context.getBroker().getConfiguration().getProperty(XQueryContext.PROPERTY_STATIC_MODULE_MAP)).get(uri) != null);
		} else if ("map-module".equals(getSignature().getName().getLocalPart())) {
			if (!context.getSubject().hasDbaRole()) {
				final XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
				logger.error("Invalid user", xPathException);
				throw xPathException;
			}			
			final String namespace = args[0].getStringValue();
			final String location = args[1].getStringValue();
			final Map <String, String> moduleMap = (Map<String, String>)context.getBroker().getConfiguration().getProperty(XQueryContext.PROPERTY_STATIC_MODULE_MAP);
			moduleMap.put(namespace, location);
			return Sequence.EMPTY_SEQUENCE;
		} else if ("unmap-module".equals(getSignature().getName().getLocalPart())) {
			if (!context.getSubject().hasDbaRole()) {
				final XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
				logger.error("Invalid user", xPathException);
				throw xPathException;
			}			
			final String namespace = args[0].getStringValue();
			final Map <String, String> moduleMap = (Map<String, String>)context.getBroker().getConfiguration().getProperty(XQueryContext.PROPERTY_STATIC_MODULE_MAP);
			moduleMap.remove(namespace);
			return Sequence.EMPTY_SEQUENCE;
		} else if ("get-module-info".equals(getSignature().getName().getLocalPart())) {
			context.pushDocumentContext();
			
			try {
				final MemTreeBuilder builder = context.getDocumentBuilder();
				builder.startElement(MODULES_QNAME, null);
				
				if (getArgumentCount() == 1) {
					final Module module = context.getModule(args[0].getStringValue());
					if (module != null)
						{outputModule(builder, module);}
				} else {
					for(final Iterator<Module> i = context.getRootModules(); i.hasNext(); ) {
						final Module module = i.next();
						outputModule(builder, module);
					}
				}
				return builder.getDocument().getNode(1);
			} finally {
				context.popDocumentContext();
			}
		} else {
			final ValueSequence resultSeq = new ValueSequence();
            final XQueryContext tempContext = new XQueryContext(context.getBroker().getBrokerPool());
			for(final Iterator<Module> i = tempContext.getRootModules(); i.hasNext(); ) {
				final Module module = i.next();
				resultSeq.add(new StringValue(module.getNamespaceURI()));
			}
			if (tempContext.getRepository().isPresent()) {
			    for (final URI uri : tempContext.getRepository().get().getJavaModules()) {
				resultSeq.add(new StringValue(uri.toString()));
			    }
			}
			return resultSeq;
		}
	}

	private void outputModule(MemTreeBuilder builder, Module module) {
		builder.startElement(MODULE_QNAME, null);
		
		builder.addAttribute(MODULE_URI_ATTR, module.getNamespaceURI());
		builder.addAttribute(MODULE_PREFIX_ATTR, module.getDefaultPrefix());
		if (!module.isInternalModule()) {
			final Source source = ((ExternalModule)module).getSource();
			if (source != null)
				{builder.addAttribute(MODULE_SOURCE_ATTR, source.getKey().toString());}
		}
		builder.startElement(MODULE_DESC_QNAME, null);
		builder.characters(module.getDescription());
		builder.endElement(); // <description>
		
		builder.endElement(); // <module>
	}

}
