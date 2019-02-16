/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.client.security;
 
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.AbstractListModel;

public class SortedListModel<T extends Object> extends AbstractListModel {

    private static final long serialVersionUID = 8156990970750901747L;

    private final SortedSet<T> model;

    public SortedListModel() {
        model = new TreeSet<T>();
    }

    @Override
    public int getSize() {
        return model.size();
    }

    @Override
    public T getElementAt(final int index) {
        return (T) model.toArray()[index];
    }

    public void add(final T element) {
        if(model.add(element)) {
            fireContentsChanged(this, 0, getSize());
        }
    }
    
    public void addAll(final T elements[]) {
        final Collection<T> c = Arrays.asList(elements);
        model.addAll(c);
        fireContentsChanged(this, 0, getSize());
    }

    public void clear() {
        model.clear();
        fireContentsChanged(this, 0, getSize());
    }

    public boolean contains(Object element) {
        return model.contains(element);
    }

    public T firstElement() {
        return model.first();
    }

    public Iterator<T> iterator() {
        return model.iterator();
    }

    public T lastElement() {
        return model.last();
    }

    public boolean removeElement(final T element) {
        final boolean removed = model.remove(element);
        if(removed) {
            fireContentsChanged(this, 0, getSize());
        }
        return removed;
    }
}