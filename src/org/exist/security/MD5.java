
package org.exist.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.log4j.Category;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    20. August 2002
 */
public class MD5 {

    private static String[] hex = {"0", "1", "2", "3", "4", "5", "6", "7",
            "8", "9", "a", "b", "c", "d", "e", "f"};

    private static final Category LOG = Category.getInstance(MD5.class.getName());

    /**
     *  Description of the Method
     *
     *@param  passwd  Description of the Parameter
     *@return         Description of the Return Value
     */
    public static String md( String passwd ) {
        MessageDigest md5 = null;
        String digest = passwd;
        try {
            md5 = MessageDigest.getInstance( "MD5" );
            md5.update( passwd.getBytes() );
            byte[] digestData = md5.digest();

            digest = byteArrayToHex( digestData );
        } catch ( NoSuchAlgorithmException e ) {
            LOG.warn( "MD5 not supported. Using plain string as password!" );
        } catch ( Exception e ) {
            LOG.warn( "Digest creation failed. Using plain string as password!" );
        }
        return digest;
    }


    private static void byteToHex( StringBuffer buf, byte b ) {
        int n = b;
        if ( n < 0 ) {
            n = 256 + n;
        }
        int d1 = n / 16;
        int d2 = n % 16;
        buf.append( hex[d1] );
        buf.append( hex[d2] );
    }


    /**
     *  Description of the Method
     *
     *@param  b  Description of the Parameter
     *@return    Description of the Return Value
     */
    public static String byteArrayToHex( byte[] b ) {
        StringBuffer buf = new StringBuffer( b.length * 2 );
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
        System.out.println( "MD5:   " + MD5.md( args[0] ) );
    }
}

