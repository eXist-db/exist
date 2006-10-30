/**
 * EntityPermissionsList.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

public class EntityPermissionsList  implements java.io.Serializable {
    private org.exist.soap.EntityPermissions[] elements;

    public EntityPermissionsList() {
    }

    public EntityPermissionsList(
           org.exist.soap.EntityPermissions[] elements) {
           this.elements = elements;
    }


    /**
     * Gets the elements value for this EntityPermissionsList.
     * 
     * @return elements
     */
    public org.exist.soap.EntityPermissions[] getElements() {
        return elements;
    }


    /**
     * Sets the elements value for this EntityPermissionsList.
     * 
     * @param elements
     */
    public void setElements(org.exist.soap.EntityPermissions[] elements) {
        this.elements = elements;
    }

    public org.exist.soap.EntityPermissions getElements(int i) {
        return this.elements[i];
    }

    public void setElements(int i, org.exist.soap.EntityPermissions _value) {
        this.elements[i] = _value;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof EntityPermissionsList)) return false;
        EntityPermissionsList other = (EntityPermissionsList) obj;
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
        new org.apache.axis.description.TypeDesc(EntityPermissionsList.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:exist", "EntityPermissionsList"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("elements");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "elements"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:exist", "EntityPermissions"));
        elemField.setMinOccurs(0);
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
