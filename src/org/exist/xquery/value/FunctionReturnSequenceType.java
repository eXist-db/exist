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
public class FunctionReturnSequenceType extends SequenceType {

    private String description = null;

    /**
     * @param primaryType The <strong>Type</strong> of the parameter.
     * @param cardinality The <strong>Cardinality</strong> of the parameter.
     * @param description A description of the parameter in the <strong>FunctionSignature</strong>.
     * @see org.exist.xquery.FunctionSignature @see Type @see org.exist.xquery.Cardinality
     */
    public FunctionReturnSequenceType(int primaryType, int cardinality, String description) {
        super(primaryType, cardinality);
        this.description = description;
    }

    public FunctionReturnSequenceType() {
        super();
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }


}
