/**
 * IndexedElement.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

import org.exist.Namespaces;

public class IndexedElement  implements java.io.Serializable {
    private java.lang.String localName;
    private java.lang.String namespaceURI;
    private java.lang.String prefix;
    private int occurences;

    public IndexedElement() {
    }

    public IndexedElement(
           java.lang.String localName,
           java.lang.String namespaceURI,
           java.lang.String prefix,
           int occurences) {
           this.localName = localName;
           this.namespaceURI = namespaceURI;
           this.prefix = prefix;
           this.occurences = occurences;
    }


    /**
     * Gets the localName value for this IndexedElement.
     * 
     * @return localName
     */
    public java.lang.String getLocalName() {
        return localName;
    }


    /**
     * Sets the localName value for this IndexedElement.
     * 
     * @param localName
     */
    public void setLocalName(java.lang.String localName) {
        this.localName = localName;
    }


    /**
     * Gets the namespaceURI value for this IndexedElement.
     * 
     * @return namespaceURI
     */
    public java.lang.String getNamespaceURI() {
        return namespaceURI;
    }


    /**
     * Sets the namespaceURI value for this IndexedElement.
     * 
     * @param namespaceURI
     */
    public void setNamespaceURI(java.lang.String namespaceURI) {
        this.namespaceURI = namespaceURI;
    }


    /**
     * Gets the prefix value for this IndexedElement.
     * 
     * @return prefix
     */
    public java.lang.String getPrefix() {
        return prefix;
    }


    /**
     * Sets the prefix value for this IndexedElement.
     * 
     * @param prefix
     */
    public void setPrefix(java.lang.String prefix) {
        this.prefix = prefix;
    }


    /**
     * Gets the occurences value for this IndexedElement.
     * 
     * @return occurences
     */
    public int getOccurences() {
        return occurences;
    }


    /**
     * Sets the occurences value for this IndexedElement.
     * 
     * @param occurences
     */
    public void setOccurences(int occurences) {
        this.occurences = occurences;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof IndexedElement)) return false;
        IndexedElement other = (IndexedElement) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.localName==null && other.getLocalName()==null) || 
             (this.localName!=null &&
              this.localName.equals(other.getLocalName()))) &&
            ((this.namespaceURI==null && other.getNamespaceURI()==null) || 
             (this.namespaceURI!=null &&
              this.namespaceURI.equals(other.getNamespaceURI()))) &&
            ((this.prefix==null && other.getPrefix()==null) || 
             (this.prefix!=null &&
              this.prefix.equals(other.getPrefix()))) &&
            this.occurences == other.getOccurences();
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
        if (getLocalName() != null) {
            _hashCode += getLocalName().hashCode();
        }
        if (getNamespaceURI() != null) {
            _hashCode += getNamespaceURI().hashCode();
        }
        if (getPrefix() != null) {
            _hashCode += getPrefix().hashCode();
        }
        _hashCode += getOccurences();
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(IndexedElement.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:exist", "IndexedElement"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("localName");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "localName"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("namespaceURI");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "namespaceURI"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("prefix");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "prefix"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("occurences");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "occurences"));
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
