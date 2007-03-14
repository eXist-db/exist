/**
 * QueryResponse.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

import org.exist.Namespaces;

public class QueryResponse  implements java.io.Serializable {
    private org.exist.soap.QueryResponseCollections collections;
    private int hits;
    private long queryTime;

    public QueryResponse() {
    }

    public QueryResponse(
           org.exist.soap.QueryResponseCollections collections,
           int hits,
           long queryTime) {
           this.collections = collections;
           this.hits = hits;
           this.queryTime = queryTime;
    }


    /**
     * Gets the collections value for this QueryResponse.
     * 
     * @return collections
     */
    public org.exist.soap.QueryResponseCollections getCollections() {
        return collections;
    }


    /**
     * Sets the collections value for this QueryResponse.
     * 
     * @param collections
     */
    public void setCollections(org.exist.soap.QueryResponseCollections collections) {
        this.collections = collections;
    }


    /**
     * Gets the hits value for this QueryResponse.
     * 
     * @return hits
     */
    public int getHits() {
        return hits;
    }


    /**
     * Sets the hits value for this QueryResponse.
     * 
     * @param hits
     */
    public void setHits(int hits) {
        this.hits = hits;
    }


    /**
     * Gets the queryTime value for this QueryResponse.
     * 
     * @return queryTime
     */
    public long getQueryTime() {
        return queryTime;
    }


    /**
     * Sets the queryTime value for this QueryResponse.
     * 
     * @param queryTime
     */
    public void setQueryTime(long queryTime) {
        this.queryTime = queryTime;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof QueryResponse)) return false;
        QueryResponse other = (QueryResponse) obj;
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
            this.hits == other.getHits() &&
            this.queryTime == other.getQueryTime();
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
        _hashCode += getHits();
        _hashCode += new Long(getQueryTime()).hashCode();
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(QueryResponse.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:exist", "QueryResponse"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("collections");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "collections"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:exist", "QueryResponseCollections"));
        elemField.setNillable(true);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("hits");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "hits"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("queryTime");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "queryTime"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "long"));
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
