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
 
import java.util.ArrayList; 
import org.exist.xquery.GroupSpec; 
import org.exist.xquery.value.GroupedValueSequence; 
import org.exist.xquery.value.Sequence; 
import org.exist.xquery.XPathException; 
import org.exist.xquery.XQueryContext; 
 
/** 
 * An arrayList that containts a GroupedValueSequence for each group. 
 * Groups are specified by the group specs of a "group by" clause. Used by  
 * {@link org.exist.xquery.ForExpr} et al. 
 * 
 *  WARNING : don't use except for group by clause  
 *   
 * @author Boris Verhaegen (boris.verhaegen@gmail.com) 
 */ 
 
public class GroupedValueSequenceList extends ArrayList { 
 
    private GroupSpec groupSpecs[]; 
    private String toGroupVarName; 
    private XQueryContext context; 
 
    public GroupedValueSequenceList(GroupSpec groupSpecs[], String varName, XQueryContext aContext) { 
        this.groupSpecs = groupSpecs;     
        this.toGroupVarName = varName; 
        this.context = aContext; 
    } 
     
     
    public void setToGroupVarName(String varName){ 
        toGroupVarName = varName; 
    } 
     
    public String getToGroupVarName(){ 
        return toGroupVarName; 
    } 
     
    /** 
     * Add <code>item</code> in the correct <code>GroupedValueSequence</code>. 
     * Create correct GroupedValueSequence if needed. Insertion based on  
     * the group specs of a "group by" clause. 
     *   
     * @throws     XPathException 
     */ 
    public void add(Item item) throws XPathException { 
        Sequence specEvaluation[] = new Sequence[groupSpecs.length]; 
        Sequence keySequence = new ValueSequence(); /* new Sequence[groupSpecs.length]; */ 
         
        boolean groupAdded = false; 
                 
        for(int i = 0; i < groupSpecs.length ; i++){ 
            //evaluates the values of the grouping keys 
            specEvaluation[i] =  groupSpecs[i].getGroupExpression().eval(item.toSequence()); 
            keySequence.add(specEvaluation[i].itemAt(0)); 
        } 
         
        for(int k = 0; (k < this.size())/*&& (groupAdded == false)*/ ; k++){ 
            GroupedValueSequence currentGroup = (GroupedValueSequence)this.get(k); 
            if(currentGroup.checkKeys(keySequence)){ 
                //this group already exists, then add to this group 
                currentGroup.add(item); 
                groupAdded = true; 
            } 
        } 
        if(groupAdded == false){ 
            //this group doesn't exists, then creates this group 
            GroupedValueSequence newGroup = new GroupedValueSequence(groupSpecs, 1, keySequence,context); 
            newGroup.add(item); 
            super.add(newGroup); 
            groupAdded = true; 
        } 
    } 
     
    /** 
     * Add all items of a sequence 
     *  
     * @param sequence 
     * @throws XPathException 
     */ 
    public void addAll(Sequence sequence) throws XPathException{ 
        for (SequenceIterator i = sequence.iterate(); i.hasNext(); ) { 
            this.add(i.nextItem()); 
        } 
    } 
} 
