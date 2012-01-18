package org.exist.xquery;

import org.exist.dom.QName;

/**
 * Represents an XQuery 3.0 Annotation
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class Annotation {
    
    private final QName name;
    private final LiteralValue value[];
    
    public Annotation(QName name) {
        this(name, new LiteralValue[0]);
    }
    
    public Annotation(QName name, LiteralValue[] value) {
       this.name = name;
       this.value = value;
    }
    
    public QName getName() {
        return name;
    }
    
    public LiteralValue[] getValue() {
        return value;
    }
}