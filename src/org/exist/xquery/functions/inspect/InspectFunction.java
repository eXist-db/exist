package org.exist.xquery.functions.inspect;

import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.functions.util.UtilModule;
import org.exist.xquery.xqdoc.XQDocHelper;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Map;

public class InspectFunction extends BasicFunction {

    public final static FunctionSignature SIGNATURE_DEPRECATED =
        new FunctionSignature(
            new QName("inspect-function", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns an XML fragment describing the function referenced by the passed function item.",
            new SequenceType[] {
                new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function item to inspect"),
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the signature of the function"));

    public final static FunctionSignature SIGNATURE =
        new FunctionSignature(
            new QName("inspect-function", InspectionModule.NAMESPACE_URI, InspectionModule.PREFIX),
            "Returns an XML fragment describing the function referenced by the passed function item.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function item to inspect"),
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the signature of the function"));

    protected final static QName ARGUMENT_QNAME = new QName("argument");
    protected final static QName DEPRECATED_QNAME = new QName("deprecated");
    protected final static QName DESCRIPTION_QNAME = new QName("description");
    protected final static QName RETURN_QNAME = new QName("returns");
    protected final static QName FUNCTION_QNAME = new QName("function");
    protected final static QName ANNOTATION_QNAME = new QName("annotation");
    protected final static QName ANNOTATION_VALUE_QNAME = new QName("value");
    protected static final QName VERSION_QNAME = new QName("version");
    protected static final QName AUTHOR_QNAME = new QName("author");

    public InspectFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        FunctionReference ref = (FunctionReference) args[0].itemAt(0);
        FunctionSignature sig = ref.getSignature();
        MemTreeBuilder builder = context.getDocumentBuilder();
        int nodeNr = generateDocs(sig, builder);
        return builder.getDocument().getNode(nodeNr);
    }

    public static int generateDocs(FunctionSignature sig, MemTreeBuilder builder) throws XPathException {
        XQDocHelper.parse(sig);

        AttributesImpl attribs = new AttributesImpl();
        attribs.addAttribute("", "name", "name", "CDATA", sig.getName().toString());
        attribs.addAttribute("", "module", "module", "CDATA", sig.getName().getNamespaceURI());
        int nodeNr = builder.startElement(FUNCTION_QNAME, attribs);
        writeParameters(sig, builder);
        SequenceType returnType = sig.getReturnType();
        if (returnType != null) {
            attribs.clear();
            attribs.addAttribute("", "type", "type", "CDATA", Type.getTypeName(returnType.getPrimaryType()));
            attribs.addAttribute("", "cardinality", "cardinality", "CDATA", Cardinality.getDescription(returnType.getCardinality()));
            builder.startElement(RETURN_QNAME, attribs);
            if (returnType instanceof FunctionReturnSequenceType) {
                FunctionReturnSequenceType type = (FunctionReturnSequenceType) returnType;
                builder.characters(type.getDescription());
            }
            builder.endElement();
        }
        writeAnnotations(sig, builder);
        if (sig.getDescription() != null) {
            builder.startElement(DESCRIPTION_QNAME, null);
            builder.characters(sig.getDescription());
            builder.endElement();
        }
        Map<String, String> metadata = sig.getMetadata();
        if (metadata != null) {
            for (Map.Entry<String, String> meta : metadata.entrySet()) {
                builder.startElement(new QName(meta.getKey()), null);
                builder.characters(meta.getValue());
                builder.endElement();
            }
        }
        if (sig.isDeprecated()) {
            builder.startElement(DEPRECATED_QNAME, null);
            builder.characters(sig.getDeprecated());
            builder.endElement();
        }
        builder.endElement();
        return nodeNr;
    }

    private static void writeParameters(FunctionSignature sig, MemTreeBuilder builder) {
        SequenceType[] arguments = sig.getArgumentTypes();
        if (arguments != null) {
            AttributesImpl attribs = new AttributesImpl();
            for (SequenceType type: arguments) {
                attribs.clear();
                attribs.addAttribute("", "type", "type", "CDATA", Type.getTypeName(type.getPrimaryType()));
                attribs.addAttribute("", "cardinality", "cardinality", "CDATA", Cardinality.getDescription(type.getCardinality()));
                if (type instanceof FunctionParameterSequenceType)
                    attribs.addAttribute("", "var", "var", "CDATA", ((FunctionParameterSequenceType)type).getAttributeName());
                builder.startElement(ARGUMENT_QNAME, attribs);
                if (type instanceof FunctionParameterSequenceType) {
                    builder.characters(((FunctionParameterSequenceType)type).getDescription());
                }
                builder.endElement();
            }
        }
    }

    private static void writeAnnotations(FunctionSignature signature, MemTreeBuilder builder) throws XPathException {
        AttributesImpl attribs = new AttributesImpl();
        Annotation[] annots = signature.getAnnotations();
        if (annots != null) {
            for (Annotation annot : annots) {
                attribs.clear();
                attribs.addAttribute(null, "name", "name", "CDATA", annot.getName().toString());
                attribs.addAttribute(null, "namespace", "namespace", "CDATA", annot.getName().getNamespaceURI());
                builder.startElement(ANNOTATION_QNAME, attribs);
                LiteralValue[] value = annot.getValue();
                if (value != null) {
                    for (LiteralValue literal : value) {
                        builder.startElement(ANNOTATION_VALUE_QNAME, null);
                        builder.characters(literal.getValue().getStringValue());
                        builder.endElement();
                    }
                }
                builder.endElement();
            }
        }
    }
}
