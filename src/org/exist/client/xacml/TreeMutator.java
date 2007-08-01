package org.exist.client.xacml;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.net.URI;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import com.sun.xacml.Policy;
import com.sun.xacml.PolicySet;
import com.sun.xacml.PolicyTreeElement;
import com.sun.xacml.Rule;

public class TreeMutator implements ActionListener, DragGestureListener, DragSourceListener, DropTargetListener, KeyListener, MouseListener, PopupMenuListener 
{
	private static final String NEW_RULE = "New Rule";
	private static final String NEW_POLICY = "New Policy";
	private static final String NEW_POLICY_SET = "New Policy Set";
	private static final String REMOVE = "Remove";	

	public static final int BIAS_BEFORE = -1;
	public static final int BIAS_CURRENT = 0;
	public static final int BIAS_AFTER = 1;
	public static final int BIAS_NO_DESTINATION = -2;
	
	private static final int BIAS_DELTA_Y = 4;

	private XACMLTreeNode currentDestinationNode = null;
	private int destinationBias = 0;
	
	private NodeCopyAction copyAction;
	private NodeExpander expander; 
	private AutoScroller scroller;
	
	private JTree tree;
	private JPopupMenu popup;
	private XACMLTreeNode contextNode;
	
	private TreeMutator() {}
	public TreeMutator(JTree tree)
	{
		if(tree == null)
			throw new NullPointerException("Tree cannot be null");
		popup = new JPopupMenu();
		
		this.tree = tree;
		
		scroller = new AutoScroller();
		expander = new NodeExpander(tree);
		copyAction = new NodeCopyAction(tree);
		tree.getInputMap().put(copyAction.getTrigger(), copyAction.getName());
		tree.getActionMap().put(copyAction.getName(), copyAction);
		
		tree.setDragEnabled(false);
		tree.setTransferHandler(null);
		tree.addMouseListener(this);
		tree.addKeyListener(this);
		DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(tree, DnDConstants.ACTION_COPY_OR_MOVE, this);
		new DropTarget(tree, this);

		reset();
	}
	public JTree getTree()
	{
		return tree;
	}
	
	public void reset()
	{
		contextNode = null;
		copyAction.setContextNode(null);
		popup.removeAll();
		if(popup.isVisible())
			popup.setVisible(false);
	}

	//MouseListener methods
	public void mouseClicked(MouseEvent event)
	{
		showPopup(event);
	}
	public void mouseEntered(MouseEvent event)
	{
		showPopup(event);
	}
	public void mouseExited(MouseEvent event)
	{
		showPopup(event);
	}
	public void mousePressed(MouseEvent event)
	{
		showPopup(event);
	}
	public void mouseReleased(MouseEvent event)
	{
		showPopup(event);
	}
	private void showPopup(MouseEvent event)
	{	
		if(!popup.isPopupTrigger(event))
			return;
		reset();
		Object source = event.getSource();
		if(source != tree)
			return;
		
		Point p = event.getPoint();
		int row = tree.getClosestRowForLocation(p.x, p.y);
		if(row == -1)
		{
			showRootPopup(p);
			return;
		}
		
		Rectangle bounds = tree.getRowBounds(row);
		if(bounds.y > p.y || bounds.y + bounds.height <= p.y)
		{
			showRootPopup(p);
			return;
		}
		
		TreePath path = tree.getPathForRow(row);
		if(path == null)
		{
			showRootPopup(p);
			return;
		}
		
		
		Object last = path.getLastPathComponent();
		XACMLTreeNode node = (XACMLTreeNode)last;
		copyAction.setContextNode(node);
		
		if(!(last instanceof PolicyElementNode))
		{
			popup.add(copyAction);
			popup.show(tree, p.x, p.y);
			return;
		}
		
		contextNode = (XACMLTreeNode)last;
		handleTreeElementNode();
		
		popup.addSeparator();
		popup.add(copyAction);
		
		popup.show(tree, p.x, p.y);
	}
	private void handleTreeElementNode()
	{
		if(contextNode instanceof PolicySetNode)
		{
			addPolicySetItem();
			addPolicyItem();
		}
		else if(contextNode instanceof PolicyNode)
			addRuleItem();
		//else if(contextNode instanceof Rule)
		//	do nothing in this case
		
		addRemoveItem();
	}
	private void addRemoveItem()
	{
		JMenuItem remove = new JMenuItem(REMOVE, KeyEvent.VK_R);
		remove.addActionListener(this);
		popup.add(remove);
	}
	private void addRuleItem()
	{
		JMenuItem newRule = new JMenuItem(NEW_RULE, KeyEvent.VK_R);
		newRule.addActionListener(this);
		popup.add(newRule);
	}
	private void addPolicyItem()
	{
		JMenuItem newPolicy = new JMenuItem(NEW_POLICY, KeyEvent.VK_P);
		newPolicy.addActionListener(this);
		popup.add(newPolicy);
	}
	private void addPolicySetItem()
	{
		JMenuItem newPolicySet = new JMenuItem(NEW_POLICY_SET, KeyEvent.VK_S);
		newPolicySet.addActionListener(this);
		popup.add(newPolicySet);
	}

