/*
 *  eXist Java Cryptographic Extension
 *  Copyright (C) 2010 Claudius Teodorescu at http://kuberam.ro
 *  
 *  Released under LGPL License - http://gnu.org/licenses/lgpl.html.
 *  
 */
package ro.kuberam.xcrypt;

import java.net.URISyntaxException;
import org.apache.log4j.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.exist.Namespaces;
import org.exist.dom.BinaryDocument;
import org.exist.memtree.DocumentImpl;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
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
import org.exist.memtree.SAXAdapter;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.validation.internal.node.NodeInputStream;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.XMLReader;

/**
 * @author Claudius Teodorescu (claud108@yahoo.com)
 */
public class GenerateXMLSignatureFunction extends BasicFunction {

	private final static Logger logger = Logger.getLogger(GenerateXMLSignatureFunction.class);

        private final static String digitalCertificateDetailsDescription =
                "Details about the digital certificate to be used for signing the input document." +
                " These details have be passed using an XML fragment with the following structure (this is an example): " +
                "<digital-certificate>" +
                    "<keystore-type>JKS</keystore-type>" +
                    "<keystore-password>ab987c</keystore-password>" +
                    "<key-alias>eXist</key-alias>" +
                    "<private-key-password>kpi135</private-key-password>" +
                    "<keystore-uri>/db/mykeystoreEXist</keystore-uri>" +
                "</digital-certificate>.";

