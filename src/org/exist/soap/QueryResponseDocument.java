/**
 * QueryResponseDocument.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis WSDL2Java emitter.
 */

package org.exist.soap;

public class QueryResponseDocument  implements java.io.Serializable {
    private java.lang.String documentName;
    private int hitCount;

    public QueryResponseDocument() {
    }

    public java.lang.String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(java.lang.String documentName) {
        this.documentName = documentName;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof QueryResponseDocument)) return false;
        QueryResponseDocument other = (QueryResponseDocument) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((documentName==null && other.getDocumentName()==null) || 
             (documentName!=null &&
              documentName.equals(other.getDocumentName()))) &&
            hitCount == other.getHitCount();
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
        if (getDocumentName() != null) {
            _hashCode += getDocumentName().hashCode();
        }
        _hashCode += getHitCount();
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(QueryResponseDocument.class);

    static {
        org.apache.axis.description.FieldDesc field = new org.apache.axis.description.ElementDesc();
        field.setFieldName("documentName");
        field.setXmlName(new javax.xml.namespace.QName("", "documentName"));
        field.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        typeDesc.addFieldDesc(field);
        field = new org.apache.axis.description.ElementDesc();
        field.setFieldName("hitCount");
        field.setXmlName(new javax.xml.namespace.QName("", "hitCount"));
        field.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "int"));
        typeDesc.addFieldDesc(field);
    };

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
