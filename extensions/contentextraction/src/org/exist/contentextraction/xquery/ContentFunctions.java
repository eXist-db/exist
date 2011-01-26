package org.exist.contentextraction.xquery;

import java.io.IOException;
import org.apache.log4j.Logger;
import org.exist.contentextraction.ContentExtraction;
import org.exist.contentextraction.ContentExtractionException;
import org.exist.dom.QName;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * @author Dulip Withanage <dulip.withanage@gmail.com>
 * @version 1.0
 */
public class ContentFunctions extends BasicFunction {

    @SuppressWarnings("unused")
    private final static Logger logger = Logger.getLogger(ContentFunctions.class);

    public final static FunctionSignature getMeatadata = new FunctionSignature(
        new QName("get-metadata", ContentExtractionModule.NAMESPACE_URI, ContentExtractionModule.PREFIX),
        "extracts the metadata",
        new SequenceType[]{
            new FunctionParameterSequenceType("binary", Type.BASE64_BINARY, Cardinality.ONE, "The binary data to extract from")
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "Extracted metadata")
    );

    public final static FunctionSignature getMetadataAndContent = new FunctionSignature(
        new QName("get-metadata-and-content", ContentExtractionModule.NAMESPACE_URI, ContentExtractionModule.PREFIX),
        "extracts the metadata and contents",
        new SequenceType[]{
            new FunctionParameterSequenceType("binary", Type.BASE64_BINARY, Cardinality.ONE, "The binary data to extract from")
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "Extracted content and metadata")
    );

    public ContentFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        // is argument the empty sequence?
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        
        DocumentBuilderReceiver builder = new DocumentBuilderReceiver();
        ContentExtraction  ce = new ContentExtraction();

        try {
            if(isCalledAs("get-metadata")) {
                ce.extractMetadata((BinaryValue) args[0].itemAt(0), (ContentHandler)builder);
            } else {
                ce.extractContentAndMetadata((BinaryValue) args[0].itemAt(0), (ContentHandler)builder);
            }

            return (NodeValue)builder.getDocument();
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
            throw new XPathException(ex.getMessage(),ex);
        } catch (SAXException ex) {
            LOG.error(ex.getMessage(), ex);
            throw new XPathException(ex.getMessage(),ex);
        } catch (ContentExtractionException ex) {
           LOG.error(ex.getMessage(), ex);
           throw new XPathException(ex.getMessage(),ex);
        }
    }
}
