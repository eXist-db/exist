package org.exist.client.xacml;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.tree.TreePath;

public class NodeCopyAction extends AbstractAction implements ClipboardOwner
{
	private static final long serialVersionUID = -3740034384422578150L;

	private JTree tree;
	private XACMLTreeNode contextNode;
	
	@SuppressWarnings("unused")
	private NodeCopyAction() {}
	public NodeCopyAction(JTree tree)
	{
		super("Copy as XML");
		if(tree == null)
			{throw new NullPointerException("Tree cannot be null");}
		
		putValue(MNEMONIC_KEY, Integer.valueOf(KeyEvent.VK_C));
		putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("ctrl C"));
		this.tree = tree;
	}
	
	public String getName()
	{
		return (String)getValue(NAME);
	}

	public KeyStroke getTrigger()
	{
		return (KeyStroke)getValue(ACCELERATOR_KEY);
	}
	public void actionPerformed(ActionEvent event)
	{
		final Object source = event.getSource();
		XACMLTreeNode node;
		if(source == tree)
		{
			final TreePath path = tree.getSelectionPath();
			if(path == null)
				{return;}
			 node = (XACMLTreeNode)path.getLastPathComponent();
		}
		else
			{node = contextNode;}
		if(node == null)
			{return;}
		final Transferable data = new NodeTransferable(node);
		final Toolkit toolkit = Toolkit.getDefaultToolkit();
		final Clipboard clipboard = toolkit.getSystemClipboard();
		clipboard.setContents(data, this);
		contextNode = null;
	}

	public void setContextNode(XACMLTreeNode contextNode)
	{
		this.contextNode = contextNode;
	}
	
	// ClipboardOwnder method
	public void lostOwnership(Clipboard clipboard, Transferable data) {}
}
