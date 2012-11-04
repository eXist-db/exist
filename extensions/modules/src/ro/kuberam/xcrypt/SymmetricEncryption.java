/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ro.kuberam.xcrypt;

import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;
import java.security.*;
import java.util.StringTokenizer;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.exist.xquery.XPathException;


/**
 *
 * @author claudius
 */
public class SymmetricEncryption {

     private static String asHex (byte buf[]) {
      StringBuffer strbuf = new StringBuffer(buf.length * 2);
      int i;

      for (i = 0; i < buf.length; i++) {
       if (((int) buf[i] & 0xff) < 0x10)
	    strbuf.append("0");

       strbuf.append(Long.toString((int) buf[i] & 0xff, 16));
      }

      return strbuf.toString();
     }

     public static String symmetricEncryption(String input, String plainKey, String cryptographicAlgorithm) throws XPathException {
         SecretKeySpec skeySpec = new SecretKeySpec(plainKey.getBytes(), cryptographicAlgorithm);

         // Instantiate the cipher
         Cipher cipher;
         try {
             cipher = Cipher.getInstance(cryptographicAlgorithm);
         } catch (NoSuchAlgorithmException ex) {
             throw new XPathException(ex.getMessage());
         } catch (NoSuchPaddingException ex) {
             throw new XPathException(ex.getMessage());
         }
         try {
             cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
         } catch (InvalidKeyException ex) {
             throw new XPathException(ex.getMessage());
         }

         byte[] encrypted = null;
         try {
             encrypted = cipher.doFinal(input.getBytes());
         } catch (IllegalBlockSizeException ex) {
             throw new XPathException(ex.getMessage());
         } catch (BadPaddingException ex) {
             throw new XPathException(ex.getMessage());
         }

         return getString(encrypted);
     }

     public static String symmetricDecryption(String encryptedInput, String plainKey, String cryptographicAlgorithm) throws XPathException {
         SecretKeySpec skeySpec = new SecretKeySpec(plainKey.getBytes(), cryptographicAlgorithm);
         System.out.println("\tKey length: " + plainKey.length());

         // Instantiate the cipher
         Cipher cipher;
         try {
             cipher = Cipher.getInstance(cryptographicAlgorithm);
         } catch (NoSuchAlgorithmException ex) {
             throw new XPathException(ex.getMessage());
         } catch (NoSuchPaddingException ex) {
             throw new XPathException(ex.getMessage());
         }
         try {
             cipher.init(Cipher.DECRYPT_MODE, skeySpec);
         } catch (InvalidKeyException ex) {
             throw new XPathException(ex.getMessage());
         }

         byte[] decrypted = null;
         try {
             decrypted = cipher.doFinal(getBytes(encryptedInput));
         } catch (IllegalBlockSizeException ex) {
             throw new XPathException(ex.getMessage());
         } catch (BadPaddingException ex) {
             throw new XPathException(ex.getMessage());
         }

         return new String(decrypted);
    }

    public static String getString( byte[] bytes )
      {
        StringBuffer sb = new StringBuffer();
        for( int i=0; i<bytes.length; i++ )
        {
          byte b = bytes[ i ];
          sb.append( ( int )( 0x00FF & b ) );
          if( i+1 <bytes.length )
          {
            sb.append( "-" );
          }
        }
        return sb.toString();
      }

      public static byte[] getBytes( String str )
      {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        StringTokenizer st = new StringTokenizer( str, "-", false );
        while( st.hasMoreTokens() )
        {
          int i = Integer.parseInt( st.nextToken() );
          bos.write( ( byte )i );
        }
        return bos.toByteArray();
      }

    /*public static void main(String[] args) throws Exception {

    // Symmetric encryption with SymmetricEncryption algorithm and 128 key
    System.out.println("Symmetric encryption with AES algorithm and 128 key:");
    System.out.println("\tOriginal string: This is a test!");
    String encryptedString1 = symmetricEncryption("<root xmlns=\"http://kuberam.ro\"><a>This is a really new test!</a></root>", "1234567890123456", "AES");
    System.out.println("\tEncrypted string: " + encryptedString1);
    System.out.println("\tDecrypted string: " + symmetricDecryption(encryptedString1, "1234567890123456", "AES"));

    // Symmetric encryption with Blowfish algorithm and 128 key
    System.out.println("Symmetric encryption with Blowfish algorithm and 128 key:");
    System.out.println("\tOriginal string: This is a test!");
    String encryptedString2 = symmetricEncryption("This is a test!", "1234567890123456", "Blowfish");
    System.out.println("\tEncrypted string: " + encryptedString2);
    System.out.println("\tDecrypted string: " + symmetricDecryption(encryptedString2, "1234567890123456", "Blowfish"));



    }*/

     


}
