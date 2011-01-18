package ro.kuberam.xcrypt;

import java.io.StringWriter;
import java.util.Properties;
import javax.xml.transform.OutputKeys;
import org.exist.xquery.XPathException;
import org.xml.sax.SAXException;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.SequenceIterator;

public class SerializeXML {
	public static String serialize( SequenceIterator siNode, XQueryContext context ) throws XPathException, SAXException {
            //set the implicit serialization properties
            Properties outputProperties = new Properties();
            outputProperties.setProperty( OutputKeys.INDENT, "no" );
            outputProperties.setProperty( OutputKeys.OMIT_XML_DECLARATION, "yes" );


            // serialize the node set
            StringWriter os = new StringWriter();

            SAXSerializer sax = (SAXSerializer)SerializerPool.getInstance().borrowObject( SAXSerializer.class );
            try {
                sax.setOutput( os, outputProperties );
                Serializer serializer = context.getBroker().getSerializer();
                serializer.reset();
                serializer.setProperties( outputProperties );
                serializer.setReceiver( sax );

                sax.startDocument();
                while( siNode.hasNext() ) {
                    NodeValue next = (NodeValue)siNode.nextItem();
                    serializer.toSAX( next );
                }
                sax.endDocument();
		}
		catch( SAXException e ) {
                    throw( new XPathException( "Cannot serialize the input XML document or node. A problem ocurred while serializing the node set: " + e.getMessage(), e ) );
		}
		finally {
                    SerializerPool.getInstance().returnObject( sax );
		}
                return os.toString();
        }
}
