/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


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
            
            if (base64) {
                digest = Base64.encodeBase64String(digestData);
            } else {
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
        if (base64) {
            digest = Base64.encodeBase64String(digestData);
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
        for (byte value : b) {
            byteToHex(buf, value);
        }
        return buf.toString();
    }
}

