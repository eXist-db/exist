package org.exist.xquery;

import org.exist.dom.QName;

/**
 * Represents an XQuery 3.0 Annotation
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class Annotation {
    
    private final QName name;
    private final LiteralValue value[];
    private final FunctionSignature signature;
    
    public Annotation(final QName name, final FunctionSignature signature) {
        this(name, new LiteralValue[0], signature);
    }
    
    public Annotation(final QName name, final LiteralValue[] value, final FunctionSignature signature) {
       this.name = name;
       this.value = value;
       this.signature = signature;
    }
    
    public QName getName() {
        return name;
    }
    
    public LiteralValue[] getValue() {
        return value;
    }
    
    /**
     * Get the signature of the function on which this
     * annotation was placed
     *
     * @return the function signature
     */
    public FunctionSignature getFunctionSignature() {
        return signature;
    }
}