        private final static String certificateRootElementName = "digital-certificate";
        private final static String[] certificateChildElementNames = {"keystore-type", "keystore-password", "key-alias", "private-key-password", "keystore-uri"};

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("generate-signature", XcryptModule.NAMESPACE_URI, XcryptModule.PREFIX),
			"Generate an XML digital signature based on generated key pair. This signature is for the whole document",
			new SequenceType[] {
                            new FunctionParameterSequenceType("input-doc", Type.NODE, Cardinality.EXACTLY_ONE, "The document to be signed."),
                            new FunctionParameterSequenceType("canonicalization-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The canonicalization algorithm applied to the SignedInfo element prior to performing signature calculations. Possible values are: 'exclusive', 'exclusive-with-comments', 'inclusive', and 'inclusive-with-comments'. The default value is 'inclusive-with-comments'."),
                            new FunctionParameterSequenceType("digest-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The digest algorithm to be applied to the signed object. Possible values are: 'SHA1', 'SHA256', and 'SHA512'. The default value is 'SHA1'."),
                            new FunctionParameterSequenceType("signature-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The algorithm used for signature generation and validation. Possible values are: 'DSA_SHA1', and 'RSA_SHA1'. The default value is 'RSA_SHA1'."),
                            new FunctionParameterSequenceType("signature-namespace-prefix", Type.STRING, Cardinality.EXACTLY_ONE, "The default namespace prefix for signature."),
                            new FunctionParameterSequenceType("signature-type", Type.STRING, Cardinality.EXACTLY_ONE, "The method used for signing the content of signature. Possible values are: 'enveloping', 'enveloped', and 'detached'. The default value is 'enveloped'.")
                        },
			new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the signed document (or signature) as node().")),
		new FunctionSignature(
			new QName("generate-signature", XcryptModule.NAMESPACE_URI, XcryptModule.PREFIX),
			"Generate an XML digital signature based on generated key pair. This signature is for node(s) selected using an XPath expression",
			new SequenceType[] {
                            new FunctionParameterSequenceType("input-doc", Type.NODE, Cardinality.EXACTLY_ONE, "The document to be signed."),
                            new FunctionParameterSequenceType("canonicalization-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The canonicalization algorithm applied to the SignedInfo element prior to performing signature calculations. Possible values are: 'exclusive', 'exclusive-with-comments', 'inclusive', and 'inclusive-with-comments'. The default value is 'inclusive-with-comments'."),
                            new FunctionParameterSequenceType("digest-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The digest algorithm to be applied to the signed object. Possible values are: 'SHA1', 'SHA256', and 'SHA512'. The default value is 'SHA1'."),
                            new FunctionParameterSequenceType("signature-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The algorithm used for signature generation and validation. Possible values are: 'DSA_SHA1', and 'RSA_SHA1'. The default value is 'RSA_SHA1'."),
                            new FunctionParameterSequenceType("signature-namespace-prefix", Type.STRING, Cardinality.EXACTLY_ONE, "The default namespace prefix for signature."),
                            new FunctionParameterSequenceType("signature-type", Type.STRING, Cardinality.EXACTLY_ONE, "The method used for signing the content of signature. Possible values are: 'enveloping', 'enveloped', and 'detached'. The default value is 'enveloped'."),
                            new FunctionParameterSequenceType("xpath-expression", Type.ANY_TYPE, Cardinality.EXACTLY_ONE, "The XPath expression used for selecting the node(s) to be signed.")
                        },
			new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the signed document (or signature) as node().")),
		new FunctionSignature(
			new QName("generate-signature", XcryptModule.NAMESPACE_URI, XcryptModule.PREFIX),
			"Generate an XML digital signature based on X.509 certificate.",
			new SequenceType[] {
                            new FunctionParameterSequenceType("input-doc", Type.NODE, Cardinality.EXACTLY_ONE, "The document to be signed."),
                            new FunctionParameterSequenceType("canonicalization-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The canonicalization algorithm applied to the SignedInfo element prior to performing signature calculations. Possible values are: 'exclusive', 'exclusive-with-comments', 'inclusive', and 'inclusive-with-comments'. The default value is 'inclusive-with-comments'."),
                            new FunctionParameterSequenceType("digest-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The digest algorithm to be applied to the signed object. Possible values are: 'SHA1', 'SHA256', and 'SHA512'. The default value is 'SHA1'."),
                            new FunctionParameterSequenceType("signature-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The algorithm used for signature generation and validation. Possible values are: 'DSA_SHA1', and 'RSA_SHA1'. The default value is 'RSA_SHA1'."),
                            new FunctionParameterSequenceType("signature-namespace-prefix", Type.STRING, Cardinality.EXACTLY_ONE, "The default namespace prefix for signature."),
                            new FunctionParameterSequenceType("signature-type", Type.STRING, Cardinality.EXACTLY_ONE, "The method used for signing the content of signature. Possible values are: 'enveloping', 'enveloped', and 'detached'. The default value is 'enveloped'."),
                            new FunctionParameterSequenceType("digital-certificate", Type.ANY_TYPE, Cardinality.ONE, digitalCertificateDetailsDescription)
                        },
			new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the signed document (or signature) as node().")),
		new FunctionSignature(
			new QName("generate-signature", XcryptModule.NAMESPACE_URI, XcryptModule.PREFIX),
			"Generate an XML digital signature based on generated key pair. This signature is for node(s) selected using an XPath expression",
			new SequenceType[] {
                            new FunctionParameterSequenceType("input-doc", Type.NODE, Cardinality.EXACTLY_ONE, "The document to be signed."),
                            new FunctionParameterSequenceType("canonicalization-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The canonicalization algorithm applied to the SignedInfo element prior to performing signature calculations. Possible values are: 'exclusive', 'exclusive-with-comments', 'inclusive', and 'inclusive-with-comments'. The default value is 'inclusive-with-comments'."),
                            new FunctionParameterSequenceType("digest-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The digest algorithm to be applied to the signed object. Possible values are: 'SHA1', 'SHA256', and 'SHA512'. The default value is 'SHA1'."),
                            new FunctionParameterSequenceType("signature-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The algorithm used for signature generation and validation. Possible values are: 'DSA_SHA1', and 'RSA_SHA1'. The default value is 'RSA_SHA1'."),
                            new FunctionParameterSequenceType("signature-namespace-prefix", Type.STRING, Cardinality.EXACTLY_ONE, "The default namespace prefix for signature."),
                            new FunctionParameterSequenceType("signature-type", Type.STRING, Cardinality.EXACTLY_ONE, "The method used for signing the content of signature. Possible values are: 'enveloping', 'enveloped', and 'detached'. The default value is 'enveloped'."),
                            new FunctionParameterSequenceType("xpath-expression", Type.ANY_TYPE, Cardinality.EXACTLY_ONE, "The XPath expression used for selecting the node(s) to be signed."),
                            new FunctionParameterSequenceType("digital-certificate", Type.ANY_TYPE, Cardinality.ONE, digitalCertificateDetailsDescription)
                        },
			new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the signed document (or detached signature) as node()."))
        };

	public GenerateXMLSignatureFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
            //get  and process the input document or node to InputStream, in order to be transformed into DOM Document
            Serializer serializer = context.getBroker().getSerializer();
            NodeValue inputNode = (NodeValue) args[0].itemAt(0);
            InputStream inputNodeStream = new NodeInputStream( serializer, inputNode );

            //get the canonicalization method
            String canonicalizationAlgorithm = args[1].getStringValue();

            //get the canonicalization method URI
            String canonicalizationAlgorithmURI = CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS;
            if ( canonicalizationAlgorithm.equals( "exclusive" ) ) {
                canonicalizationAlgorithmURI = CanonicalizationMethod.EXCLUSIVE;
            } else if ( canonicalizationAlgorithm.equals( "exclusive-with-comments" ) ) {
                canonicalizationAlgorithmURI = CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS;
            } else if ( canonicalizationAlgorithm.equals( "inclusive" ) ) {
                canonicalizationAlgorithmURI = CanonicalizationMethod.INCLUSIVE;
            } else {
                throw new XPathException( this, "This canonicalization algorithm is not supported!" );
            }
            
            //get the digest algorithm
            String digestAlgorithm = args[2].getStringValue();

            //get the digest algorithm URI
            String digestAlgorithmURI = "";
            if ( digestAlgorithm.equals( "SHA256" ) ) {
                digestAlgorithmURI = DigestMethod.SHA256;
            } else if ( digestAlgorithm.equals( "SHA512" ) ) {
                digestAlgorithmURI = DigestMethod.SHA512;
            } else if ( digestAlgorithm.equals( "SHA1" ) || digestAlgorithm.equals( "" ) ) {
                digestAlgorithmURI = DigestMethod.SHA1;
            } else {
                throw new XPathException( this, "This digest algorithm is not supported!" );
            }

            //get the signature algorithm
            String signatureAlgorithm = args[3].getStringValue();
            
            //get the signature algorithm URI
            String signatureAlgorithmURI = "";
            if ( signatureAlgorithm.equals( "DSA_SHA1" ) ) {
                signatureAlgorithmURI = SignatureMethod.DSA_SHA1;
            } else if ( signatureAlgorithm.equals( "RSA_SHA1" ) || signatureAlgorithm.equals( "" ) ) {
                signatureAlgorithmURI = SignatureMethod.RSA_SHA1;
            } else {
                throw new XPathException( this, "This signature algorithm is not supported!" );
            }

            //get the key pair algorithm
            String keyPairAlgorithm = signatureAlgorithm.substring( 0, 3);
            
            //get the signature namespace prefix
            String signatureNamespacePrefix = args[4].getStringValue();

            //get the signature type
            String signatureType = args[5].getStringValue();

            /*if ( ( args.length == 7 || args.length == 8 ) && args[6].getItemType() == Type.STRING ) {
           
            }*/

            //get the XPath expression and/or the certificate's details
            String xpathExprString = null;
            String[] certificateDetails = new String[5];
            certificateDetails[0] = "";
            InputStream keyStoreInputStream = null;
            //function with 7 arguments
            if ( args.length == 7 ) {
                if ( args[6].itemAt(0).getType() == 22 ) {
                    xpathExprString = args[6].getStringValue();
                } else if ( args[6].itemAt(0).getType() == 1 ) {
                    Node certificateDetailsNode = ((NodeValue)args[6].itemAt(0)).getNode();
                    //get the certificate details
                    certificateDetails = getDigitalCertificateDetails( certificateDetails, certificateDetailsNode );
                    //get the keystore InputStream
                    keyStoreInputStream = getKeyStoreInputStream( keyStoreInputStream, certificateDetails[4] );
                }
            }
            //function with 8 arguments
            if ( args.length == 8 ) {
                xpathExprString = args[6].getStringValue();
                Node certificateDetailsNode = ((NodeValue)args[7].itemAt(0)).getNode();
                //get the certificate details
                certificateDetails = getDigitalCertificateDetails( certificateDetails, certificateDetailsNode );
                //get the keystore InputStream
                keyStoreInputStream = getKeyStoreInputStream( keyStoreInputStream, certificateDetails[4] );
            }

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
                inputDOMDoc = db.parse( inputNodeStream );
            } catch (SAXException ex) {
                ex.getMessage();
            } catch (IOException ex) {
                ex.getMessage();
            }

            //sign the document
            String outputString = null;
            try {
                outputString = GenerateXMLSignature.GenerateDigitalSignature
                    (inputDOMDoc,
                    canonicalizationAlgorithmURI,
                    digestAlgorithmURI,
                    signatureAlgorithmURI,
                    keyPairAlgorithm,
                    signatureNamespacePrefix,
                    signatureType,
                    xpathExprString,
                    certificateDetails,
                    keyStoreInputStream);
            } catch (Exception ex) {
                throw new XPathException( ex.getMessage() );
            }

            //process the output (signed) document from string to node()
            SAXAdapter adapter = null;
            try {
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(true);
                SAXParser parser = factory.newSAXParser();
                XMLReader xr = parser.getXMLReader();
                adapter = new SAXAdapter( context );
                xr.setContentHandler(adapter);
                xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
                xr.parse( new InputSource( new StringReader( outputString ) ) );
            } catch (ParserConfigurationException e) {
                throw new XPathException(this, "Error while constructing XML parser: " + e.getMessage(), e);
            } catch (SAXException e) {
                throw new XPathException(this, "Error while parsing XML: " + e.getMessage(), e);
            } catch (IOException e) {
                throw new XPathException(this, "Error while parsing XML: " + e.getMessage(), e);
            }

            return (Sequence) (DocumentImpl) adapter.getDocument();
     }