	private void showRootPopup(Point p)
	{
		contextNode = getRootNode();
		
		addPolicySetItem();
		addPolicyItem();
		
		popup.show(tree, p.x, p.y);
	}
	private RootNode getRootNode()
	{
		TreeModel model = tree.getModel();
		if(!(model instanceof XACMLTreeModel))
			return null;
		XACMLTreeModel xmodel = (XACMLTreeModel)model;
		return (RootNode)xmodel.getRoot();
	}
	
	private void newRule()
	{
		if(contextNode instanceof PolicyNode)
		{
			PolicyNode node = (PolicyNode)contextNode;
			Rule rule = XACMLEditor.createDefaultRule(node);
			node.add(rule);
		}
	}
	private void newPolicySet()
	{
		if(contextNode instanceof PolicySetNode || contextNode instanceof RootNode)
		{
			PolicyElementContainer node =((PolicyElementContainer)contextNode);
			PolicySet ps = XACMLEditor.createDefaultPolicySet(node);
			node.add(ps);
		}
	}
	private void newPolicy()
	{
		if(contextNode instanceof PolicySetNode || contextNode instanceof RootNode)
		{
			PolicyElementContainer node =((PolicyElementContainer)contextNode);
			Policy p = XACMLEditor.createDefaultPolicy(node);
			node.add(p);
		}
	}
	private void remove()
	{
		if(contextNode == null)
			return;
		NodeContainer parent = contextNode.getParent();
		if(parent instanceof PolicyElementContainer && contextNode instanceof PolicyElementNode)
			((PolicyElementContainer)parent).remove((PolicyElementNode)contextNode);
	}

	public void actionPerformed(ActionEvent event)
	{
		String actionCommand = event.getActionCommand();
		if(actionCommand == null)
			return;
		else if(actionCommand.equals(NEW_RULE))
			newRule();
		else if(actionCommand.equals(NEW_POLICY))
			newPolicy();
		else if(actionCommand.equals(NEW_POLICY_SET))
			newPolicySet();
		else if(actionCommand.equals(REMOVE))
			remove();
		tree.revalidate();
		tree.repaint();
	}
	


	//KeyListener methods
	public void keyPressed(KeyEvent event)
	{
		//avoid collisions with JTree's builtin bindings
		if(event.isShiftDown() || event.isControlDown() || !event.isAltDown())
			return;
		
		int keyCode = event.getKeyCode();
		int delta;
		if(keyCode == KeyEvent.VK_UP)
			delta = -1;
		else if(keyCode == KeyEvent.VK_DOWN)
			delta = 1;
		else
			return;
		TreePath selected = tree.getSelectionPath();
		if(selected == null)
			return;
		XACMLTreeNode treeNode = (XACMLTreeNode)selected.getLastPathComponent();
		if(!(treeNode instanceof PolicyElementNode))
			return;
		PolicyElementNode node = (PolicyElementNode)treeNode;
		PolicyElementContainer parent = (PolicyElementContainer)node.getParent();
		int currentIndex = parent.indexOfChild(node);
		if(currentIndex < 0)
			return;
		currentIndex += delta;
		if(currentIndex < 0 || currentIndex >= parent.getChildCount())
			return;
		if(currentIndex == 0 && !(parent instanceof RootNode))
			return;
		tree.clearSelection();
		parent.remove(node);
		parent.add(currentIndex, node);
		tree.setSelectionPath(XACMLTreeModel.getPathToNode(node));
	}

	public void keyReleased(KeyEvent event) {}
	public void keyTyped(KeyEvent event) {}
	
