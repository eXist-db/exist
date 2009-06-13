package org.exist.client.xacml;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.xmldb.api.base.Collection;

import com.sun.xacml.Policy;
import com.sun.xacml.PolicySet;
import com.sun.xacml.Rule;
import com.sun.xacml.Target;
import com.sun.xacml.combine.OrderedPermitOverridesPolicyAlg;
import com.sun.xacml.combine.OrderedPermitOverridesRuleAlg;
import com.sun.xacml.combine.PolicyCombiningAlgorithm;
import com.sun.xacml.combine.RuleCombiningAlgorithm;
import com.sun.xacml.ctx.Result;

public class XACMLEditor extends JFrame implements ActionListener, TreeModelListener, TreeSelectionListener, WindowListener
{
	private static final String DEFAULT_DESCRIPTION = "This is a policy template.  It will match and deny everything until you change the target and add rules.";
	private static final String DEFAULT_RULE_DESCRIPTION = "This rule denies everything that is not permitted by the rules above it when " +
	"used with the ordered permit overrides combining algorithm.  Any rules below it will not be evaluated, so it should be the last rule";

	private static final String DEFAULT_POLICY_ID = "NewPolicy";
	private static final String DEFAULT_POLICY_SET_ID = "NewPolicySet";
	private static final String DEFAULT_RULE_ID = "NewRule";
	
	private static final String CLOSE = "Close";
	private static final String SAVE = "Save";
	
	private static final int MIN_FRAME_WIDTH = 600;
	private static final int MIN_FRAME_HEIGHT = 350;
	private static final int MINIMUM_TREE_WIDTH = 100;
		
	private DatabaseInterface dbInterface;
	private XACMLTreeModel model;
	private JTree tree;
	private NodeEditor editor;
	private JSplitPane split;
	
