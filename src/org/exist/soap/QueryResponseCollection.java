/**
 * QueryResponseCollection.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

import org.exist.Namespaces;

public class QueryResponseCollection  implements java.io.Serializable {
    private java.lang.String collectionName;
    private org.exist.soap.QueryResponseDocuments documents;

    public QueryResponseCollection() {
    }

    public QueryResponseCollection(
           java.lang.String collectionName,
           org.exist.soap.QueryResponseDocuments documents) {
           this.collectionName = collectionName;
           this.documents = documents;
    }


    /**
     * Gets the collectionName value for this QueryResponseCollection.
     * 
     * @return collectionName
     */
    public java.lang.String getCollectionName() {
        return collectionName;
    }


    /**
     * Sets the collectionName value for this QueryResponseCollection.
     * 
     * @param collectionName
     */
    public void setCollectionName(java.lang.String collectionName) {
        this.collectionName = collectionName;
    }


    /**
     * Gets the documents value for this QueryResponseCollection.
     * 
     * @return documents
     */
    public org.exist.soap.QueryResponseDocuments getDocuments() {
        return documents;
    }


    /**
     * Sets the documents value for this QueryResponseCollection.
     * 
     * @param documents
     */
    public void setDocuments(org.exist.soap.QueryResponseDocuments documents) {
        this.documents = documents;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof QueryResponseCollection)) return false;
        QueryResponseCollection other = (QueryResponseCollection) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.collectionName==null && other.getCollectionName()==null) || 
             (this.collectionName!=null &&
              this.collectionName.equals(other.getCollectionName()))) &&
            ((this.documents==null && other.getDocuments()==null) || 
             (this.documents!=null &&
              this.documents.equals(other.getDocuments())));
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
        if (getCollectionName() != null) {
            _hashCode += getCollectionName().hashCode();
        }
        if (getDocuments() != null) {
            _hashCode += getDocuments().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(QueryResponseCollection.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:exist", "QueryResponseCollection"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("collectionName");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "collectionName"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("documents");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "documents"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:exist", "QueryResponseDocuments"));
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
