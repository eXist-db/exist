
package org.exist.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.log4j.Logger;

import org.exist.util.Base64Encoder;


public class MessageDigester {

    private static String[] hex = {"0", "1", "2", "3", "4", "5", "6", "7",
            "8", "9", "a", "b", "c", "d", "e", "f"};

    private static final Logger LOG = Logger.getLogger(MessageDigester.class);

    public static String md5( String message, boolean base64) {
        MessageDigest md5 = null;
        String digest = message;
        try {
            md5 = MessageDigest.getInstance( "MD5" );
            md5.update( message.getBytes() );
            byte[] digestData = md5.digest();
            
            if(base64)
            {
            	Base64Encoder enc = new Base64Encoder();
            	enc.translate(digestData);
            	digest = new String(enc.getCharArray());
            }
            else
            {
               digest = byteArrayToHex( digestData );
            }
        } catch ( NoSuchAlgorithmException e ) {
            LOG.warn( "MD5 not supported. Using plain string as password!" );
        } catch ( Exception e ) {
            LOG.warn( "Digest creation failed. Using plain string as password!" );
        }
        return digest;
    }

    public static String calculate(String message, String algorithm, boolean base64)
    throws IllegalArgumentException {

        // Can throw a  NoSuchAlgorithmException
        MessageDigest  md = null;
        try {
            md = MessageDigest.getInstance(algorithm);

        } catch (NoSuchAlgorithmException e) {
            String error = "'"+ algorithm + "' is not a supported MessageDigest algorithm.";
            LOG.error(error, e);
            throw new IllegalArgumentException(error);
        }


        // Calculate hash
        md.update( message.getBytes() );
        byte[] digestData = md.digest();

        // Write digest as string
        String digest = null;
        if(base64)
        {
            Base64Encoder enc = new Base64Encoder();
            enc.translate(digestData);
            digest = new String(enc.getCharArray());

        } else {
           digest = byteArrayToHex( digestData );
        }

        return  digest;
    }


    private static void byteToHex( StringBuilder buf, byte b ) {
        int n = b;
        if ( n < 0 ) {
            n = 256 + n;
        }
        int d1 = n / 16;
        int d2 = n % 16;
        buf.append( hex[d1] );
        buf.append( hex[d2] );
    }


    public static String byteArrayToHex( byte[] b ) {
        StringBuilder buf = new StringBuilder( b.length * 2 );
        for ( int i = 0; i < b.length; i++ ) {
            byteToHex( buf, b[i] );
        }
        return buf.toString();
    }


    /**
     *  The main program for the MD5 class
     *
     *@param  args  The command line arguments
     */
    public static void main( String[] args ) {
        System.out.println( "input: " + args[0] );
        System.out.println( "MD5:   " + MessageDigester.md5( args[0], false ) );
        System.out.println( "MD5 (base64):   " + MessageDigester.md5( args[0], true ) );
    }
}

