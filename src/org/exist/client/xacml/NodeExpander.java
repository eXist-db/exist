package org.exist.client.xacml;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.tree.TreePath;

public class NodeExpander implements ActionListener
{
	private static final int EXPAND_DELAY = 1000;
	private Timer expandTimer;
	private JTree tree;
	private XACMLTreeNode contextNode;
	
	public NodeExpander(JTree tree)
	{
		this.tree = tree;
		expandTimer = new Timer(EXPAND_DELAY, this);
		expandTimer.setRepeats(false);
	}
	public void actionPerformed(ActionEvent event)
	{
		if(contextNode == null)
			return;
		TreePath path = XACMLTreeModel.getPathToNode(contextNode);
		tree.expandPath(path);
		contextNode = null;
	}
	public void hover(XACMLTreeNode contextNode)
	{
		this.contextNode = contextNode;
		expandTimer.restart();
	}
	public void stop()
	{
		expandTimer.stop();
		this.contextNode = null;
	}
}