	//PopupMenuListener methods
	public void popupMenuCanceled(PopupMenuEvent arg0) {}
	public void popupMenuWillBecomeInvisible(PopupMenuEvent event)
	{
		reset();
	}
	public void popupMenuWillBecomeVisible(PopupMenuEvent arg0) {}

	
	//	DragGestureListener method
	public void dragGestureRecognized(DragGestureEvent event)
	{
		Point location = event.getDragOrigin();
		TreePath path = tree.getPathForLocation(location.x, location.y);
		if(path == null)
			return;

		int action = event.getDragAction();
		XACMLTreeNode transferNode = (XACMLTreeNode)path.getLastPathComponent();
		Cursor cursor = (action == DnDConstants.ACTION_MOVE) ? DragSource.DefaultMoveDrop : DragSource.DefaultCopyDrop;
		event.startDrag(cursor, new NodeTransferable(transferNode), this);
	}
	// DropTargetListener methods
	public void drop(DropTargetDropEvent event)
	{
		event.acceptDrop(event.getDropAction());
		
		boolean success = false;
		try
		{
			success = handleDrop(event);
		}
		//these exceptions should not happen:
		//	the flavor is checked and the returned
		//	data requires no IO
		catch(IOException ioe)
		{
			success = false;
		}
		catch(UnsupportedFlavorException ufe)
		{
			success = false;
		}
		finally
		{
			haltTimers();
			clearDestination();
			event.dropComplete(success);
		}
	}
	public void dragOver(DropTargetDragEvent event)
	{
		checkDrag(event);
	}
	public void dragEnter(DropTargetDragEvent event)
	{
		checkDrag(event);
	}
	public void dropActionChanged(DropTargetDragEvent event)
	{
		checkDrag(event);
	}
	public void dragExit(DropTargetEvent event)
	{
		haltTimers();
		clearDestination();
	}
	private void haltTimers()
	{
		scroller.stop();
		expander.stop();
	}

	private void checkDrag(DropTargetDragEvent event)
	{
		XACMLTreeNode oldNode = currentDestinationNode;
		int oldBias = destinationBias;
		
		Point location = event.getLocation();
		updateCurrentDestination(location, event.getDropAction());
		
		if(currentDestinationNode == null)
		{
			expander.stop();
			clearDestination();
			event.rejectDrag();
			return;
		}

		scroller.autoscroll(tree, location);
		
		if(destinationBias != BIAS_CURRENT)
			expander.stop();
		else if(oldNode != currentDestinationNode || destinationBias != oldBias)
			expander.hover(currentDestinationNode);
		
		if(supportsDrop(event))
			repaintDestination(oldNode, oldBias);
		else
			clearDestination();
	}

	private boolean supportsDrop(DropTargetDragEvent event)
	{
		int action = event.getDropAction();
		boolean supported;
		updateCurrentDestination(event.getLocation(), action);
		if(currentDestinationNode == null)
			supported = false;
		else if(event.isDataFlavorSupported(NodeTransferable.TARGET_FLAVOR))
		{
			if(action == DnDConstants.ACTION_COPY_OR_MOVE || action == DnDConstants.ACTION_MOVE)
				action = DnDConstants.ACTION_COPY;
			supported = isTargetDropValid(action);
		}
		else if(event.isDataFlavorSupported(NodeTransferable.CONDITION_FLAVOR))
		{
			if(action == DnDConstants.ACTION_COPY_OR_MOVE || action == DnDConstants.ACTION_MOVE)
				action = DnDConstants.ACTION_COPY;
			supported = isConditionDropValid(action);
		}
		else if(event.isDataFlavorSupported(NodeTransferable.RULE_FLAVOR))
			supported = isRuleDropValid(action);
		else if(event.isDataFlavorSupported(NodeTransferable.ABSTRACT_POLICY_FLAVOR))
			supported = isAbstractPolicyDropValid(action);
		else
			supported = false;
		
		if(supported)
			event.acceptDrag(action);
		else
			event.rejectDrag();
		return supported;
	}
	
