package org.exist.contentextraction;

import java.io.IOException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.SAXToReceiver;
import org.exist.xquery.value.BinaryValue;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;



/**
 * @author Dulip Withanage <dulip.withanage@gmail.com>
 * @version 1.0
 */
public class ContentExtraction {

    final Parser parser = new AutoDetectParser();
    final ParseContext parseContext = new ParseContext();

    public ContentExtraction() {
        parseContext.set(Parser.class, parser);
    }

    public void extractContentAndMetadata(BinaryValue binaryValue, ContentHandler contentHandler) throws IOException, SAXException, ContentExtractionException {
        
        Metadata metadata = new Metadata();

        try {
            parser.parse(binaryValue.getInputStream(), contentHandler, metadata, parseContext);
            
        } catch (TikaException e) {
            throw new ContentExtractionException("Problem with content extraction library: " + e.getMessage(), e);
        }
    }

    public void extractContentAndMetadata(BinaryValue binaryValue, Receiver receiver) 
            throws IOException, SAXException, ContentExtractionException {
        
        extractContentAndMetadata(binaryValue, new SAXToReceiver(receiver));
    }

    public void extractMetadata(BinaryValue binaryValue, ContentHandler contentHandler) throws IOException, SAXException, ContentExtractionException {
        Metadata metadata = new Metadata();

        try {
            parser.parse(binaryValue.getInputStream(), 
                    new AbortAfterMetadataContentHandler(contentHandler), metadata, parseContext);
            
        } catch (TikaException e) {
            throw new ContentExtractionException("Problem with content extraction library: " + e.getMessage(), e);
            
        } catch (AbortedAfterMetadataException ame) {
            //do nothing, we have finished with the document
        }
    }
}