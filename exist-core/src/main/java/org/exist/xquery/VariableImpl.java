/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.Messages;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nullable;

/**
 * An XQuery/XPath variable, consisting of a QName and a value.
 * 
 * @author wolf
 */
public class VariableImpl implements Variable {

	// the name of the variable
	private final QName qname;
	
	// the current value assigned to the variable
	private Sequence value = null;
	
	// the context position of this variable in the local variable stack
	// this can be used to determine if a variable has been declared
	// before another
	private int positionInStack = 0;
	
	// the context document set
	private DocumentSet contextDocs = null;
	
    // the sequence type of this variable if known
    private SequenceType type = null;

    private int staticType = Type.ITEM;

    private boolean initialized = true;

	public VariableImpl(QName qname) {
		this.qname = qname;
	}
	
	public VariableImpl(VariableImpl var) {
		this(var.qname);
		this.value = var.value;
		this.contextDocs = var.contextDocs;
		this.type = var.type;
		this.staticType = var.staticType;
	}
	
	public void setValue(Sequence val) {
		this.value = val;
        if (val instanceof NodeImpl) {
            ValueSequence newSeq = new ValueSequence(1);
            newSeq.add((Item) val);
            newSeq.setHolderVariable(this);
            this.value = newSeq;
        } else if (val instanceof ValueSequence) {
            ((ValueSequence) this.value).setHolderVariable(this);
        }
    }

    public Sequence getValue() {
        return value;
	}
	
	public QName getQName() {
		return qname;
	}

	@Override
    public int getType() {
        if (type != null) {
        	return type.getPrimaryType();
        } else {
        	return Type.ITEM;
        }
    }

    private String getTypeDescription() {
		if (type != null) {
			return type.toString();
		} else {
			return Type.getTypeName(Type.ITEM) + getCardinality().toXQueryCardinalityString();
		}
	}
    
    public void setSequenceType(SequenceType type) throws XPathException {
    	this.type = type;
    	//Check the value's type if it is already assigned : happens with external variables    	
    	if (getValue() != null) {
            if (getSequenceType() != null) {
                Cardinality actualCardinality;
                if (getValue().isEmpty()) {actualCardinality = Cardinality.EMPTY_SEQUENCE;}
                else if (getValue().hasMany()) {actualCardinality = Cardinality._MANY;}
                else {actualCardinality = Cardinality.EXACTLY_ONE;}
            	//Type.EMPTY is *not* a subtype of other types ; checking cardinality first
        		if (!getSequenceType().getCardinality().isSuperCardinalityOrEqualOf(actualCardinality))
    				{throw new XPathException(getValue(), "XPTY0004: Invalid cardinality for variable $" + getQName() +
    						". Expected " +
    						getSequenceType().getCardinality().getHumanDescription() +
    						", got " + actualCardinality.getHumanDescription());}
        		//TODO : ignore nodes right now ; they are returned as xs:untypedAtomicType
        		if (!Type.subTypeOf(getSequenceType().getPrimaryType(), Type.NODE)) {
            		if (!getValue().isEmpty() && !Type.subTypeOf(getValue().getItemType(), getSequenceType().getPrimaryType()))
        				{throw new XPathException(getValue(), "XPTY0004: Invalid type for variable $" + getQName() +
        						". Expected " +
        						Type.getTypeName(getSequenceType().getPrimaryType()) +
        						", got " +Type.getTypeName(getValue().getItemType()));}
        		//Here is an attempt to process the nodes correctly
        		} else {
        			//Same as above : we probably may factorize 
            		if (!getValue().isEmpty() && !Type.subTypeOf(getValue().getItemType(), getSequenceType().getPrimaryType()))
        				{throw new XPathException(getValue(), "XPTY0004: Invalid type for variable $" + getQName() +
        						". Expected " +
        						Type.getTypeName(getSequenceType().getPrimaryType()) +
        						", got " +Type.getTypeName(getValue().getItemType()));}
        			
        		}
            }
    		
    	}
    }
    
    public SequenceType getSequenceType() {
        return type;
    }

    public void setStaticType(int type) {
        staticType = type;
    }

    public int getStaticType() {
        return staticType;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public void setIsInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public void destroy(final XQueryContext context, @Nullable final Sequence contextSequence) {
        if (value != null) {
			value.destroy(context, contextSequence);
		}
    }

    @Override
	public String toString() {
		final StringBuilder result = new StringBuilder();
		result.append("$").append(qname.getStringValue());
		result.append(" as ");
		result.append(getTypeDescription());
		result.append(" ");	
		if (value == null) {
			result.append("[not set]");
		} else {
			result.append(":= ").append(value.toString());
		}
		return result.toString();
	}
	
	public int getDependencies(XQueryContext context) {
//		if(context.getCurrentStackSize() > positionInStack)
//			return Dependency.CONTEXT_SET + Dependency.GLOBAL_VARS+ Dependency.CONTEXT_ITEM;
//		else
//			return Dependency.CONTEXT_SET + Dependency.LOCAL_VARS;
		
		if(context.getCurrentStackSize() > positionInStack)
			{return Dependency.CONTEXT_SET + Dependency.CONTEXT_VARS;}
		else
			{return Dependency.CONTEXT_SET + Dependency.LOCAL_VARS;}
	}

	@Override
	public Cardinality getCardinality() {
		if (type != null) {
			return type.getCardinality();
		} else {
			return Cardinality.ZERO_OR_MORE;
		}
	}
	
	public void setStackPosition(int position) {
		positionInStack = position;
	}
	
	public DocumentSet getContextDocs() {
	    return contextDocs;
	}
	
	public void setContextDocs(DocumentSet docs) {
	    this.contextDocs = docs;
	}
    
    public void checkType() throws XPathException {
        if (type == null)
            {return;}
        type.checkCardinality(value);
        
        if (value.isEmpty())
            {return;}
        
        final int requiredType = type.getPrimaryType();
        if(Type.subTypeOf(requiredType, Type.ANY_ATOMIC_TYPE)) {
        	if(!Type.subTypeOf(value.getItemType(), Type.ANY_ATOMIC_TYPE))
                {value = Atomize.atomize(value);}
        	
        	//TODO : we should recheck the dependencies of this method
        	//and remove that conversion !        	
        	
            if(requiredType != Type.ANY_ATOMIC_TYPE)
                {value = convert(value);}
        }
        if(!type.checkType(value)) {
			final SequenceType valueType = new SequenceType(value.getItemType(), value.getCardinality());
			if ((!value.isEmpty()) && type.getPrimaryType() == Type.DOCUMENT && value.getItemType() == Type.DOCUMENT) {
				// it's a document... we need to get the document element's name
				final NodeValue nvItem = (NodeValue)value.itemAt(0);
				final Document doc;
				if (nvItem instanceof Document) {
					doc = (Document) nvItem;
				} else {
					doc = nvItem.getOwnerDocument();
				}
				if (doc != null) {
					final Element elem = doc.getDocumentElement();
					if (elem != null) {
						valueType.setNodeName(new QName(elem.getLocalName(), elem.getNamespaceURI()));
					}
				}
			}

        	throw new XPathException(getValue(),
					Messages.getMessage(Error.VAR_TYPE_MISMATCH,
							toString(),
							type.toString(),
							valueType.toString()
					)
			);
        }
    }
    
    private Sequence convert(Sequence seq) throws XPathException {
        final ValueSequence result = new ValueSequence();
        Item item;
        for(final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            item = i.nextItem();
            result.add(item.convertTo(type.getPrimaryType()));
        }
        return result;
    }
}