	private boolean isTargetDropValid(int action)
	{
		if(action == DnDConstants.ACTION_MOVE)
			return false;
		if(currentDestinationNode instanceof PolicyElementNode || currentDestinationNode instanceof TargetNode)
			return destinationBias == TreeMutator.BIAS_CURRENT;
		return false;
	}
	private boolean isConditionDropValid(int action)
	{
		if(action == DnDConstants.ACTION_MOVE)
			return false;
		if(currentDestinationNode instanceof ConditionNode)
			return destinationBias == TreeMutator.BIAS_CURRENT;
		if(currentDestinationNode instanceof RuleNode || currentDestinationNode instanceof ConditionNode)
			return destinationBias == TreeMutator.BIAS_CURRENT;
		return false;
	}
	private boolean isRuleDropValid(int action)
	{
		if(currentDestinationNode instanceof PolicyNode)
			return destinationBias == TreeMutator.BIAS_CURRENT;
		if(currentDestinationNode instanceof RuleNode)
			return destinationBias == TreeMutator.BIAS_AFTER || destinationBias == TreeMutator.BIAS_BEFORE;
		if(currentDestinationNode instanceof TargetNode && currentDestinationNode.getParent() instanceof PolicyNode)
			return destinationBias == TreeMutator.BIAS_AFTER; 
		return false;
	}
	private boolean isAbstractPolicyDropValid(int action)
	{
		if(currentDestinationNode instanceof PolicySetNode || currentDestinationNode instanceof RootNode)
			return true;
		if(currentDestinationNode instanceof PolicyNode)
			return destinationBias == TreeMutator.BIAS_AFTER || destinationBias == TreeMutator.BIAS_BEFORE;
		if(currentDestinationNode instanceof TargetNode && currentDestinationNode.getParent() instanceof PolicySetNode)
			return destinationBias == TreeMutator.BIAS_AFTER;
		return false;
	}
	private boolean isPolicyElementDropValid(int action, PolicyElementNode srcNode)
	{
		if(srcNode instanceof RuleNode)
			return isRuleDropValid(action);
		else if(srcNode instanceof AbstractPolicyNode)
			return isAbstractPolicyDropValid(action);
		else
			return false;
	}

	private boolean handleDrop(DropTargetDropEvent event) throws IOException, UnsupportedFlavorException
	{
		Transferable data = event.getTransferable();
		int action = event.getDropAction();
		
		updateCurrentDestination(event.getLocation(), event.getDropAction());
		if(currentDestinationNode == null)
			return false;
		
		if(data.isDataFlavorSupported(NodeTransferable.TARGET_FLAVOR))
		{
			if(!isTargetDropValid(action))
				return false;
			TargetNode destTarget;
			if(currentDestinationNode instanceof PolicyElementNode)
				destTarget = ((PolicyElementNode)currentDestinationNode).getTarget();
			else if(currentDestinationNode instanceof TargetNode)
				destTarget = (TargetNode)currentDestinationNode;
			else
				return false;
			TargetNode source = (TargetNode)data.getTransferData(NodeTransferable.TARGET_FLAVOR);
			destTarget.setTarget(source.getTarget());
			return true;
		}
		if(data.isDataFlavorSupported(NodeTransferable.CONDITION_FLAVOR))
		{
			if(!isConditionDropValid(action))
				return false;
			ConditionNode destCondition;
			if(currentDestinationNode instanceof RuleNode)
				destCondition = ((RuleNode)currentDestinationNode).getCondition();
			else if(currentDestinationNode instanceof ConditionNode)
				destCondition = (ConditionNode)currentDestinationNode;
			else
				return false;
			ConditionNode source = (ConditionNode)data.getTransferData(NodeTransferable.CONDITION_FLAVOR);
			destCondition.setCondition(source.getCondition());
			return true;
		}
		if(data.isDataFlavorSupported(NodeTransferable.POLICY_ELEMENT_FLAVOR))
		{
			PolicyElementNode srcNode = (PolicyElementNode)data.getTransferData(NodeTransferable.POLICY_ELEMENT_FLAVOR);
			PolicyElementContainer oldParent = (PolicyElementContainer)srcNode.getParent();

			if(!isPolicyElementDropValid(action, srcNode))
				return false;
			
			
			PolicyElementContainer newParent;
			if(destinationBias == TreeMutator.BIAS_CURRENT)
				newParent = (PolicyElementContainer)currentDestinationNode;
			else
				newParent = (PolicyElementContainer)currentDestinationNode.getParent();
						
			if(isDescendantOrSelf(srcNode, newParent))
				return false;
			
			if(action == DnDConstants.ACTION_MOVE)
			{
				if(oldParent != null)
					oldParent.remove(srcNode);
			}
			
			int insertionIndex = newParent.indexOfChild(currentDestinationNode);
			if(insertionIndex < 0)
				insertionIndex = newParent.getChildCount();
			else if(destinationBias == TreeMutator.BIAS_AFTER) 
				insertionIndex++;
			
			if(action == DnDConstants.ACTION_MOVE && oldParent == newParent)
				newParent.add(insertionIndex, srcNode);
			else
			{
				PolicyTreeElement copy;
				String currentId = srcNode.getId().toString();
				if(newParent.containsId(currentId))
					copy = srcNode.create(URI.create(XACMLEditor.createUniqueId(newParent, currentId)));
				else
					copy = srcNode.create();
				newParent.add(insertionIndex, copy);
			}
			return true;
		}
		return false;
	}

