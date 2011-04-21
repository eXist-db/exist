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
import org.exist.xquery.XPathException;
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
	    throw new XPathException("Cannot find Signature element");
	}

	// Create a DOM XMLSignatureFactory that will be used to unmarshal the
	// document containing the XMLSignature
	XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

	// Create a DOMValidateContext and specify a KeyValue KeySelector
        // and document context
	DOMValidateContext valContext = new DOMValidateContext( new KeyValueKeySelector(), nl.item(0) );
        
	// unmarshal the XMLSignature
	XMLSignature signature = fac.unmarshalXMLSignature( valContext );


        System.out.println( "valContext: " + nl.getLength() + "\n" );



	// Validate the XMLSignature (generated above)
	boolean coreValidity = signature.validate( valContext );
        
	// Check core validation status
	if ( coreValidity == false ) {
    	    System.err.println("Signature failed core validation");
	    boolean sv = signature.getSignatureValue().validate( valContext );
	    System.out.println( "signature validation status: " + sv );
	    // check the validation status of each Reference
	    Iterator iterator = signature.getSignedInfo().getReferences().iterator();
	    for ( int j=0; iterator.hasNext(); j++ ) {
		boolean refValid = ((Reference)iterator.next()).validate( valContext );
		System.out.println( "ref["+j+"] validity status: " + refValid );
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
		XMLStructure xmlStructure = (XMLStructure)list.get(i);
            	if (xmlStructure instanceof KeyValue) {
                    PublicKey pk = null;
                    try {
                        pk = ((KeyValue)xmlStructure).getPublicKey();
                    } catch (KeyException ke) {
                        throw new KeySelectorException(ke);
                    }
                    // make sure algorithm is compatible with method
                    if ( algEquals( sm.getAlgorithm(), pk.getAlgorithm() ) ) {
                        return new SimpleKeySelectorResult( pk );
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
	SimpleKeySelectorResult( PublicKey pk ) {
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

                                "<dsig:Transform Algorithm=\"http://www.w3.org/TR/1999/REC-xpath-19991116\">" +
                                    "<dsig:XPath Filter=\"intersect\">/*</dsig:XPath>" +
                                "</dsig:Transform>" +
                                                       
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
                            "<dsig:X509Data><dsig:X509SubjectName>CN=Test Certificate,OU=JavaSoft,O=Sun,C=US</dsig:X509SubjectName><dsig:X509IssuerSerial><dsig:X509IssuerName>CN=Test Certificate,OU=JavaSoft,O=Sun,C=US</dsig:X509IssuerName><dsig:X509SerialNumber>1293552445</dsig:X509SerialNumber></dsig:X509IssuerSerial><dsig:X509Certificate>MIICyzCCAomgAwIBAgIETRoLPTALBgcqhkjOOAQDBQAwSTELMAkGA1UEBhMCVVMxDDAKBgNVBAoTA1N1bjERMA8GA1UECxMISmF2YVNvZnQxGTAXBgNVBAMTEFRlc3QgQ2VydGlmaWNhdGUwHhcNMTAxMjI4MTYwNzI1WhcNMTEwNjI2MTYwNzI1WjBJMQswCQYDVQQGEwJVUzEMMAoGA1UEChMDU3VuMREwDwYDVQQLEwhKYXZhU29mdDEZMBcGA1UEAxMQVGVzdCBDZXJ0aWZpY2F0ZTCCAbcwggEsBgcqhkjOOAQBMIIBHwKBgQD9f1OBHXUSKVLfSpwu7OTn9hG3UjzvRADDHj+AtlEmaUVdQCJR+1k9jVj6v8X1ujD2y5tVbNeBO4AdNG/yZmC3a5lQpaSfn+gEexAiwk+7qdf+t8Yb+DtX58aophUPBPuD9tPFHsMCNVQTWhaRMvZ1864rYdcq7/IiAxmd0UgBxwIVAJdgUI8VIwvMspK5gqLrhAvwWBz1AoGBAPfhoIXWmz3ey7yrXDa4V7l5lK+7+jrqgvlXTAs9B4JnUVlXjrrUWU/mcQcQgYC0SRZxI+hMKBYTt88JMozIpuE8FnqLVHyNKOCjrh4rs6Z1kW6jfwv6ITVi8ftiegEkO8yk8b6oUZCJqIPf4VrlnwaSi2ZegHtVJWQBTDv+z0kqA4GEAAKBgDb4fpsP1kDnlX5gnNu7uR/NPxweuq1+brJ6G3UX1z5fe1Zq+wEM3+Ic3G95fS+VWjWMn1rr0uQafyDhHPqN9yq9qPEftDK97jpYIEpZG0YvMQ94AaSC8cpqQwmTgzu6utNGaBhp8u5+tlA5Qj7uguHLeLBklThU7ESZaaL1bOtLMAsGByqGSM44BAMFAAMvADAsAhQP1O1m6w4ljJw5abm04R4uMexzEwIUN+h7BgZTo0He+mh4mw9E+Q4tnWs=</dsig:X509Certificate></dsig:X509Data>" +
                        "</dsig:KeyInfo>" +
                    "</dsig:Signature>" +
                "</data>";

        docString = "<data><a xml:id=\"type\">17</a><dsig:Signature xmlns:dsig=\"http://www.w3.org/2000/09/xmldsig#\"><dsig:SignedInfo><dsig:CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/><dsig:SignatureMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#dsa-sha1\"/><dsig:Reference URI=\"\"><dsig:Transforms><dsig:Transform Algorithm=\"http://www.w3.org/TR/1999/REC-xpath-19991116\"><dsig:XPath>/*</dsig:XPath></dsig:Transform><dsig:Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"/></dsig:Transforms><dsig:DigestMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha256\"/><dsig:DigestValue>0jkA8y+iwbQf5qhC+XFg+DgwtPTHPB1aUHiziNAMbcA=</dsig:DigestValue></dsig:Reference></dsig:SignedInfo><dsig:SignatureValue>gcmejX13ooJ/Sas4BEGJCRxefARR166O7gGAMFJxExllWRoXE8KJxQ==</dsig:SignatureValue><dsig:KeyInfo><dsig:KeyValue><dsig:DSAKeyValue><dsig:P>/X9TgR11EilS30qcLuzk5/YRt1I870QAwx4/gLZRJmlFXUAiUftZPY1Y+r/F9bow9subVWzXgTuAHTRv8mZgt2uZUKWkn5/oBHsQIsJPu6nX/rfGG/g7V+fGqKYVDwT7g/bTxR7DAjVUE1oWkTL2dfOuK2HXKu/yIgMZndFIAcc=</dsig:P><dsig:Q>l2BQjxUjC8yykrmCouuEC/BYHPU=</dsig:Q><dsig:G>9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSo=</dsig:G><dsig:Y>Nvh+mw/WQOeVfmCc27u5H80/HB66rX5usnobdRfXPl97Vmr7AQzf4hzcb3l9L5VaNYyfWuvS5Bp/IOEc+o33Kr2o8R+0Mr3uOlggSlkbRi8xD3gBpILxympDCZODO7q600ZoGGny7n62UDlCPu6C4ct4sGSVOFTsRJlpovVs60s=</dsig:Y></dsig:DSAKeyValue></dsig:KeyValue></dsig:KeyInfo></dsig:Signature></data>";
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document inputDoc = dbf.newDocumentBuilder().parse( new InputSource(  new StringReader( docString ) ) );

        Boolean isValid = ValidateDigitalSignature
                ( inputDoc
                );
        System.out.print( isValid + "\n");
    }
}

/*
 <data>
    <a>
        <b>23</b>
        <c>
            <d/>
        </c>
    </a>
    <dsig:Signature xmlns:dsig="http://www.w3.org/2000/09/xmldsig#">
        <dsig:SignedInfo>
            <dsig:CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
            <dsig:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#dsa-sha1"/>
            <dsig:Reference URI="">
 *
                <dsig:Transforms>
                    <dsig:Transform Algorithm="http://www.w3.org/TR/1999/REC-xpath-19991116">
                        <dsig:XPath Filter="intersect">/*</dsig:XPath>
                    </dsig:Transform>
                    <dsig:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
                </dsig:Transforms>
 *
                <dsig:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
                <dsig:DigestValue>0jkA8y+iwbQf5qhC+XFg+DgwtPTHPB1aUHiziNAMbcA=</dsig:DigestValue>
            </dsig:Reference>
        </dsig:SignedInfo>
        <dsig:SignatureValue>gcmejX13ooJ/Sas4BEGJCRxefARR166O7gGAMFJxExllWRoXE8KJxQ==</dsig:SignatureValue>
        <dsig:KeyInfo>
            <dsig:KeyValue>
                <dsig:DSAKeyValue>
                    <dsig:P>/X9TgR11EilS30qcLuzk5/YRt1I870QAwx4/gLZRJmlFXUAiUftZPY1Y+r/F9bow9subVWzXgTuAHTRv8mZgt2uZUKWkn5/oBHsQIsJPu6nX/rfGG/g7V+fGqKYVDwT7g/bTxR7DAjVUE1oWkTL2dfOuK2HXKu/yIgMZndFIAcc=</dsig:P>
                    <dsig:Q>l2BQjxUjC8yykrmCouuEC/BYHPU=</dsig:Q>
                    <dsig:G>9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSo=</dsig:G>
                    <dsig:Y>Nvh+mw/WQOeVfmCc27u5H80/HB66rX5usnobdRfXPl97Vmr7AQzf4hzcb3l9L5VaNYyfWuvS5Bp/IOEc+o33Kr2o8R+0Mr3uOlggSlkbRi8xD3gBpILxympDCZODO7q600ZoGGny7n62UDlCPu6C4ct4sGSVOFTsRJlpovVs60s=</dsig:Y>
                </dsig:DSAKeyValue>
            </dsig:KeyValue>
            <dsig:X509Data>
                <dsig:X509SubjectName>CN=Test Certificate,OU=JavaSoft,O=Sun,C=US</dsig:X509SubjectName>
                <dsig:X509IssuerSerial>
                    <dsig:X509IssuerName>CN=Test Certificate,OU=JavaSoft,O=Sun,C=US</dsig:X509IssuerName>
                    <dsig:X509SerialNumber>1293552445</dsig:X509SerialNumber>
                </dsig:X509IssuerSerial>
                <dsig:X509Certificate>MIICyzCCAomgAwIBAgIETRoLPTALBgcqhkjOOAQDBQAwSTELMAkGA1UEBhMCVVMxDDAKBgNVBAoTA1N1bjERMA8GA1UECxMISmF2YVNvZnQxGTAXBgNVBAMTEFRlc3QgQ2VydGlmaWNhdGUwHhcNMTAxMjI4MTYwNzI1WhcNMTEwNjI2MTYwNzI1WjBJMQswCQYDVQQGEwJVUzEMMAoGA1UEChMDU3VuMREwDwYDVQQLEwhKYXZhU29mdDEZMBcGA1UEAxMQVGVzdCBDZXJ0aWZpY2F0ZTCCAbcwggEsBgcqhkjOOAQBMIIBHwKBgQD9f1OBHXUSKVLfSpwu7OTn9hG3UjzvRADDHj+AtlEmaUVdQCJR+1k9jVj6v8X1ujD2y5tVbNeBO4AdNG/yZmC3a5lQpaSfn+gEexAiwk+7qdf+t8Yb+DtX58aophUPBPuD9tPFHsMCNVQTWhaRMvZ1864rYdcq7/IiAxmd0UgBxwIVAJdgUI8VIwvMspK5gqLrhAvwWBz1AoGBAPfhoIXWmz3ey7yrXDa4V7l5lK+7+jrqgvlXTAs9B4JnUVlXjrrUWU/mcQcQgYC0SRZxI+hMKBYTt88JMozIpuE8FnqLVHyNKOCjrh4rs6Z1kW6jfwv6ITVi8ftiegEkO8yk8b6oUZCJqIPf4VrlnwaSi2ZegHtVJWQBTDv+z0kqA4GEAAKBgDb4fpsP1kDnlX5gnNu7uR/NPxweuq1+brJ6G3UX1z5fe1Zq+wEM3+Ic3G95fS+VWjWMn1rr0uQafyDhHPqN9yq9qPEftDK97jpYIEpZG0YvMQ94AaSC8cpqQwmTgzu6utNGaBhp8u5+tlA5Qj7uguHLeLBklThU7ESZaaL1bOtLMAsGByqGSM44BAMFAAMvADAsAhQP1O1m6w4ljJw5abm04R4uMexzEwIUN+h7BgZTo0He+mh4mw9E+Q4tnWs=</dsig:X509Certificate>
            </dsig:X509Data>
        </dsig:KeyInfo>
    </dsig:Signature>
</data>

 */