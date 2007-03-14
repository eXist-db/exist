/**
 * Base64BinaryArray.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

import org.exist.Namespaces;

public class Base64BinaryArray  implements java.io.Serializable {
    private byte[][] elements;

    public Base64BinaryArray() {
    }

    public Base64BinaryArray(
           byte[][] elements) {
           this.elements = elements;
    }


    /**
     * Gets the elements value for this Base64BinaryArray.
     * 
     * @return elements
     */
    public byte[][] getElements() {
        return elements;
    }


    /**
     * Sets the elements value for this Base64BinaryArray.
     * 
     * @param elements
     */
    public void setElements(byte[][] elements) {
        this.elements = elements;
    }

    public byte[] getElements(int i) {
        return this.elements[i];
    }

    public void setElements(int i, byte[] _value) {
        this.elements[i] = _value;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof Base64BinaryArray)) return false;
        Base64BinaryArray other = (Base64BinaryArray) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.elements==null && other.getElements()==null) || 
             (this.elements!=null &&
              java.util.Arrays.equals(this.elements, other.getElements())));
        __equalsCalc = null;
        return _equals;
    }

    private boolean __hashCodeCalc = false;
    public synchronized int hashCode() {
        if (__hashCodeCalc) {
            return 0;
        }
        __hashCodeCalc = true;
        int _hashCode = 1;
        if (getElements() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getElements());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getElements(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(Base64BinaryArray.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:exist", "Base64BinaryArray"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("elements");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "elements"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "base64Binary"));
        elemField.setNillable(false);
        elemField.setMaxOccursUnbounded(true);
        typeDesc.addFieldDesc(elemField);
    }

    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

    /**
     * Get Custom Serializer
     */
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanSerializer(
            _javaType, _xmlType, typeDesc);
    }

    /**
     * Get Custom Deserializer
     */
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanDeserializer(
            _javaType, _xmlType, typeDesc);
    }

}
