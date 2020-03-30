/*
 * 
 */
package org.exist.xquery.value;

import org.exist.xquery.Cardinality;

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
    public FunctionParameterSequenceType(final String attributeName, final int primaryType, final Cardinality cardinality, final String description) {
        super(primaryType, cardinality, description);
        this.attributeName = attributeName;
    }

    /**
     * @param attributeName The name of the parameter in the <strong>FunctionSignature</strong>.
     * @param primaryType   The <strong>Type</strong> of the parameter.
     * @param cardinality   The <strong>Cardinality</strong> of the parameter.
     * @param description   A description of the parameter in the <strong>FunctionSignature</strong>.
     * @see org.exist.xquery.FunctionSignature @see Type @see org.exist.xquery.Cardinality
     *
     * @deprecated Use {@link #FunctionParameterSequenceType(String, int, Cardinality, String)}
     */
    @Deprecated
    public FunctionParameterSequenceType(final String attributeName, final int primaryType, final int cardinality, final String description) {
        super(primaryType, Cardinality.fromInt(cardinality), description);
        this.attributeName = attributeName;
    }

    public FunctionParameterSequenceType(final String attributeName) {
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
