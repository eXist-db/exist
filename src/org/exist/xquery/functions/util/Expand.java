package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.InMemoryNodeSet;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Option;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: Jul 2, 2008
 * Time: 9:41:24 PM
 * To change this template use File | Settings | File Templates.
 */
public class Expand extends BasicFunction {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("expand", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Creates an in-memory copy of the passed node set, using the specified " +
            "serialization options. By default, full-text match terms will be " +
            "tagged with &lt;exist:match&gt; and XIncludes will be expanded.",
            new SequenceType[]{
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)),
        new FunctionSignature(
            new QName("expand", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Creates an in-memory copy of the passed node set, using the specified " +
            "serialization options. By default, full-text match terms will be " +
            "tagged with &lt;exist:match&gt; and XIncludes will be expanded. Serialization " +
            "parameters can be set in the second argument, which accepts the same parameters " +
            "as the exist:serialize option.",
            new SequenceType[]{
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE))
    };

    public Expand(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;

        // apply serialization options set on the XQuery context
        Properties serializeOptions = new Properties();
        serializeOptions.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
        serializeOptions.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
        if (getArgumentCount() == 4) {
            String serOpts = args[3].getStringValue();
            String[] contents = Option.tokenize(serOpts);
            for (int i = 0; i < contents.length; i++) {
                String[] pair = Option.parseKeyValuePair(contents[i]);
                if (pair == null)
                    throw new XPathException(getASTNode(), "Found invalid serialization option: " + pair);
                LOG.debug("Setting serialization property: " + pair[0] + " = " + pair[1]);
                serializeOptions.setProperty(pair[0], pair[1]);
            }
        } else
            context.checkOptions(serializeOptions);

        context.pushDocumentContext();
        try {
            InMemoryNodeSet result = new InMemoryNodeSet();
            MemTreeBuilder builder = context.getDocumentBuilder();
            DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
            for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
                int nodeNr = builder.getDocument().getLastNode();
                NodeValue next = (NodeValue) i.nextItem();
                next.toSAX(context.getBroker(), receiver, serializeOptions);
                result.add(builder.getDocument().getNode(nodeNr + 1));
            }
            return result;
        } catch (SAXException e) {
            throw new XPathException(getASTNode(), e.getMessage());
        } finally {
            context.popDocumentContext();
        }
    }

    private Properties parseSerializationOptions(SequenceIterator siSerializeParams) throws XPathException
    {
    	//parse serialization options
        Properties outputProperties = new Properties();
        outputProperties.setProperty(OutputKeys.INDENT, "yes");
        outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        while(siSerializeParams.hasNext())
        {
            String opt[] = Option.parseKeyValuePair(siSerializeParams.nextItem().getStringValue());
            outputProperties.setProperty(opt[0], opt[1]);
        }

        return outputProperties;
    }
}
