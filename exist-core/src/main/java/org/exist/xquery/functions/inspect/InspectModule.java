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
package org.exist.xquery.functions.inspect;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.*;
import org.exist.xquery.Module;
import org.exist.xquery.value.*;
import org.exist.xquery.xqdoc.XQDocHelper;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.XMLConstants;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returnsOpt;
import static org.exist.xquery.functions.inspect.InspectionModule.functionSignature;

public class InspectModule extends BasicFunction {

    private static final String FN_INSPECT_MODULE_NAME = "inspect-module";
    private static final FunctionReturnSequenceType RETURN_FRAGMENT = returnsOpt(Type.ELEMENT, "An XML fragment describing the module and all functions contained in it.");
    static final FunctionSignature FN_INSPECT_MODULE = functionSignature(
            FN_INSPECT_MODULE_NAME,
            "Compiles a library module from source (without importing it) and returns an XML fragment describing the " +
                    "module and the functions/variables contained in it.",
            RETURN_FRAGMENT,
            param("location", Type.ANY_URI, "The location URI of the module to inspect")
    );

    private static final String FN_INSPECT_MODULE_URI_NAME = "inspect-module-uri";
    public static final FunctionSignature FN_INSPECT_MODULE_URI = functionSignature(
            FN_INSPECT_MODULE_URI_NAME,
            "Returns an XML fragment describing the " +
                    "library module identified by the given namespace URI and the functions/variables contained in it.",
            RETURN_FRAGMENT,
            param("uri", Type.ANY_URI, "The namespace URI of the module to inspect")
    );


    private static final QName MODULE_QNAME = new QName("module", XMLConstants.NULL_NS_URI);
    private static final QName VARIABLE_QNAME = new QName("variable", XMLConstants.NULL_NS_URI);

    public InspectModule(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        final XQueryContext tempContext = new XQueryContext(context.getBroker().getBrokerPool());
        tempContext.setModuleLoadPath(context.getModuleLoadPath());
        final Module[] modules;
        if (isCalledAs(FN_INSPECT_MODULE_NAME)) {
            modules = tempContext.importModule(null, null, new AnyURIValue[] { (AnyURIValue) args[0].itemAt(0) });
        } else {
            modules = tempContext.importModule(args[0].getStringValue(), null, null);
        }

        if (modules == null || modules.length == 0) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // this function only supports working with a singular module for a namespace!
        final Module module = modules[0];

        try {
            context.pushDocumentContext();
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final AttributesImpl attribs = new AttributesImpl();
            attribs.addAttribute("", "uri", "uri", "CDATA", module.getNamespaceURI());
            attribs.addAttribute("", "prefix", "prefix", "CDATA", module.getDefaultPrefix());
            if (module.isInternalModule()) {
                attribs.addAttribute("", "location", "location", "CDATA", "java:" + module.getClass().getName());
            } else if (isCalledAs("inspect-module")) {
                attribs.addAttribute("", "location", "location", "CDATA", args[0].getStringValue());
            }
            final int nodeNr = builder.startElement(MODULE_QNAME, attribs);
            if (!module.isInternalModule()) {
                XQDocHelper.parse((ExternalModule) module);
            }
            if (module.getDescription() != null) {
                builder.startElement(InspectFunctionHelper.DESCRIPTION_QNAME, null);
                builder.characters(module.getDescription());
                builder.endElement();
            }
            if (!module.isInternalModule()) {
                final ExternalModule externalModule = (ExternalModule) module;
                if (externalModule.getMetadata() != null) {
                    for (final Map.Entry<String, String> entry : externalModule.getMetadata().entrySet()) {
                        builder.startElement(new QName(entry.getKey(), XMLConstants.NULL_NS_URI), null);
                        builder.characters(entry.getValue());
                        builder.endElement();
                    }
                }
                // variables
                for (final VariableDeclaration var : externalModule.getVariableDeclarations()) {
                    attribs.clear();
                    attribs.addAttribute("", "name", "name", "CDATA", var.getName().toString());
                    final SequenceType type = var.getSequenceType();
                    if (type != null) {
                        attribs.addAttribute("", "type", "type", "CDATA", Type.getTypeName(type.getPrimaryType()));
                        attribs.addAttribute("", "cardinality", "cardinality", "CDATA", type.getCardinality().getHumanDescription());
                    }
                    builder.startElement(VARIABLE_QNAME, attribs);
                    builder.endElement();
                }
            }
            // functions
            for (final FunctionSignature sig : module.listFunctions()) {
                if (!sig.isPrivate()) {
                    UserDefinedFunction func = null;
                    if (!module.isInternalModule()) {
                        func = ((ExternalModule) module).getFunction(sig.getName(), sig.getArgumentCount(), null);
                    }
                    InspectFunctionHelper.generateDocs(sig, func, builder);
                }
            }
            builder.endElement();
            return builder.getDocument().getNode(nodeNr);
        } finally {
            context.popDocumentContext();
        }
    }
}
