package org.exist.soap;

/**
 * A wrapper class containing a query summary for all documents for which hits
 * have been found in the collection.
 */
public class QueryResponseCollection  implements java.io.Serializable {
    private java.lang.String collectionName;
    private org.exist.soap.QueryResponseDocument[] documents;

    public QueryResponseCollection() {
    }

	/**
	 * Returns the name of the collection represented by this 
	 * object.
	 * 
	 * @return
	 */
    public java.lang.String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(java.lang.String collectionName) {
        this.collectionName = collectionName;
    }

	/**
	 * Returns a query result summary for each of the documents
	 * for which hits have been found in this collection.
	 * 
	 * @return
	 */
    public org.exist.soap.QueryResponseDocument[] getDocuments() {
        return documents;
    }

    public void setDocuments(org.exist.soap.QueryResponseDocument[] documents) {
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
              java.util.Arrays.equals(this.documents, other.getDocuments())));
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
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getDocuments());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getDocuments(), i);
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
        new org.apache.axis.description.TypeDesc(QueryResponseCollection.class);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:exist", "QueryResponseCollection"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("collectionName");
        elemField.setXmlName(new javax.xml.namespace.QName("", "collectionName"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("documents");
        elemField.setXmlName(new javax.xml.namespace.QName("", "documents"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:exist", "QueryResponseDocument"));
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
