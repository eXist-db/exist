/*
 * 
 */
package org.exist.xquery.value;

/**
 * This class is used to specify the name and description of an XQuery function parameter.
 *
 * @author lcahlander
 * @version 1.3
 */
public class FunctionParameterSequenceType extends FunctionReturnSequenceType {

    private String attributeName = null;

    /**
     * @param attributeName The name of the parameter in the <strong>FunctionSignature</strong>.
     * @param primaryType   The <strong>Type</strong> of the parameter.
     * @param cardinality   The <strong>Cardinality</strong> of the parameter.
     * @param description   A description of the parameter in the <strong>FunctionSignature</strong>.
     * @see org.exist.xquery.FunctionSignature @see Type @see org.exist.xquery.Cardinality
     */
    public FunctionParameterSequenceType(String attributeName, int primaryType, int cardinality, String description) {
        super(primaryType, cardinality, description);
        this.attributeName = attributeName;
    }

    public FunctionParameterSequenceType(String attributeName) {
        super();
        this.attributeName = attributeName;
    }

    /**
     * @return the attributeName
     */
    public String getAttributeName() {
        return attributeName;
    }

    /**
     * @param attributeName the attributeName to set
     */
    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

}
