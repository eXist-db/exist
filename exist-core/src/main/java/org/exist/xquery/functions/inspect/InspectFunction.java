package org.exist.xquery.functions.inspect;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.functions.util.UtilModule;
import org.exist.xquery.xqdoc.XQDocHelper;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.XMLConstants;
import java.util.Map;
import java.util.Set;

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

    protected final static QName ARGUMENT_QNAME = new QName("argument", XMLConstants.NULL_NS_URI);
    protected final static QName DEPRECATED_QNAME = new QName("deprecated", XMLConstants.NULL_NS_URI);
    protected final static QName DESCRIPTION_QNAME = new QName("description", XMLConstants.NULL_NS_URI);
    protected final static QName RETURN_QNAME = new QName("returns", XMLConstants.NULL_NS_URI);
    protected final static QName FUNCTION_QNAME = new QName("function", XMLConstants.NULL_NS_URI);
    protected final static QName ANNOTATION_QNAME = new QName("annotation", XMLConstants.NULL_NS_URI);
    protected final static QName ANNOTATION_VALUE_QNAME = new QName("value", XMLConstants.NULL_NS_URI);
    protected static final QName VERSION_QNAME = new QName("version", XMLConstants.NULL_NS_URI);
    protected static final QName AUTHOR_QNAME = new QName("author", XMLConstants.NULL_NS_URI);
    protected static final QName CALLS_QNAME = new QName("calls", XMLConstants.NULL_NS_URI);

    public InspectFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final FunctionReference ref = (FunctionReference) args[0].itemAt(0);
        final FunctionSignature sig = ref.getSignature();
        final MemTreeBuilder builder = context.getDocumentBuilder();
        final int nodeNr = generateDocs(sig, null, builder);
        return builder.getDocument().getNode(nodeNr);
    }

    /**
     * Generate an XML fragment containing information about the function identified by its signature.
     *
     * @param sig the signature of the function to describe
     * @param func the function implementation. If provided, the method will also inspect the function body
     *             and list all functions called from the current function.
     * @param builder builder used to create the XML
     * @return nodeNr of the generated element
     * @throws XPathException in case of dynamic error
     */
    public static int generateDocs(FunctionSignature sig, UserDefinedFunction func, MemTreeBuilder builder) throws XPathException {
        XQDocHelper.parse(sig);

        final AttributesImpl attribs = new AttributesImpl();
        attribs.addAttribute("", "name", "name", "CDATA", sig.getName().toString());
        attribs.addAttribute("", "module", "module", "CDATA", sig.getName().getNamespaceURI());
        final int nodeNr = builder.startElement(FUNCTION_QNAME, attribs);
        writeParameters(sig, builder);
        final SequenceType returnType = sig.getReturnType();
        if (returnType != null) {
            attribs.clear();
            attribs.addAttribute("", "type", "type", "CDATA", Type.getTypeName(returnType.getPrimaryType()));
            attribs.addAttribute("", "cardinality", "cardinality", "CDATA", Cardinality.getDescription(returnType.getCardinality()));
            builder.startElement(RETURN_QNAME, attribs);
            if (returnType instanceof FunctionReturnSequenceType) {
                final FunctionReturnSequenceType type = (FunctionReturnSequenceType) returnType;
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
        final Map<String, String> metadata = sig.getMetadata();
        if (metadata != null) {
            for (final Map.Entry<String, String> meta : metadata.entrySet()) {
                builder.startElement(new QName(meta.getKey(), XMLConstants.NULL_NS_URI), null);
                builder.characters(meta.getValue());
                builder.endElement();
            }
        }
        if (sig.isDeprecated()) {
            builder.startElement(DEPRECATED_QNAME, null);
            builder.characters(sig.getDeprecated());
            builder.endElement();
        }
        if (func != null) {
            generateDependencies(func, builder);
        }
        builder.endElement();
        return nodeNr;
    }

    private static void writeParameters(FunctionSignature sig, MemTreeBuilder builder) {
        final SequenceType[] arguments = sig.getArgumentTypes();
        if (arguments != null) {
            final AttributesImpl attribs = new AttributesImpl();
            for (final SequenceType type: arguments) {
                attribs.clear();
                attribs.addAttribute("", "type", "type", "CDATA", Type.getTypeName(type.getPrimaryType()));
                attribs.addAttribute("", "cardinality", "cardinality", "CDATA", Cardinality.getDescription(type.getCardinality()));
                if (type instanceof FunctionParameterSequenceType)
                    {attribs.addAttribute("", "var", "var", "CDATA", ((FunctionParameterSequenceType)type).getAttributeName());}
                builder.startElement(ARGUMENT_QNAME, attribs);
                if (type instanceof FunctionParameterSequenceType) {
                    builder.characters(((FunctionParameterSequenceType)type).getDescription());
                }
                builder.endElement();
            }
        }
    }

    private static void writeAnnotations(FunctionSignature signature, MemTreeBuilder builder) throws XPathException {
        final AttributesImpl attribs = new AttributesImpl();
        final Annotation[] annots = signature.getAnnotations();
        if (annots != null) {
            for (final Annotation annot : annots) {
                attribs.clear();
                attribs.addAttribute(null, "name", "name", "CDATA", annot.getName().toString());
                attribs.addAttribute(null, "namespace", "namespace", "CDATA", annot.getName().getNamespaceURI());
                builder.startElement(ANNOTATION_QNAME, attribs);
                final LiteralValue[] value = annot.getValue();
                if (value != null) {
                    for (final LiteralValue literal : value) {
                        builder.startElement(ANNOTATION_VALUE_QNAME, null);
                        builder.characters(literal.getValue().getStringValue());
                        builder.endElement();
                    }
                }
                builder.endElement();
            }
        }
    }

    /**
     * Inspect the provided function implementation and return an XML fragment listing all
     * functions called from the function.
     *
     * @param function the function to inspect
     * @param builder to write output to
     */
    public static void generateDependencies(UserDefinedFunction function, MemTreeBuilder builder) {
        FunctionCallVisitor visitor = new FunctionCallVisitor();
        function.getFunctionBody().accept(visitor);
        Set<FunctionSignature> signatures = visitor.getFunctionCalls();
        if (signatures.size() == 0) {
            return;
        }
        builder.startElement(CALLS_QNAME, null);
        final AttributesImpl attribs = new AttributesImpl();
        for (FunctionSignature signature : signatures) {
            attribs.clear();
            attribs.addAttribute(null, "name", "name", "CDATA", signature.getName().toString());
            attribs.addAttribute("", "module", "module", "CDATA", signature.getName().getNamespaceURI());
            attribs.addAttribute("", "arity", "arity", "CDATA", Integer.toString(signature.getArgumentCount()));

            builder.startElement(FUNCTION_QNAME, attribs);
            builder.endElement();
        }
        builder.endElement();
    }
}
