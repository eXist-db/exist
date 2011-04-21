/*
 *  eXist Java Cryptographic Extension
 *  Copyright (C) 2010 Claudius Teodorescu at http://kuberam.ro
 *
 *  Released under LGPL License - http://gnu.org/licenses/lgpl.html.
 *
 */
package ro.kuberam.xcrypt;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.exist.xquery.XPathException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class GenerateXMLSignature {
    public static String GenerateDigitalSignature
            ( org.w3c.dom.Document inputDoc,
                String canonicalizationAlgorithmURI,
                String digestAlgorithmURI,
                String signatureAlgorithmURI,
                String keyPairAlgorithm,
                String signatureNamespacePrefix,
                String signatureType,
                final String xpathExprString,
                String[] certificateDetails,
                InputStream keyStoreInputStream) throws Exception {
        // Create a DOM XMLSignatureFactory
        String providerName = System.getProperty("jsr105Provider", "org.jcp.xml.dsig.internal.dom.XMLDSigRI");
	final XMLSignatureFactory sigFactory = XMLSignatureFactory.getInstance("DOM");

        // Create a Reference to the signed element
        Node sigParent = null;
        List transforms = null;

        if ( xpathExprString == null ) {
		sigParent = inputDoc.getDocumentElement();
		transforms = Collections.singletonList( sigFactory.newTransform( Transform.ENVELOPED, (TransformParameterSpec) null ) );
        } else {
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();
            // Find the node to be signed by PATH
            XPathExpression expr = xpath.compile( xpathExprString );
            NodeList nodes = (NodeList) expr.evaluate( inputDoc, XPathConstants.NODESET );
            if ( nodes.getLength() < 1 ) {
                throw new XPathException( "Can't find node by this XPath expression: " + xpathExprString );
            }

            //Node nodeToSign = nodes.item(0);
            //sigParent = nodeToSign.getParentNode();
            sigParent = nodes.item(0);
            /*if ( signatureType.equals( "enveloped" ) ) {
            sigParent = ( nodes.item(0) ).getParentNode();
            }*/
            transforms = new ArrayList<Transform>() {{
                add(sigFactory.newTransform(
                        Transform.XPATH,
                        new XPathFilterParameterSpec( xpathExprString )
                        )
                        );
                add(sigFactory.newTransform(
                        Transform.ENVELOPED,
                        (TransformParameterSpec) null
                        )
                        );
            }};
        }
        
	Reference ref = sigFactory.newReference
	    ( "",
            sigFactory.newDigestMethod(digestAlgorithmURI, null),
            transforms,
	    null,
            null);

	// Create the SignedInfo
	SignedInfo si = sigFactory.newSignedInfo
	    (sigFactory.newCanonicalizationMethod
	     (canonicalizationAlgorithmURI, (C14NMethodParameterSpec)null),
	     sigFactory.newSignatureMethod(signatureAlgorithmURI, null),
	     Collections.singletonList(ref));

        //generate key pair
        KeyInfo ki = null;
        PrivateKey privateKey = null;
        if (certificateDetails[0].length() != 0) {
            KeyStore keyStore = null;
            try {
                keyStore = KeyStore.getInstance( certificateDetails[0] );
            } catch(Exception ex) {
                throw new XPathException( "The keystore type '" + certificateDetails[0] + "' is not supported!." );
            }
            keyStore.load( keyStoreInputStream, certificateDetails[1].toCharArray() );
            String alias = certificateDetails[2];
            if ( !keyStore.containsAlias( alias ) ) {
                throw new XPathException("Cannot find key for alias '" + alias + "' in given keystore!.");
            }
            privateKey = (PrivateKey) keyStore.getKey( alias, certificateDetails[3].toCharArray() );
            X509Certificate cert = (X509Certificate) keyStore.getCertificate( alias );
            PublicKey publicKey = cert.getPublicKey();
            KeyInfoFactory kif = sigFactory.getKeyInfoFactory();
            Vector<Object> kiContent = new Vector<Object>();
            KeyValue keyValue = kif.newKeyValue( publicKey );
            kiContent.add( keyValue );
            List x509Content = new ArrayList();
            X509IssuerSerial issuer = kif.newX509IssuerSerial(cert.getIssuerX500Principal().getName(), cert.getSerialNumber());
            x509Content.add(cert.getSubjectX500Principal().getName());
            x509Content.add(issuer);
            x509Content.add(cert);
            X509Data x509Data = kif.newX509Data(x509Content);
            kiContent.add( x509Data );
            ki = kif.newKeyInfo( kiContent );
        } else {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance( keyPairAlgorithm );
            kpg.initialize(512);
            KeyPair kp = kpg.generateKeyPair();
            KeyInfoFactory kif = sigFactory.getKeyInfoFactory();
            KeyValue kv = kif.newKeyValue(kp.getPublic());
            ki = kif.newKeyInfo(Collections.singletonList(kv));
            privateKey = kp.getPrivate();
        }

        /*<element name="X509Data" type="ds:X509DataType"/>
        <complexType name="X509DataType">
            <sequence maxOccurs="unbounded">
                <choice>
                    SOLVED <element name="X509IssuerSerial" type="ds:X509IssuerSerialType"/>
                    <element name="X509SKI" type="base64Binary"/>
                    SOLVED <element name="X509SubjectName" type="string"/>
                    SOLVED <element name="X509Certificate" type="base64Binary"/>
                    <element name="X509CRL" type="base64Binary"/>
                    <any namespace="##other" processContents="lax"/>
                </choice>
            </sequence>
        </complexType>
>*/

        // Create a DOMSignContext and specify the location of the resulting XMLSignature's parent element
        DOMSignContext dsc = null;
        XMLSignature signature = null;
        Document signatureDoc = null;
        if ( signatureType.equals( "enveloped" ) ) {
            dsc = new DOMSignContext( privateKey, sigParent );
            signature = sigFactory.newXMLSignature(si, ki);
        } else if ( signatureType.equals( "detached" ) ) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            sigParent = dbf.newDocumentBuilder().newDocument();
            dsc = new DOMSignContext( privateKey, sigParent );
            signature = sigFactory.newXMLSignature(si, ki);
        } else if ( signatureType.equals( "enveloping" ) ) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            signatureDoc = dbf.newDocumentBuilder().newDocument();
            XMLStructure content = new DOMStructure( sigParent );
            XMLObject xmlobj = sigFactory.newXMLObject(Collections.singletonList(content), "object", null, null);
            dsc = new DOMSignContext( privateKey, signatureDoc );
            signature = sigFactory.newXMLSignature( si, ki, Collections.singletonList( xmlobj ), null, null );
        }
        dsc.setDefaultNamespacePrefix( signatureNamespacePrefix );

        // Marshal, generate and sign
        signature.sign( dsc );

        DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
        DOMImplementationLS impl = (DOMImplementationLS)registry.getDOMImplementation("LS");
        LSSerializer serializer = impl.createLSSerializer();
        if ( signatureType.equals( "enveloping" ) ) {
            return  serializer.writeToString( signatureDoc );
        } else {
             return serializer.writeToString( sigParent );
        }
    }

    public static void main( String[] args ) throws ParserConfigurationException, SAXException, IOException, Exception {
        String docString = "<data><a xml:id=\"type\"><b>23</b><c><d/></c></a></data>";
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document inputDoc = dbf.newDocumentBuilder().parse( new InputSource(  new StringReader( docString ) ) );

        String[] certificateDetails = new String[5];
        certificateDetails[0] = "JKS";
        certificateDetails[1] = "ab987c";
        certificateDetails[2] = "eXist";
        certificateDetails[3] = "kpi135";

        String domString = GenerateDigitalSignature
                ( inputDoc,
                CanonicalizationMethod.EXCLUSIVE,
                DigestMethod.SHA1,
                SignatureMethod.DSA_SHA1,
                "DSA",
                "ds",
                "enveloped",
                "//b",
                certificateDetails,
                new FileInputStream( "/home/claudius/mykeystoreEXist" ));
        System.out.print( domString + "\n");
    }
}