/*
 *  eXist Java Cryptographic Extension
 *  Copyright (C) 2010 Claudius Teodorescu at http://kuberam.ro
 *
 *  Released under LGPL License - http://gnu.org/licenses/lgpl.html.
 *
 */

package ro.kuberam.xcrypt;

/*
 * @author claudius
 */

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.StringValue;
import javax.crypto.Mac;
import java.security.NoSuchAlgorithmException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import javax.crypto.spec.SecretKeySpec;
import org.exist.util.Base64Encoder;
import org.exist.util.Base64Decoder;


public class HmacFunctions extends BasicFunction {

    private static final String[] supportedHashAlgorithms = { "HmacMD5", "HmacSHA1", "HmacSHA256", "HmacSHA384", "HmacSHA512" };
    private static final String[] supportedResultEncodingMethods = { "hex", "base64" };

    @SuppressWarnings("unused")
    private final static Logger logger = Logger.getLogger(HmacFunctions.class);

    public final static FunctionSignature signatures[] = {
                    new FunctionSignature(
                            new QName("hmac", XcryptModule.NAMESPACE_URI, XcryptModule.PREFIX),
                            "Encrypts the input string.",
                            new SequenceType[] {
                                new FunctionParameterSequenceType("message", Type.STRING, Cardinality.EXACTLY_ONE, "The message to be authenticated."),
                                new FunctionParameterSequenceType("secret-key", Type.STRING, Cardinality.EXACTLY_ONE, "The secret key used for calculating the authentication."),
                                new FunctionParameterSequenceType("algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The cryptographic hasing algorithm. Legal values are 'MD5', 'SHA-1', 'SHA-256', 'SHA-384', and 'SHA-512'."),
                                new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.EXACTLY_ONE, "The method used for encoding of the result. Legal values are 'hex', and 'base64'."),
                            },
                            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "hash-based message authentication code.")
                    ),
                    new FunctionSignature(
                            new QName("hmac", XcryptModule.NAMESPACE_URI, XcryptModule.PREFIX),
                            "Encrypts the input string. Default value for encoding is 'base64'.",
                            new SequenceType[] {
                                new FunctionParameterSequenceType("message", Type.STRING, Cardinality.EXACTLY_ONE, "The message to be authenticated."),
                                new FunctionParameterSequenceType("secret-key", Type.STRING, Cardinality.EXACTLY_ONE, "The secret key used for calculating the authentication."),
                                new FunctionParameterSequenceType("algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The cryptographic hasing algorithm. Legal values are 'MD5', 'SHA-1', 'SHA-256', 'SHA-384', and 'SHA-512'.")
                            },
                            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "hash-based message authentication code.")
                    )
   };
    
   public HmacFunctions(XQueryContext context, FunctionSignature signature) {
       super(context, signature);
   }

    @Override
   public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
       String result = null;
       String hashAlgorithm = "Hmac" + args[2].getStringValue().replaceAll( "-", "" );
       if ( !Arrays.asList( supportedHashAlgorithms ).contains( hashAlgorithm ) ) {
          throw new XPathException( "The XForms hmac() function does not support '" + hashAlgorithm + "' algoritm for hashing");
       }
       String key = args[1].getStringValue();
       String data = args[0].getStringValue();
       String encoding = ( args.length == 3 ) ? "base64" : args[3].getStringValue();
       if ( !Arrays.asList( supportedResultEncodingMethods ).contains( encoding ) ) {
           throw new XPathException( "xforms-compute-exception the XForms hmac() function does not support '" + encoding + "' method for encoding the result of hashing" );
       }

       byte[] encodedKey = null;
       byte[] encodedData = null;
       StringBuffer sb = null;
       Mac mac = null;
       //encoding the key
       try { encodedKey = key.getBytes( "UTF-8" ); } catch (UnsupportedEncodingException ex) {}
       //generating the signing key
       SecretKeySpec signingKey = new SecretKeySpec( encodedKey, hashAlgorithm );
       //get and initialize the Mac instance
       try { mac = Mac.getInstance( hashAlgorithm ); } catch (NoSuchAlgorithmException ex) {}
       try { mac.init( signingKey ); } catch (InvalidKeyException ex) {}
       //encode the data
       try { encodedData = data.getBytes( "UTF-8" ); } catch (UnsupportedEncodingException ex) {}
       //compute the hmac
       byte byteData[] = mac.doFinal( encodedData );
       //get the encoded result
       if ( encoding.equals( "hex" ) ) {
           sb = new StringBuffer();
           for (int i = 0; i < byteData.length; i++) {
               sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
           }
           result = sb.toString();
       } else {
           Base64Encoder enc = new Base64Encoder();
           enc.translate(byteData);
           result = new String(enc.getCharArray());
       }
       return new StringValue(result);
   }
}
//hmac('key', 'abc', 'MD5', 'hex')