     private String[] getDigitalCertificateDetails( String[] certificateDetails, Node certificateDetailsNode ) throws XPathException {
         if ( !certificateDetailsNode.getNodeName().equals(certificateRootElementName) ) {
             throw new XPathException(this, "The root element of argument $digital-certificate must have the name 'digital-certificate'.");
         }
         NodeList certificateDetailsNodeList = certificateDetailsNode.getChildNodes();
         for (int i=0, il = certificateDetailsNodeList.getLength(); i<il; i++) {
             Node child = certificateDetailsNodeList.item(i);
             if (child.getNodeName().equals(certificateChildElementNames[i])) {
                 certificateDetails[i] = child.getFirstChild().getNodeValue();
             } else {
                 throw new XPathException(this, "The child element of argument $digital-certificate having position " + (i+1) + " must have the name '" + certificateChildElementNames[i] + "'.");
             }
         }
         return certificateDetails;
     }

     private InputStream getKeyStoreInputStream( InputStream keyStoreInputStream, String keystoreURI ) throws XPathException {
         //get the keystore as InputStream
         org.exist.dom.DocumentImpl keyStoreDoc = null;
         try {
             try {
                 keyStoreDoc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor( keystoreURI ), Lock.READ_LOCK);
                 if ( keyStoreDoc == null ) {
                     throw new XPathException(this, "The keystore located at URL '" + keystoreURI + "' is null'.");
                 }
                 BinaryDocument keyStoreBinaryDoc = (BinaryDocument) keyStoreDoc;
                 try {
                     keyStoreInputStream = context.getBroker().getBinaryResource(keyStoreBinaryDoc);
                 } catch (IOException ex) {
                     logger.error( keystoreURI + ": I/O error while reading resource", ex );
                 }
             } catch (PermissionDeniedException ex) {
                 logger.info( keystoreURI + ": permission denied to read resource", ex);
             }
         } catch (URISyntaxException ex) {
             logger.error("Invalid resource URI", ex);
         }
         return keyStoreInputStream;
     }
}