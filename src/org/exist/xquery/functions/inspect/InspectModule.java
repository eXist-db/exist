package org.exist.xquery.functions.inspect;

import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.exist.xquery.xqdoc.XQDocHelper;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;

public class InspectModule extends BasicFunction {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("inspect-module", InspectionModule.NAMESPACE_URI, InspectionModule.PREFIX),
            "Compiles a module from source (without importing it) and returns an XML fragment describing the " +
            "module and the functions/variables contained in it.",
            new SequenceType[] {
                new FunctionParameterSequenceType("location", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                        "The location URI of the module to inspect"),
            },
            new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.ZERO_OR_ONE,
                "An XML fragment describing the module and all functions contained in it.")),
        new FunctionSignature(
            new QName("inspect-module-uri", InspectionModule.NAMESPACE_URI, InspectionModule.PREFIX),
            "Compiles a module from source (without importing it) and returns an XML fragment describing the " +
                    "module and the functions/variables contained in it.",
            new SequenceType[] {
                new FunctionParameterSequenceType("uri", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                    "The location URI of the module to inspect"),
            },
            new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.ZERO_OR_ONE,
                "An XML fragment describing the module and all functions contained in it.")),
    };

    private static final QName MODULE_QNAME = new QName("module");
    private static final QName VARIABLE_QNAME = new QName("variable");

    public InspectModule(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        Module module;
        XQueryContext tempContext = new XQueryContext(context.getBroker().getBrokerPool(), AccessContext.XMLDB);
        tempContext.setModuleLoadPath(context.getModuleLoadPath());
        if (isCalledAs("inspect-module")) {
            module = tempContext.importModule(null, null, args[0].getStringValue());
        } else {
            module = tempContext.importModule(args[0].getStringValue(), null, null);
        }

        if (module == null)
            return Sequence.EMPTY_SEQUENCE;
        MemTreeBuilder builder = context.getDocumentBuilder();
        AttributesImpl attribs = new AttributesImpl();
        attribs.addAttribute("", "uri", "uri", "CDATA", module.getNamespaceURI());
        attribs.addAttribute("", "prefix", "prefix", "CDATA", module.getDefaultPrefix());
        if (isCalledAs("inspect-module"))
            attribs.addAttribute("", "location", "location", "CDATA", args[0].getStringValue());
        else
            attribs.addAttribute("", "location", "location", "CDATA", module.getClass().getName());
        int nodeNr = builder.startElement(MODULE_QNAME, attribs);
        if (!module.isInternalModule())
            XQDocHelper.parse((ExternalModule) module);
        if (module.getDescription() != null) {
            builder.startElement(InspectFunction.DESCRIPTION_QNAME, null);
            builder.characters(module.getDescription());
            builder.endElement();
        }
        if (!module.isInternalModule()) {
            ExternalModule externalModule = (ExternalModule) module;
            if (externalModule.getMetadata() != null) {
                for (Map.Entry<String, String> entry: externalModule.getMetadata().entrySet()) {
                    builder.startElement(new QName(entry.getKey()), null);
                    builder.characters(entry.getValue());
                    builder.endElement();
                }
            }
            // variables
            for (VariableDeclaration var: externalModule.getVariableDeclarations()) {
                attribs.clear();
                attribs.addAttribute("", "name", "name", "CDATA", var.getName());
                SequenceType type = var.getSequenceType();
                if (type != null) {
                    attribs.addAttribute("", "type", "type", "CDATA", Type.getTypeName(type.getPrimaryType()));
                    attribs.addAttribute("", "cardinality", "cardinality", "CDATA", Cardinality.getDescription(type.getCardinality()));
                }
                builder.startElement(VARIABLE_QNAME, attribs);
                builder.endElement();
            }
        }
        // functions
        for (FunctionSignature sig : module.listFunctions()) {
            if (!sig.isPrivate()) {
                InspectFunction.generateDocs(sig, builder);
            }
        }
        builder.endElement();
        return builder.getDocument().getNode(nodeNr);
    }
}
