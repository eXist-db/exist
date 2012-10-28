
package org.exist.client.security;
 
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.AbstractListModel;

public class SortedListModel<T extends Object> extends AbstractListModel {
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