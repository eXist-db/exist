package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.Sequence;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.DocumentImpl;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Iterator;

public class ExtractDocs extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
			new QName("extract-docs", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns an XML document which describes the functions available in a given module. " +
            "The module is identified through its module namespace URI, which is passed as an argument. " +
            "The function returns a module documentation in XQDoc format.",
			new SequenceType[] {
                    new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE));

    private final String XQDOC_NS = "http://www.xqdoc.org/1.0";

    public ExtractDocs(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String moduleURI = args[0].getStringValue();
        Module module = context.getModule(moduleURI);
        if (module == null)
            return Sequence.EMPTY_SEQUENCE;
        MemTreeBuilder builder = context.getDocumentBuilder();
        int nodeNr = builder.startElement(XQDOC_NS, "xqdoc", "xqdoc", null);
        module(module, builder);
        functions(module, builder);
        builder.endElement();
        return ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
    }

    private void functions(Module module, MemTreeBuilder builder) {
        builder.startElement(XQDOC_NS, "functions", "functions", null);
        FunctionSignature[] functions = module.listFunctions();
        for (int i = 0; i < functions.length; i++) {
            FunctionSignature function = functions[i];
            builder.startElement(XQDOC_NS, "function", "function", null);
            simpleElement(builder, "name", function.getName().getLocalName());
            simpleElement(builder, "signature", function.toString());
            builder.startElement(XQDOC_NS, "comment", "comment", null);
            simpleElement(builder, "description", function.getDescription());
            builder.endElement();
            builder.endElement();
        }
        builder.endElement();
    }

    private void module(Module module, MemTreeBuilder builder) {
        AttributesImpl attribs = new AttributesImpl();
        attribs.addAttribute("", "type", "type", "CDATA", "library");
        builder.startElement(XQDOC_NS, "module", "module", attribs);
        simpleElement(builder, "uri", module.getNamespaceURI());
        builder.startElement(XQDOC_NS, "comment", "comment", null);
        simpleElement(builder, "description", module.getDescription());
        builder.endElement();
        builder.endElement();
    }

    private void simpleElement(MemTreeBuilder builder, String tag, String value) {
        builder.startElement(XQDOC_NS, tag, tag, null);
        builder.characters(value == null ? "" : value);
        builder.endElement();
    }
}
