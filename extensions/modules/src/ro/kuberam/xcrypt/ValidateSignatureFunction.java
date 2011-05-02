/*
 *  eXist Java Cryptographic Extension
 *  Copyright (C) 2010 Claudius Teodorescu at http://kuberam.ro
 *  
 *  Released under LGPL License - http://gnu.org/licenses/lgpl.html.
 *  
 */
package ro.kuberam.xcrypt;

import java.util.logging.Level;
import org.apache.log4j.Logger;
import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.NodeValue;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * Cryptographic extension functions.
 * 
 * @author Claudius Teodorescu (claud108@yahoo.com)
 */
public class ValidateSignatureFunction extends BasicFunction {

	private final static Logger logger = Logger.getLogger(ValidateSignatureFunction.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("validate-signature", XcryptModule.NAMESPACE_URI, XcryptModule.PREFIX),
			"Validate an XML digital signature.",
			new SequenceType[] {
                            new FunctionParameterSequenceType("input-doc", Type.NODE, Cardinality.EXACTLY_ONE, "The enveloped, enveloping, or detached signature.")                        },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "boolean value true() if the signature is valid, otherwise return value false()."));

	public ValidateSignatureFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	protected final static Properties defaultOutputKeysProperties = new Properties();

	static {
		defaultOutputKeysProperties.setProperty(OutputKeys.INDENT, "no");
		defaultOutputKeysProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		defaultOutputKeysProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
            if( args[0].isEmpty() ){
                return Sequence.EMPTY_SEQUENCE;
            }

            String inputNodeSerialized = "";
            try {
                inputNodeSerialized = SerializeXML.serialize( args[0].iterate(), context );
            } catch (SAXException ex) {
                throw new XPathException( ex.getMessage() );
            }


            //get  and process the input document or node to InputStream, in order to be transformed into DOM Document
            Serializer serializer = context.getBroker().getSerializer();
            serializer.reset();
            Properties outputProperties = new Properties( defaultOutputKeysProperties );
            try {
                serializer.setProperties(outputProperties);
            } catch (SAXNotRecognizedException ex) {
            java.util.logging.Logger.getLogger(ValidateSignatureFunction.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SAXNotSupportedException ex) {
            java.util.logging.Logger.getLogger(ValidateSignatureFunction.class.getName()).log(Level.SEVERE, null, ex);
            }
            //NodeValue inputNode = (NodeValue)args[0].itemAt(0);
            //InputStream inputNodeStream = new NodeInputStream( serializer, inputNode );




            //System.out.println( "inputNodeSerialized: " + inputNodeSerialized + "\n" );

            /*try {
            System.out.println("inputDOMDoc for validating: " + serializer.serialize( (NodeValue)args[0].itemAt(0) ) + "\n");
            } catch (SAXException ex) {}*/

            //initialize the document builder
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware( true );
            DocumentBuilder db = null;
            try {
                db = dbf.newDocumentBuilder();
            } catch (ParserConfigurationException ex) {}

            //process the input string to DOM document
            org.w3c.dom.Document inputDOMDoc = null;
            try {
                //inputDOMDoc = db.parse( inputNodeStream );
                //inputDOMDoc = db.parse(new InputSource(new StringReader( inputNodeSerialized )));
                inputDOMDoc = db.parse( new InputSource( new StringReader( serializer.serialize( (NodeValue)args[0].itemAt(0) ) ) ) );
            } catch (SAXException ex) {
                ex.getMessage();
            } catch (IOException ex) {
                ex.getMessage();
            }

            //validate the document
            Boolean isValid = false;
            try {
                isValid = ValidateSignature.ValidateDigitalSignature( inputDOMDoc );
            } catch (Exception ex) {
                throw new XPathException( ex.getMessage() );
            }

            return new BooleanValue( isValid );
     }
}