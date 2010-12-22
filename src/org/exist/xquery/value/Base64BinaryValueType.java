package org.exist.xquery.value;

import org.apache.commons.codec.binary.Base64OutputStream;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class Base64BinaryValueType extends BinaryValueType<Base64OutputStream> {

    public Base64BinaryValueType() {
        super(Type.BASE64_BINARY, Base64OutputStream.class);
    }
}
