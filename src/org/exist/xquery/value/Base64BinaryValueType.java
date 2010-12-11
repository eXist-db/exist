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





//    @Override
 //   public AtomicValue convertTo(int requiredType) throws XPathException {

   //     if(requiredType == Type.BASE64_BINARY) {
     //       return this;
       // } else {
         //   return super.convertTo(requiredType);
        //}

        /* switch(requiredType) {
            case Type.BASE64_BINARY:
                return this;
            case Type.HEX_BINARY:
                //buf.position(0);
                //return new HexBinary(channel, buf);       //TODO
                return null;
            case Type.UNTYPED_ATOMIC:
                //Added trim() since it looks like a new line character is added
                return new UntypedAtomicValue(new String(getStringValue()).trim());
            case Type.STRING:
                //return new StringValue(new String(data, "UTF-8"));
                //Added trim() since it looks like a new line character is added
                return new StringValue(getStringValue());
            default:
                throw new XPathException("cannot convert " + Type.getTypeName(getType()) + " to " + Type.getTypeName(requiredType));
        } */
    //}
}
