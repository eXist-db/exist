/**
 * Collection.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

public class Collection  implements java.io.Serializable {
	//TODO: should this become XmldbURI?
    private org.exist.soap.StringArray collections;
    private org.exist.soap.StringArray resources;

    public Collection() {
    }

    public Collection(
           org.exist.soap.StringArray collections,
           org.exist.soap.StringArray resources) {
           this.collections = collections;
           this.resources = resources;
    }


    /**
     * Gets the collections value for this Collection.
     * 
     * @return collections
     */
    public org.exist.soap.StringArray getCollections() {
        return collections;
    }


    /**
     * Sets the collections value for this Collection.
     * 
     * @param collections
     */
    public void setCollections(org.exist.soap.StringArray collections) {
        this.collections = collections;
    }


    /**
     * Gets the resources value for this Collection.
     * 
     * @return resources
     */
    public org.exist.soap.StringArray getResources() {
        return resources;
    }


    /**
     * Sets the resources value for this Collection.
     * 
     * @param resources
     */
    public void setResources(org.exist.soap.StringArray resources) {
        this.resources = resources;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof Collection)) return false;
        Collection other = (Collection) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.collections==null && other.getCollections()==null) || 
             (this.collections!=null &&
              this.collections.equals(other.getCollections()))) &&
            ((this.resources==null && other.getResources()==null) || 
             (this.resources!=null &&
              this.resources.equals(other.getResources())));
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
        if (getCollections() != null) {
            _hashCode += getCollections().hashCode();
        }
        if (getResources() != null) {
            _hashCode += getResources().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(Collection.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:exist", "Collection"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("collections");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "collections"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:exist", "StringArray"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("resources");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "resources"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:exist", "StringArray"));
        elemField.setNillable(false);
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
