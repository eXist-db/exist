package org.exist.client.xacml;

import javax.swing.JComponent;
import javax.swing.event.ChangeListener;

public interface NodeEditor
{
	JComponent getComponent();
	void setNode(XACMLTreeNode node);
	void pushChanges();

	void removeChangeListener(ChangeListener listener);
	void addChangeListener(ChangeListener listener);
}
