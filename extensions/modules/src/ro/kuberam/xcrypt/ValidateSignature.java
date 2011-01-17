/*
 *  eXist Java Cryptographic Extension
 *  Copyright (C) 2010 Claudius Teodorescu at http://kuberam.ro
 *
 *  Released under LGPL License - http://gnu.org/licenses/lgpl.html.
 *
 */
package ro.kuberam.xcrypt;

import java.io.StringReader;
import java.security.Key;
import java.security.KeyException;
import java.security.PublicKey;
import java.util.Iterator;
import java.util.List;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 *
 * @author claudius
 */
public class ValidateSignature {

    public static Boolean ValidateDigitalSignature( org.w3c.dom.Document inputDoc ) throws Exception {

	// Find Signature element
	NodeList nl = inputDoc.getElementsByTagNameNS( XMLSignature.XMLNS, "Signature" );
	if ( nl.getLength() == 0 ) {
	    throw new Exception("Cannot find Signature element");
	}

	// Create a DOM XMLSignatureFactory that will be used to unmarshal the
	// document containing the XMLSignature
	XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

	// Create a DOMValidateContext and specify a KeyValue KeySelector
        // and document context
	DOMValidateContext valContext = new DOMValidateContext(new KeyValueKeySelector(), nl.item(0));

	// unmarshal the XMLSignature
	XMLSignature signature = fac.unmarshalXMLSignature(valContext);

	// Validate the XMLSignature (generated above)
	boolean coreValidity = signature.validate(valContext);

	// Check core validation status
	if (coreValidity == false) {
    	    System.err.println("Signature failed core validation");
	    boolean sv = signature.getSignatureValue().validate(valContext);
	    System.out.println("signature validation status: " + sv);
	    // check the validation status of each Reference
	    Iterator i = signature.getSignedInfo().getReferences().iterator();
	    for (int j=0; i.hasNext(); j++) {
		boolean refValid = ((Reference) i.next()).validate(valContext);
		System.out.println("ref["+j+"] validity status: " + refValid);
	    }
	}
        
        return coreValidity;
    }

    /**
     * KeySelector which retrieves the public key out of the
     * KeyValue element and returns it.
     * NOTE: If the key algorithm doesn't match signature algorithm,
     * then the public key will be ignored.
     */
    private static class KeyValueKeySelector extends KeySelector {
	public KeySelectorResult select(KeyInfo keyInfo,
                                        KeySelector.Purpose purpose,
                                        AlgorithmMethod method,
                                        XMLCryptoContext context)
            throws KeySelectorException {
            if (keyInfo == null) {
		throw new KeySelectorException("Null KeyInfo object!");
            }
            SignatureMethod sm = (SignatureMethod) method;
            List list = keyInfo.getContent();

            for (int i = 0; i < list.size(); i++) {
		XMLStructure xmlStructure = (XMLStructure) list.get(i);
            	if (xmlStructure instanceof KeyValue) {
                    PublicKey pk = null;
                    try {
                        pk = ((KeyValue)xmlStructure).getPublicKey();
                    } catch (KeyException ke) {
                        throw new KeySelectorException(ke);
                    }
                    // make sure algorithm is compatible with method
                    if (algEquals(sm.getAlgorithm(), pk.getAlgorithm())) {
                        return new SimpleKeySelectorResult(pk);
                    }
		}
            }
            throw new KeySelectorException("No KeyValue element found!");
	}

	static boolean algEquals(String algURI, String algName) {
            if ( algName.equalsIgnoreCase("DSA") && algURI.equalsIgnoreCase( SignatureMethod.DSA_SHA1 ) ) {
		return true;
            } else if ( algName.equalsIgnoreCase("RSA") && algURI.equalsIgnoreCase(SignatureMethod.RSA_SHA1) ) {
		return true;
            } else {
		return false;
            }
	}
    }

    private static class SimpleKeySelectorResult implements KeySelectorResult {
	private PublicKey pk;
	SimpleKeySelectorResult(PublicKey pk) {
	    this.pk = pk;
	}
        public Key getKey() { return pk; }
    }

    public static void main( String[] args ) throws Exception {
        String docString = 
                "<data>" +
                    "<a xml:id=\"type\">17</a>" +
                    "<dsig:Signature xmlns:dsig=\"http://www.w3.org/2000/09/xmldsig#\">" +
                        "<dsig:SignedInfo>" +
                            "<dsig:CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/>" +
                            "<dsig:SignatureMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#dsa-sha1\"/>" +
                            "<dsig:Reference URI=\"\">" +
                                "<dsig:Transforms>" +
                                    "<dsig:Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"/>" +
                                "</dsig:Transforms>" +
                                "<dsig:DigestMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#sha1\"/>" +
                                "<dsig:DigestValue>YHmHtPDiBQLusVhIZECdZ60C9vA=</dsig:DigestValue>" +
                            "</dsig:Reference>" +
                        "</dsig:SignedInfo>" +
                        "<dsig:SignatureValue>dfpLi1wje1gAOgclv91hkJ2GupQgkIeth1IVQuCmDvMDy6C2U+XYDA==</dsig:SignatureValue>" +
                        "<dsig:KeyInfo>" +
                            "<dsig:KeyValue>" +
                                "<dsig:DSAKeyValue>" +
                                    "<dsig:P>/KaCzo4Syrom78z3EQ5SbbB4sF7ey80etKII864WF64B81uRpH5t9jQTxeEu0ImbzRMqzVDZkVG9xD7nN1kuFw==</dsig:P>" +
                                    "<dsig:Q>li7dzDacuo67Jg7mtqEm2TRuOMU=</dsig:Q>" +
                                    "<dsig:G>Z4Rxsnqc9E7pGknFFH2xqaryRPBaQ01khpMdLRQnG541Awtx/XPaF5Bpsy4pNWMOHCBiNU0NogpsQW5QvnlMpA==</dsig:G>" +
                                    "<dsig:Y>A7M8Zg/MvIPQyRECctmsr+GpqjMGLgWDG9Bmz4DWIDJqzHjE1kGpkrmuhE1sOr8KYGBfj+zVP310fAhCR2+mxg==</dsig:Y>" +
                                "</dsig:DSAKeyValue>" +
                            "</dsig:KeyValue>" +
                        "</dsig:KeyInfo>" +
                    "</dsig:Signature>" +
                "</data>";
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document inputDoc = dbf.newDocumentBuilder().parse( new InputSource(  new StringReader( docString ) ) );

        Boolean isValid = ValidateDigitalSignature
                ( inputDoc
                );
        System.out.print( isValid + "\n");
    }
}
