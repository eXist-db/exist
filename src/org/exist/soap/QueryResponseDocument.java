/**
 * QueryResponseDocument.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

import org.exist.Namespaces;

public class QueryResponseDocument  implements java.io.Serializable {
    private java.lang.String documentName;
    private int hitCount;

    public QueryResponseDocument() {
    }

    public QueryResponseDocument(
           java.lang.String documentName,
           int hitCount) {
           this.documentName = documentName;
           this.hitCount = hitCount;
    }


    /**
     * Gets the documentName value for this QueryResponseDocument.
     * 
     * @return documentName
     */
    public java.lang.String getDocumentName() {
        return documentName;
    }


    /**
     * Sets the documentName value for this QueryResponseDocument.
     * 
     * @param documentName
     */
    public void setDocumentName(java.lang.String documentName) {
        this.documentName = documentName;
    }


    /**
     * Gets the hitCount value for this QueryResponseDocument.
     * 
     * @return hitCount
     */
    public int getHitCount() {
        return hitCount;
    }


    /**
     * Sets the hitCount value for this QueryResponseDocument.
     * 
     * @param hitCount
     */
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
            ((this.documentName==null && other.getDocumentName()==null) || 
             (this.documentName!=null &&
              this.documentName.equals(other.getDocumentName()))) &&
            this.hitCount == other.getHitCount();
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
        new org.apache.axis.description.TypeDesc(QueryResponseDocument.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:exist", "QueryResponseDocument"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("documentName");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "documentName"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("hitCount");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "hitCount"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"));
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
