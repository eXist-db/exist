/* 
 *  eXist Open Source Native XML Database 
 *  Copyright (C) 2001-06 The eXist Project 
 *  http://exist-db.org 
 *  http://exist.sourceforge.net 
 *   
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU Lesser General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version. 
 *   
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 *  GNU Lesser General Public License for more details. 
 *   
 *  You should have received a copy of the GNU Lesser General Public License 
 *  along with this program; if not, write to the Free Software 
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA. 
 *   
 *  $Id$ 
 */ 
package org.exist.xquery.value; 
 
import org.exist.dom.NodeSet; 
import org.exist.xquery.XPathException; 
import org.exist.dom.ExtArrayNodeSet; 
import org.exist.dom.NodeProxy; 
import org.exist.xquery.GroupSpec; 
import org.exist.xquery.util.ExpressionDumper; 
import org.exist.xquery.Constants; 
import org.exist.xquery.XPathException; 
import org.exist.xquery.ValueComparison; 
import org.exist.xquery.GeneralComparison; 
import org.exist.xquery.XQueryContext; 
import java.text.Collator; 
 
 
 
/** 
 * A sequence that containts items of one group specified by the group specs of 
 * an "group by" clause. Used by  
 * {@link org.exist.xquery.value.GroupedValueSequenceList}. 
 *  
 * This class is based on {@link org.exist.xquery.value.OrderedValueSequence}. 
 *  
 * WARNING : don't use except for group by clause  
 *  
 * @author Boris Verhaegen 
 */ 
 
public class GroupedValueSequence extends AbstractSequence { 
 
     
    private Entry[] items = null; 
    private int count = 0; 
    //grouping keys values of this group 
    private GroupSpec groupSpecs[];  
    private Item groupKey[]; 
    private XQueryContext context; 
     
    // used to keep track of the type of added items. 
    private int itemType = Type.ANY_TYPE; 
     
    public GroupedValueSequence(GroupSpec groupSpecs[], int size, Item keyItem[], XQueryContext aContext) { 
        this.groupSpecs = groupSpecs; 
        this.items = new Entry[size]; 
        this.groupKey = keyItem; 
        this.context = aContext; 
    } 
     
    /* (non-Javadoc) 
     * @see org.exist.xquery.value.Sequence#iterate() 
     */ 
    public SequenceIterator iterate() throws XPathException { 
        return new GroupedValueSequenceIterator(); 
    } 
 
    /* (non-Javadoc) 
     * @see org.exist.xquery.value.AbstractSequence#unorderedIterator() 
     */ 
    public SequenceIterator unorderedIterator() { 
        return new GroupedValueSequenceIterator(); 
    } 
     
    /* (non-Javadoc) 
     * @see org.exist.xquery.value.Sequence#getLength() 
     */ 
    public int getLength() { 
        return (items == null) ? 0 : count; 
    } 
     
    public Item[] getGroupKey() { 
        return this.groupKey; 
    } 
 
 
    /** 
     * Check equality with self grouping keys values. Returns a boolean. 
     *  
     *  @param     otherGroupKey    An <code>Item[]</code> to compare with 
     *                          self grouping keys values 
     *  @return                 <code>true</code> if equal, <code>false</code> 
     *                          otherwise.     
     */         
    public boolean checkKeys(Item otherGroupKey[]) throws XPathException { 
        if(this.groupKey.length == otherGroupKey.length){ 
         
            for(int i = 0; i < this.groupKey.length ; i++){ 
                // bv : compare atomic values using GeneralComparison 
                if(!GeneralComparison.compareAtomic(Collator.getInstance(), this.groupKey[i].atomize(), otherGroupKey[i].atomize(), true, Constants.TRUNC_BOTH, Constants.EQ)){ 
                    return false; 
                } 
            } 
            return true; 
        } 
        else 
            return false; 
    } 
     
     
    public boolean isEmpty() { 
        return isEmpty; 
    } 
 
    public boolean hasOne() { 
        return hasOne; 
    } 
     
    /* (non-Javadoc) 
     * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item) 
     */ 
    public void add(Item item) throws XPathException { 
        if (hasOne) 
            hasOne = false; 
        if (isEmpty) 
            hasOne = true; 
        isEmpty = false; 
        if(count == items.length) { 
            Entry newItems[] = new Entry[count * 2]; 
            System.arraycopy(items, 0, newItems, 0, count); 
            items = newItems; 
        } 
        items[count++] = new Entry(item); 
        checkItemType(item.getType()); 
    } 
 
