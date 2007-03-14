/**
 * CollectionDesc.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

import org.exist.Namespaces;

public class CollectionDesc  implements java.io.Serializable {
    private java.lang.String name;
    private java.lang.String owner;
    private java.lang.String group;
    private int permissions;
    private long created;
    private org.exist.soap.Strings collections;
    private org.exist.soap.DocumentDescs documents;

    public CollectionDesc() {
    }

    public CollectionDesc(
           java.lang.String name,
           java.lang.String owner,
           java.lang.String group,
           int permissions,
           long created,
           org.exist.soap.Strings collections,
           org.exist.soap.DocumentDescs documents) {
           this.name = name;
           this.owner = owner;
           this.group = group;
           this.permissions = permissions;
           this.created = created;
           this.collections = collections;
           this.documents = documents;
    }


    /**
     * Gets the name value for this CollectionDesc.
     * 
     * @return name
     */
    public java.lang.String getName() {
        return name;
    }


    /**
     * Sets the name value for this CollectionDesc.
     * 
     * @param name
     */
    public void setName(java.lang.String name) {
        this.name = name;
    }


    /**
     * Gets the owner value for this CollectionDesc.
     * 
     * @return owner
     */
    public java.lang.String getOwner() {
        return owner;
    }


    /**
     * Sets the owner value for this CollectionDesc.
     * 
     * @param owner
     */
    public void setOwner(java.lang.String owner) {
        this.owner = owner;
    }


    /**
     * Gets the group value for this CollectionDesc.
     * 
     * @return group
     */
    public java.lang.String getGroup() {
        return group;
    }


    /**
     * Sets the group value for this CollectionDesc.
     * 
     * @param group
     */
    public void setGroup(java.lang.String group) {
        this.group = group;
    }


    /**
     * Gets the permissions value for this CollectionDesc.
     * 
     * @return permissions
     */
    public int getPermissions() {
        return permissions;
    }


    /**
     * Sets the permissions value for this CollectionDesc.
     * 
     * @param permissions
     */
    public void setPermissions(int permissions) {
        this.permissions = permissions;
    }


    /**
     * Gets the created value for this CollectionDesc.
     * 
     * @return created
     */
    public long getCreated() {
        return created;
    }


    /**
     * Sets the created value for this CollectionDesc.
     * 
     * @param created
     */
    public void setCreated(long created) {
        this.created = created;
    }


    /**
     * Gets the collections value for this CollectionDesc.
     * 
     * @return collections
     */
    public org.exist.soap.Strings getCollections() {
        return collections;
    }


    /**
     * Sets the collections value for this CollectionDesc.
     * 
     * @param collections
     */
    public void setCollections(org.exist.soap.Strings collections) {
        this.collections = collections;
    }


    /**
     * Gets the documents value for this CollectionDesc.
     * 
     * @return documents
     */
    public org.exist.soap.DocumentDescs getDocuments() {
        return documents;
    }


    /**
     * Sets the documents value for this CollectionDesc.
     * 
     * @param documents
     */
    public void setDocuments(org.exist.soap.DocumentDescs documents) {
        this.documents = documents;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof CollectionDesc)) return false;
        CollectionDesc other = (CollectionDesc) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.name==null && other.getName()==null) || 
             (this.name!=null &&
              this.name.equals(other.getName()))) &&
            ((this.owner==null && other.getOwner()==null) || 
             (this.owner!=null &&
              this.owner.equals(other.getOwner()))) &&
            ((this.group==null && other.getGroup()==null) || 
             (this.group!=null &&
              this.group.equals(other.getGroup()))) &&
            this.permissions == other.getPermissions() &&
            this.created == other.getCreated() &&
            ((this.collections==null && other.getCollections()==null) || 
             (this.collections!=null &&
              this.collections.equals(other.getCollections()))) &&
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
        if (getName() != null) {
            _hashCode += getName().hashCode();
        }
        if (getOwner() != null) {
            _hashCode += getOwner().hashCode();
        }
        if (getGroup() != null) {
            _hashCode += getGroup().hashCode();
        }
        _hashCode += getPermissions();
        _hashCode += new Long(getCreated()).hashCode();
        if (getCollections() != null) {
            _hashCode += getCollections().hashCode();
        }
        if (getDocuments() != null) {
            _hashCode += getDocuments().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(CollectionDesc.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:exist", "CollectionDesc"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("name");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "name"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("owner");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "owner"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("group");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "group"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("permissions");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "permissions"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("created");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "created"));
        elemField.setXmlType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "long"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("collections");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "collections"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:exist", "Strings"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("documents");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:exist", "documents"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:exist", "DocumentDescs"));
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