	private XACMLEditor() {}
	public XACMLEditor(Collection systemCollection)
	{
		super("XACML Policy Editor");
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(this);
		
		dbInterface = new DatabaseInterface(systemCollection);
		setupMenuBar(); 
		createInterface();
			
		//setSize(600, 480);
		pack();
		Dimension size = getSize();
		if(size.width <= MIN_FRAME_WIDTH)
			size.width = MIN_FRAME_WIDTH;
		if(size.height <= MIN_FRAME_HEIGHT)
			size.height = MIN_FRAME_HEIGHT;
		setSize(size);
	}
	private void createInterface()
	{
		split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
		getContentPane().add(split);
		
		model = new XACMLTreeModel(dbInterface.getPolicies());
		model.addTreeModelListener(this);
		
		
		tree = new JTree(model);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		TreeMutator mutator = new TreeMutator(tree);
		tree.addTreeSelectionListener(this);
		tree.setCellRenderer(new CustomRenderer(mutator));
		tree.setEditable(false);
		tree.setShowsRootHandles(true);
		tree.setRootVisible(false);
		
		Dimension minSize = tree.getMinimumSize();
		if(minSize.width < MINIMUM_TREE_WIDTH)
			minSize.width = MINIMUM_TREE_WIDTH;
		tree.setMinimumSize(minSize);
				
		JScrollPane scroll = new JScrollPane(tree);
		
		split.setLeftComponent(scroll);
		split.setRightComponent(new JScrollPane());
		split.setOneTouchExpandable(false);
	}
	public void close()
	{
		if(hasUnsavedChanges())
		{
			String message = "There are unsaved changes.  Do you want to save your changes before closing?";
			String title = "Save changes?"; 
			int ret = JOptionPane.showConfirmDialog(this, message, title, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
			if(ret == JOptionPane.CANCEL_OPTION)
				return;
			else if(ret == JOptionPane.YES_OPTION)
				saveAll();
		}
		dispose();
	}
	public boolean hasUnsavedChanges()
	{
		return model.hasUnsavedChanges();
	}
	public void saveAll()
	{
		if(editor != null)
			editor.pushChanges();
		dbInterface.writePolicies((RootNode)model.getRoot());
		tree.repaint();
	}
	
	public static PolicySet createDefaultPolicySet(PolicyElementContainer parent)
	{
		return createDefaultPolicySet(createUniqueId(parent, DEFAULT_POLICY_SET_ID));
	}
	public static PolicySet createDefaultPolicySet(String policySetID)
	{
		PolicyCombiningAlgorithm alg = new OrderedPermitOverridesPolicyAlg(); 
		return new PolicySet(URI.create(policySetID), alg, createEmptyTarget());
	}

	public static Target createEmptyTarget()
	{
		return new Target(Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
	}
	public static Policy createDefaultPolicy(PolicyElementContainer parent)
	{
		return createDefaultPolicy(createUniqueId(parent, DEFAULT_POLICY_ID));
	}
	public static Policy createDefaultPolicy(String policyID)
	{
		Target emptyTarget = createEmptyTarget();
		RuleCombiningAlgorithm alg = new OrderedPermitOverridesRuleAlg(); 
		Rule denyEverythingRule = createDefaultRule("DenyAll");
		List rules = Collections.singletonList(denyEverythingRule);
		return new Policy(URI.create(policyID), alg, DEFAULT_DESCRIPTION, emptyTarget, rules);
	}
	public static Rule createDefaultRule(PolicyElementContainer parent)
	{
		return createDefaultRule(createUniqueId(parent, DEFAULT_RULE_ID));
	}
	public static String createUniqueId(PolicyElementContainer parent, String base)
	{
		if(parent == null)
			throw new NullPointerException("Parent cannot be null");
		String newId = base;
		for(int i = 2; parent.containsId(newId); ++i)
			newId = base + Integer.toString(i);
		return newId;
	}
	public static Rule createDefaultRule(String id)
	{
		return new Rule(URI.create(id), Result.DECISION_DENY, DEFAULT_RULE_DESCRIPTION, null, null);
	}
	
	private void setupMenuBar()
	{
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		JMenu file = new JMenu("File");
		file.setMnemonic(KeyEvent.VK_F);
		menuBar.add(file);
		
		JMenuItem saveItem = new JMenuItem(SAVE,KeyEvent.VK_S);
		saveItem.setActionCommand(SAVE);
		saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
				Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
		saveItem.addActionListener(this);
		file.add(saveItem);

		JMenuItem closeItem = new JMenuItem(CLOSE,KeyEvent.VK_W);
		closeItem.setActionCommand(CLOSE);
		closeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W,
				Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
		closeItem.addActionListener(this);
		file.add(closeItem);
	}
	public void actionPerformed(ActionEvent event)
	{
		String actionCommand = event.getActionCommand();
		if(CLOSE.equals(actionCommand))
			close();
		else if(SAVE.equals(actionCommand))
			saveAll();
	}
	//this method fixes dumb repaint issues
	//when components are updated
	private void forceRepaint()
	{
		Container contentPane = getContentPane();
		contentPane.invalidate();
		contentPane.validate();
		contentPane.repaint();
	}

	public void valueChanged(TreeSelectionEvent event)
	{
		if(editor != null)
		{
			editor.pushChanges();
			editor = null;
		}
		
		TreePath selectedPath = event.getPath();
		Object value = selectedPath.getLastPathComponent();
		
		if(value instanceof AbstractPolicyNode)
			editor = new AbstractPolicyEditor();
		else if(value instanceof RuleNode)
			editor = new RuleEditor();
		else if(value instanceof ConditionNode)
			editor = null;//TODO: implement condition editing
		else if(value instanceof TargetNode)
			editor = new TargetEditor(dbInterface);
		
		int dividerLocation = split.getDividerLocation();
		JScrollPane scroll = ((JScrollPane)split.getRightComponent());
		if(editor == null)
			scroll.setViewportView(null);
		else				
		{
			editor.setNode((XACMLTreeNode)value);
			scroll.setViewportView(editor.getComponent());
		}
		split.setDividerLocation(dividerLocation);
			
		
		forceRepaint();
	}
	//WindowListener methods
	public void windowActivated(WindowEvent event) {}
	public void windowClosed(WindowEvent event) {}
	public void windowClosing(WindowEvent event)
	{
		close();
	}
	public void windowDeactivated(WindowEvent event) {}
	public void windowDeiconified(WindowEvent event) {}
	public void windowIconified(WindowEvent event) {}
	public void windowOpened(WindowEvent event) {}

	private void treeChanged(TreeModelEvent event)
	{
		tree.revalidate();
		tree.repaint();
	}
	//TreeModelListener methods
	public void treeNodesChanged(TreeModelEvent event)
	{
		treeChanged(event);
	}
	public void treeNodesInserted(TreeModelEvent event)
	{
		treeChanged(event);
	}
	public void treeNodesRemoved(TreeModelEvent event)
	{
		treeChanged(event);
	}
	public void treeStructureChanged(TreeModelEvent event)
	{
		treeChanged(event);
	}
}