    /* (non-Javadoc) 
     * @see org.exist.xquery.value.AbstractSequence#addAll(org.exist.xquery.value.Sequence) 
     */ 
    public void addAll(Sequence other) throws XPathException { 
        if(other.hasOne()) 
            add(other.itemAt(0));         
        else if(!other.isEmpty()) { 
            for(SequenceIterator i = other.iterate(); i.hasNext(); ) {  
                Item next = i.nextItem(); 
                if(next != null) 
                    add(next); 
            } 
        }  
    } 
     
    public Item itemAt(int pos) { 
        if(items != null && pos > -1 && pos < count) 
            return items[pos].item; 
        else 
            return null; 
    } 
 
    private void checkItemType(int type) { 
        if(itemType == Type.NODE || itemType == type) 
            return; 
        if(itemType == Type.ANY_TYPE) 
            itemType = type; 
        else 
            itemType = Type.NODE; 
    } 
     
    /* (non-Javadoc) 
     * @see org.exist.xquery.value.Sequence#getItemType() 
     */ 
    public int getItemType() { 
        return itemType; 
    } 
     
    /* (non-Javadoc) 
     * @see org.exist.xquery.value.Sequence#toNodeSet() 
     */ 
    public NodeSet toNodeSet() throws XPathException { 
        // for this method to work, all items have to be nodes 
        if(itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) { 
            NodeSet set = new ExtArrayNodeSet(); 
            //We can't make it from an ExtArrayNodeSet (probably because it is sorted ?) 
            //NodeSet set = new ArraySet(100); 
            for (int i = 0; i < this.count; i++) { 
                NodeValue v = null; 
                Entry temp = items[i]; 
                v = (NodeValue)temp.item; 
                    if(v.getImplementationType() != NodeValue.PERSISTENT_NODE) { 
                    set.add((NodeProxy)v); 
                } else { 
                    set.add((NodeProxy)v); 
                } 
            } 
            return set; 
        } else 
            throw new XPathException("Type error: the sequence cannot be converted into" + 
                " a node set. Item type is " + Type.getTypeName(itemType)); 
 
    } 
 
    /* (non-Javadoc) 
     * @see org.exist.xquery.value.Sequence#removeDuplicates() 
     */ 
    public void removeDuplicates() { 
        // TODO: is this ever relevant? 
    } 
     
    private class Entry implements Comparable { 
        Item item; 
        AtomicValue values[]; 
        public Entry(Item item) throws XPathException { 
            this.item = item; 
            values = new AtomicValue[groupSpecs.length]; 
            for(int i = 0; i < groupSpecs.length; i++) { 
                Sequence seq = groupSpecs[i].getGroupExpression().eval(null); 
                values[i] = AtomicValue.EMPTY_VALUE; 
                //TODO : get rid of getLength() 
                if(seq.hasOne()) { 
                    values[i] = seq.itemAt(0).atomize(); 
                } else if(seq.hasMany()) 
                    throw new XPathException("expected a single value for group by expression " + 
                        ExpressionDumper.dump(groupSpecs[i].getGroupExpression()) +  
                        " ; found: " + seq.getLength()); 
            } 
        } 
 
        /* (non-Javadoc) 
         * @see java.lang.Comparable#compareTo(java.lang.Object) 
         */ 
        public int compareTo(Object o){ 
            Entry other = (Entry)o; 
            int cmp = 0; 
            AtomicValue a, b; 
            for(int i = 0; i < values.length; i++) { 
                a = values[i]; 
                b = other.values[i]; 
                if(a == AtomicValue.EMPTY_VALUE && b != AtomicValue.EMPTY_VALUE) { 
                        cmp = 1; 
                }  
                else{ 
                    cmp = a.compareTo(b); 
                } 
            } 
            return cmp; 
        } 
    } 
     
    private class GroupedValueSequenceIterator implements SequenceIterator { 
        int pos = 0; 
        /* (non-Javadoc) 
         * @see org.exist.xquery.value.SequenceIterator#hasNext() 
         */ 
        public boolean hasNext() { 
            return pos < count; 
        } 
         
        /* (non-Javadoc) 
         * @see org.exist.xquery.value.SequenceIterator#nextItem() 
         */ 
        public Item nextItem() { 
            if(pos < count) { 
                return items[pos++].item; 
            } 
            return null; 
        } 
    } 
} 
     
