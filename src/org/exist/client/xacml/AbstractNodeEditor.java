package org.exist.client.xacml;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public abstract class AbstractNodeEditor implements NodeEditor {
	private List<ChangeListener> listeners = new ArrayList<ChangeListener>(2);

	protected void fireChanged() {
		final ChangeEvent event = new ChangeEvent(this);
		for(final ChangeListener listener : listeners)
			listener.stateChanged(event);
	}
	
	public void addChangeListener(ChangeListener listener) {
		if(listener != null)
			{listeners.add(listener);}
	}
	
	public void removeChangeListener(ChangeListener listener) {
		if(listeners != null)
			{listeners.remove(listener);}
	}
}
