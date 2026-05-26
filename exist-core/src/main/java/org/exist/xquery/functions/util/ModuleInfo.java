/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.util;

import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.repo.ExistRepository;
import org.exist.source.Source;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import javax.xml.XMLConstants;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

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
			new FunctionReturnSequenceType( Type.ITEM, Cardinality.EMPTY_SEQUENCE, "Returns an empty sequence" ));

	public final static FunctionSignature unmapModuleSig =
		new FunctionSignature(
			new QName("unmap-module", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Remove relation between module namespace and source location. This function is only available to the DBA role.",
			new SequenceType[] { NAMESPACE_URI_PARAMETER },
			new FunctionReturnSequenceType( Type.ITEM, Cardinality.EMPTY_SEQUENCE, "Returns an empty sequence" ));

	public final static FunctionSignature registeredModulesInfoSig =
		new FunctionSignature(
			new QName("registered-modules-info", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a sequence of maps, each containing information about a registered module. " +
			"Each map has keys: 'uri' (namespace URI), 'prefix' (default prefix), and 'source' " +
			"(one of 'built-in', 'package', or 'mapped').",
			null,
			new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE,
				"sequence of maps with keys 'uri', 'prefix', and 'source'"));

	public final static FunctionSignature moduleDescriptionSig =
		new FunctionSignature(
			new QName("get-module-description", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a short description of the module identified by the namespace URI.",
			new SequenceType[] { NAMESPACE_URI_PARAMETER },
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "the description of the active function module identified by the namespace URI"),
				"Use inspect:inspect-module-uri#1 instead!"
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
					"Use inspect:inspect-module-uri#1 instead!"
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

        return switch (getSignature().getName().getLocalPart()) {
            case "get-module-description" -> {
                final String uri = args[0].getStringValue();
                final Module[] modules = context.getModules(uri);
                if (isEmpty(modules)) {
                    throw new XPathException(this, "No module found matching namespace URI: " + uri);
                }
                final Sequence result = new ValueSequence();
                for (final Module module : modules) {
                    result.add(new StringValue(this, module.getDescription()));
                }
                yield result;
            }
            case "is-module-registered" -> {
                final String uri = args[0].getStringValue();
                final Module[] modules = context.getModules(uri);
                yield new BooleanValue(this, modules != null && modules.length > 0);
            }
            case "mapped-modules" -> {
                final ValueSequence resultSeq = new ValueSequence();
                for (final Iterator<String> i = context.getMappedModuleURIs(); i.hasNext(); ) {
                    resultSeq.add(new StringValue(this, i.next()));
                }
                yield resultSeq;
            }
            case "is-module-mapped" -> {
                final String uri = args[0].getStringValue();
                yield new BooleanValue(this, ((Map<String, String>) context.getBroker().getConfiguration().getProperty(XQueryContext.PROPERTY_STATIC_MODULE_MAP)).get(uri) != null);
            }
            case "map-module" -> {
                if (!context.getSubject().hasDbaRole()) {
                    final XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
                    logger.error("Invalid user", xPathException);
                    throw xPathException;
                }
                final String namespace = args[0].getStringValue();
                final String location = args[1].getStringValue();
                final Map<String, String> moduleMap = (Map<String, String>) context.getBroker().getConfiguration().getProperty(XQueryContext.PROPERTY_STATIC_MODULE_MAP);
                moduleMap.put(namespace, location);
                yield Sequence.EMPTY_SEQUENCE;
            }
            case "unmap-module" -> {
                if (!context.getSubject().hasDbaRole()) {
                    final XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
                    logger.error("Invalid user", xPathException);
                    throw xPathException;
                }
                final String namespace = args[0].getStringValue();
                final Map<String, String> moduleMap = (Map<String, String>) context.getBroker().getConfiguration().getProperty(XQueryContext.PROPERTY_STATIC_MODULE_MAP);
                moduleMap.remove(namespace);
                yield Sequence.EMPTY_SEQUENCE;
            }
            case "get-module-info" -> {
                context.pushDocumentContext();

                try {
                    final MemTreeBuilder builder = context.getDocumentBuilder();
                    builder.startElement(MODULES_QNAME, null);

                    if (getArgumentCount() == 1) {
                        final Module[] modules = context.getModules(args[0].getStringValue());
                        if (modules != null) {
                            outputModules(builder, modules);
                        }
                    } else {
                        for (final Iterator<Module> i = context.getRootModules(); i.hasNext(); ) {
                            final Module module = i.next();
                            outputModule(builder, module);
                        }
                    }
                    yield builder.getDocument().getNode(1);
                } finally {
                    context.popDocumentContext();
                }
            }
            case "registered-modules-info" -> evalRegisteredModulesInfo();
            case null, default -> evalRegisteredModules();
        };
	}

	/**
	 * Evaluate util:registered-modules(). Returns the union of all module
	 * namespace URIs visible to the context: Java built-in modules, Java and
	 * XQuery EXPath package modules, and conf.xml-mapped XQuery modules.
	 */
	private Sequence evalRegisteredModules() {
		final ValueSequence resultSeq = new ValueSequence();
		final Set<String> seen = new HashSet<>();
		final XQueryContext tempContext = new XQueryContext(context.getBroker().getBrokerPool());
		try {
			// 1. Java built-in modules
			for (final Iterator<Module> i = tempContext.getRootModules(); i.hasNext(); ) {
				final Module module = i.next();
				final String nsUri = module.getNamespaceURI();
				if (seen.add(nsUri)) {
					resultSeq.add(new StringValue(this, nsUri));
				}
			}
			if (tempContext.getRepository().isPresent()) {
				final ExistRepository repo = tempContext.getRepository().get();
				// 2. Java EXPath package modules
				for (final URI uri : repo.getJavaModules()) {
					final String nsUri = uri.toString();
					if (seen.add(nsUri)) {
						resultSeq.add(new StringValue(this, nsUri));
					}
				}
				// 3. XQuery EXPath package modules
				for (final URI uri : repo.getXQueryModules()) {
					final String nsUri = uri.toString();
					if (seen.add(nsUri)) {
						resultSeq.add(new StringValue(this, nsUri));
					}
				}
			}
			// 4. Conf.xml-mapped XQuery modules
			for (final Iterator<String> i = tempContext.getMappedModuleURIs(); i.hasNext(); ) {
				final String nsUri = i.next();
				if (seen.add(nsUri)) {
					resultSeq.add(new StringValue(this, nsUri));
				}
			}
		} finally {
			tempContext.reset();
			tempContext.runCleanupTasks();
		}
		return resultSeq;
	}

	/**
	 * Evaluate util:registered-modules-info().
	 * Returns a sequence of maps, each with keys "uri", "prefix", and "source".
	 */
	@SuppressWarnings("unchecked")
	private Sequence evalRegisteredModulesInfo() throws XPathException {
		final ValueSequence resultSeq = new ValueSequence();
		final Set<String> seen = new HashSet<>();
		final XQueryContext tempContext = new XQueryContext(context.getBroker().getBrokerPool());
		try {
			// 1. Java built-in modules
			for (final Iterator<Module> i = tempContext.getRootModules(); i.hasNext(); ) {
				final Module module = i.next();
				final String nsUri = module.getNamespaceURI();
				if (seen.add(nsUri)) {
					resultSeq.add(createModuleInfoMap(nsUri, module.getDefaultPrefix(), "built-in"));
				}
			}
			if (tempContext.getRepository().isPresent()) {
				final ExistRepository repo = tempContext.getRepository().get();
				// 2. Java EXPath package modules
				for (final URI uri : repo.getJavaModules()) {
					final String nsUri = uri.toString();
					if (seen.add(nsUri)) {
						// Try to get prefix from loaded module
						final String prefix = getModulePrefix(tempContext, nsUri);
						resultSeq.add(createModuleInfoMap(nsUri, prefix, "package"));
					}
				}
				// 3. XQuery EXPath package modules
				for (final URI uri : repo.getXQueryModules()) {
					final String nsUri = uri.toString();
					if (seen.add(nsUri)) {
						final String prefix = getModulePrefix(tempContext, nsUri);
						resultSeq.add(createModuleInfoMap(nsUri, prefix, "package"));
					}
				}
			}
			// 4. Conf.xml-mapped XQuery modules
			for (final Iterator<String> i = tempContext.getMappedModuleURIs(); i.hasNext(); ) {
				final String nsUri = i.next();
				if (seen.add(nsUri)) {
					final String prefix = getModulePrefix(tempContext, nsUri);
					resultSeq.add(createModuleInfoMap(nsUri, prefix, "mapped"));
				}
			}
		} finally {
			tempContext.reset();
			tempContext.runCleanupTasks();
		}
		return resultSeq;
	}

	/**
	 * Try to determine the default prefix for a module given its namespace URI.
	 */
	private String getModulePrefix(final XQueryContext tempContext, final String namespaceURI) {
		final Module[] modules = tempContext.getRootModules(namespaceURI);
		if (modules != null && modules.length > 0) {
			return modules[0].getDefaultPrefix();
		}
		return "";
	}

	/**
	 * Create a map with keys "uri", "prefix", and "source".
	 */
	private MapType createModuleInfoMap(final String uri, final String prefix, final String source) throws XPathException {
		final MapType map = new MapType(this, context);
		map.add(new StringValue(this, "uri"), new StringValue(this, uri));
		map.add(new StringValue(this, "prefix"), new StringValue(this, prefix));
		map.add(new StringValue(this, "source"), new StringValue(this, source));
		return map;
	}

	private void outputModules(final MemTreeBuilder builder, final Module[] modules) {
		if (modules == null) {
			return;
		}

		for (final Module module : modules) {
			outputModule(builder, module);
		}
	}

	private void outputModule(final MemTreeBuilder builder, final Module module) {
		builder.startElement(MODULE_QNAME, null);
		
		builder.addAttribute(MODULE_URI_ATTR, module.getNamespaceURI());
		builder.addAttribute(MODULE_PREFIX_ATTR, module.getDefaultPrefix());
		if (!module.isInternalModule()) {
			final Source source = ((ExternalModule)module).getSource();
			if (source != null) {
				builder.addAttribute(MODULE_SOURCE_ATTR, source.pathOrContentOrShortIdentifier());
			}
		}
		builder.startElement(MODULE_DESC_QNAME, null);
		builder.characters(module.getDescription());
		builder.endElement(); // <description>
		
		builder.endElement(); // <module>
	}

}
