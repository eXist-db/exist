package org.exist.soap;

/**
 * A wrapper around collection information.
 * 
 * @author wolf
 */
public class Collection  implements java.io.Serializable {
    private java.lang.String[] collections;
    private java.lang.String[] resources;

    public Collection() {
    }

	/**
	 * Return the list of child collections in this collection.
	 * 
	 * @return
	 */
    public java.lang.String[] getCollections() {
        return collections;
    }

    public void setCollections(java.lang.String[] collections) {
        this.collections = collections;
    }

	/**
	 * Return the list of resources contained in this collection.
	 * @return
	 */
    public java.lang.String[] getResources() {
        return resources;
    }

    public void setResources(java.lang.String[] resources) {
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
              java.util.Arrays.equals(this.collections, other.getCollections()))) &&
            ((this.resources==null && other.getResources()==null) || 
             (this.resources!=null &&
              java.util.Arrays.equals(this.resources, other.getResources())));
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
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getCollections());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getCollections(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getResources() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getResources());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getResources(), i);
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
        new org.apache.axis.description.TypeDesc(Collection.class);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:exist", "Collection"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("collections");
        elemField.setXmlName(new javax.xml.namespace.QName("", "collections"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("resources");
        elemField.setXmlName(new javax.xml.namespace.QName("", "resources"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
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