	private boolean isDescendantOrSelf(PolicyElementNode srcNode, PolicyElementContainer newParent)
	{
		TreePath srcPath = XACMLTreeModel.getPathToNode(srcNode);
		TreePath newParentPath = XACMLTreeModel.getPathToNode(newParent);
		if(srcPath == null || newParentPath == null)
			return false;
		return srcNode == newParent || srcPath.isDescendant(newParentPath);
	}
	private void updateCurrentDestination(Point location, int dropAction)
	{
		TreePath currentPath = tree.getClosestPathForLocation(location.x, location.y);
		if(currentPath == null)
		{
			currentDestinationNode = null;
			destinationBias = BIAS_NO_DESTINATION;
			return;
		}
		currentDestinationNode = (XACMLTreeNode)currentPath.getLastPathComponent();
		
		int row = tree.getRowForPath(currentPath);
		Rectangle bounds = tree.getRowBounds(row);
		if(bounds.y > location.y || location.y < BIAS_DELTA_Y)
			destinationBias = BIAS_BEFORE;
		else if(bounds.y + bounds.height <= location.y)
			destinationBias = BIAS_AFTER;
		else
		{
			if(isDestinationDifferent(tree.getClosestPathForLocation(location.x, location.y - BIAS_DELTA_Y)))
				destinationBias = BIAS_BEFORE;
			else if(isDestinationDifferent(tree.getClosestPathForLocation(location.x, location.y + BIAS_DELTA_Y)))
				destinationBias = BIAS_AFTER;
			else
				destinationBias = BIAS_CURRENT;
		}
	}
	private boolean isDestinationDifferent(TreePath path)
	{
		return (path == null) ? (currentDestinationNode != null) : (currentDestinationNode != path.getLastPathComponent());
	}
	public int getDestinationBias(XACMLTreeNode testNode)
	{
		return (currentDestinationNode == null || destinationBias == BIAS_NO_DESTINATION || currentDestinationNode != testNode) ? BIAS_NO_DESTINATION : destinationBias;
	}
	private void clearDestination()
	{
		XACMLTreeNode oldNode = currentDestinationNode;
		int oldBias = destinationBias;
		currentDestinationNode = null;
		destinationBias = BIAS_NO_DESTINATION;
		repaintDestination(oldNode, oldBias);
	}
	private void repaintDestination(XACMLTreeNode oldNode, int oldBias)
	{
		if(oldNode != null && oldBias != BIAS_NO_DESTINATION)
			handleRepaintDestination(oldNode, oldBias);
		if(currentDestinationNode != null && destinationBias != BIAS_NO_DESTINATION)
			handleRepaintDestination(currentDestinationNode, destinationBias);
	}
	private void handleRepaintDestination(XACMLTreeNode node, int bias)
	{
		if(node == null || bias == BIAS_NO_DESTINATION)
			return;

		int row = tree.getRowForPath(XACMLTreeModel.getPathToNode(node));
		repaintRow(row);
		if(bias == BIAS_AFTER)
		{
			if(row+1 < tree.getRowCount())
				repaintRow(row+1);
		}
		else if(bias == BIAS_BEFORE)
		{
			if(row > 0)
				repaintRow(row - 1);
		}
	}
	private void repaintRow(int row)
	{
		Rectangle rect = tree.getRowBounds(row);
		if (rect != null)
			tree.repaint(rect);
	}


	//DragSourceListener methods
	public void dropActionChanged(DragSourceDragEvent event) { }
	public void dragEnter(DragSourceDragEvent event) {}
	public void dragOver(DragSourceDragEvent event) {}
	public void dragDropEnd(DragSourceDropEvent event) {}
	public void dragExit(DragSourceEvent event) {}
}
