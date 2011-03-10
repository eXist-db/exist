package org.exist.util.io;

import java.io.OutputStream;

/**
 * Base64 encoding OutputStream
 *
 * Same as org.apache.commons.codec.binary.Base64OutputStream but disabled chunking of output
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class Base64OutputStream extends org.apache.commons.codec.binary.Base64OutputStream {
    public Base64OutputStream(OutputStream out, boolean doEncode) {
        super(out, doEncode, 0, null);
    }
}