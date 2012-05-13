package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Created with IntelliJ IDEA.
 * User: wolf
 * Date: 5/12/12
 * Time: 9:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class InspectFunction extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("inspect-function", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns an XML fragment describing the function referenced by the passed function item.",
            new SequenceType[] {
                new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function item to inspect"),
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the signature of the function"));

    private final static QName ARGUMENT_QNAME = new QName("argument");
    private final static QName DEPRECATED_QNAME = new QName("deprecated");
    private final static QName DESCRIPTION_QNAME = new QName("description");
    private final static QName RETURN_QNAME = new QName("returns");
    private final static QName FUNCTION_QNAME = new QName("function");
    private final static QName ANNOTATION_QNAME = new QName("annotation");
    private final static QName ANNOTATION_VALUE_QNAME = new QName("value");

    public InspectFunction(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        FunctionReference ref = (FunctionReference) args[0].itemAt(0);
        FunctionSignature sig = ref.getSignature();

        MemTreeBuilder builder = context.getDocumentBuilder();
        AttributesImpl attribs = new AttributesImpl();
        attribs.addAttribute("", "name", "name", "CDATA", sig.getName().toString());
        attribs.addAttribute("", "module", "module", "CDATA", sig.getName().getNamespaceURI());
        int nodeNr = builder.startElement(FUNCTION_QNAME, attribs);
        writeParameters(sig, builder);
        SequenceType returnType = signature.getReturnType();
        if (returnType != null) {
            attribs.clear();
            attribs.addAttribute("", "type", "type", "CDATA", Type.getTypeName(returnType.getPrimaryType()));
            attribs.addAttribute("", "cardinality", "cardinality", "CDATA", Cardinality.getDescription(returnType.getCardinality()));
            builder.startElement(RETURN_QNAME, attribs);
            builder.endElement();
        }
        writeAnnotations(sig, builder);
        if (sig.getDescription() != null) {
            builder.startElement(DESCRIPTION_QNAME, null);
            builder.characters(sig.getDescription());
            builder.endElement();
        }
        if (sig.isDeprecated()) {
            builder.startElement(DEPRECATED_QNAME, null);
            builder.characters(sig.getDeprecated());
            builder.endElement();
        }
        builder.endElement();
        return ((DocumentImpl)builder.getDocument()).getNode(nodeNr);
    }

    private void writeParameters(FunctionSignature sig, MemTreeBuilder builder) {
        SequenceType[] arguments = sig.getArgumentTypes();
        if (arguments != null) {
            AttributesImpl attribs = new AttributesImpl();
            for (SequenceType type: arguments) {
                attribs.clear();
                attribs.addAttribute("", "type", "type", "CDATA", Type.getTypeName(type.getPrimaryType()));
                attribs.addAttribute("", "cardinality", "cardinality", "CDATA", Cardinality.getDescription(type.getCardinality()));
                builder.startElement(ARGUMENT_QNAME, attribs);
                if (type instanceof FunctionParameterSequenceType) {
                    FunctionParameterSequenceType ftype = (FunctionParameterSequenceType) type;
                    builder.characters(ftype.getAttributeName() + " " + ftype.getDescription());
                }
                builder.endElement();
            }
        }
    }

    private void writeAnnotations(FunctionSignature signature, MemTreeBuilder builder) throws XPathException {
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
