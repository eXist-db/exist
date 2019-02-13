/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.util.Base64Encoder;


public class MessageDigester {

    private static String[] hex = {"0", "1", "2", "3", "4", "5", "6", "7",
            "8", "9", "a", "b", "c", "d", "e", "f"};

    private static final Logger LOG = LogManager.getLogger(MessageDigester.class);

    public static String md5( String message, boolean base64) {
        MessageDigest md5 = null;
        String digest = message;
        try {
            md5 = MessageDigest.getInstance( "MD5" );
            md5.update( message.getBytes() );
            final byte[] digestData = md5.digest();
            
            if(base64)
            {
            	final Base64Encoder enc = new Base64Encoder();
            	enc.translate(digestData);
            	digest = new String(enc.getCharArray());
            }
            else
            {
               digest = byteArrayToHex( digestData );
            }
        } catch ( final NoSuchAlgorithmException e ) {
            LOG.warn( "MD5 not supported. Using plain string as password!" );
        } catch ( final Exception e ) {
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

        } catch (final NoSuchAlgorithmException e) {
            final String error = "'"+ algorithm + "' is not a supported MessageDigest algorithm.";
            LOG.error(error, e);
            throw new IllegalArgumentException(error);
        }


        // Calculate hash
        md.update( message.getBytes() );
        final byte[] digestData = md.digest();

        // Write digest as string
        String digest = null;
        if(base64)
        {
            final Base64Encoder enc = new Base64Encoder();
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
        final int d1 = n / 16;
        final int d2 = n % 16;
        buf.append( hex[d1] );
        buf.append( hex[d2] );
    }


    public static String byteArrayToHex( byte[] b ) {
        final StringBuilder buf = new StringBuilder( b.length * 2 );
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

