/*
Copyright (c) 2006 Yahoo! Inc. All rights reserved.
version 0.9.0
*/

/**
 * @class Contains the tree view metadata and the root node.  This is an
 * ordered tree; child nodes will be displayed in the order created, and
 * there currently is no way to change this.
 *
 * @constructor
 * @todo prune, graft, reload, repaint
 * @param {string} id The id of the element that the tree will be inserted
 * into.
 */
YAHOO.widget.TreeView = function(id) {
	if (id) { this.init(id); }
};

YAHOO.widget.TreeView.prototype = {

    /**
     * The id of tree container element
     *
     * @type String
     */
    id: null,

    /**
     * Flat collection of all nodes in this tree
     *
     * @type YAHOO.widget.Node[]
     * @private
     */
    _nodes: null,

    /**
     * We lock the tree control while waiting for the dynamic loader to return
     *
     * @type boolean
     */
    locked: false,

    /**
     * The animation to use for expanding children, if any
     *
     * @type string
     * @private
     */
    _expandAnim: null,

    /**
     * The animation to use for collapsing children, if any
     *
     * @type string
     * @private
     */
    _collapseAnim: null,

    /**
     * The current number of animations that are executing
     *
     * @type int
     * @private
     */
    _animCount: 0,

    /**
     * The maximum number of animations to run at one time.
     *
     * @type int
     */
    _maxAnim: 2,

    /**
     * Sets up the animation for expanding children
     *
     * @param {string} the type of animation (acceptable constants in YAHOO.widget.TVAnim)
     */
    setExpandAnim: function(type) {
        if (YAHOO.widget.TVAnim.isValid(type)) {
            this._expandAnim = type;
        }
    },

    /**
     * Sets up the animation for collapsing children
     *
     * @param {string} the type of animation (acceptable constants in YAHOO.widget.TVAnim)
     */
    setCollapseAnim: function(type) {
        if (YAHOO.widget.TVAnim.isValid(type)) {
            this._collapseAnim = type;
        }
    },

    /**
     * Perform the expand animation if configured, or just show the
     * element if not configured or too many animations are in progress
     *
     * @param el {HTMLElement} the element to animate
     * @return {boolean} true if animation could be invoked, false otherwise
     */
    animateExpand: function(el) {

        if (this._expandAnim && this._animCount < this._maxAnim) {
            // this.locked = true;
            var tree = this;
            var a = YAHOO.widget.TVAnim.getAnim(this._expandAnim, el,
                            function() { tree.expandComplete(); });
            if (a) {
                ++this._animCount;
                a.animate();
            }

            return true;
        }

        return false;
    },

    /**
     * Perform the collapse animation if configured, or just show the
     * element if not configured or too many animations are in progress
     *
     * @param el {HTMLElement} the element to animate
     * @return {boolean} true if animation could be invoked, false otherwise
     */
    animateCollapse: function(el) {

        if (this._collapseAnim && this._animCount < this._maxAnim) {
            // this.locked = true;
            var tree = this;
            var a = YAHOO.widget.TVAnim.getAnim(this._collapseAnim, el,
                            function() { tree.collapseComplete(); });
            if (a) {
                ++this._animCount;
                a.animate();
            }

            return true;
        }

        return false;
    },

    /**
     * Function executed when the expand animation completes
     */
    expandComplete: function() {
        --this._animCount;
        // this.locked = false;
    },

    /**
     * Function executed when the collapse animation completes
     */
    collapseComplete: function() {
        --this._animCount;
        // this.locked = false;
    },

    /**
     * Initializes the tree
     *
     * @parm {string} id the id of the element that will hold the tree
     * @private
     */
    init: function(id) {

        this.id = id;
        this._nodes = new Array();

        // store a global reference
        YAHOO.widget.TreeView.trees[id] = this;

        // Set up the root node
        this.root = new YAHOO.widget.RootNode(this);


    },

    /**
     * Renders the tree boilerplate and visible nodes
     */
    draw: function() {
        var html = this.root.getHtml();
        document.getElementById(this.id).innerHTML = html;
        this.firstDraw = false;
    },

    /**
     * Nodes register themselves with the tree instance when they are created.
     *
     * @param node {YAHOO.widget.Node} the node to register
     * @private
     */
    regNode: function(node) {
        this._nodes[node.index] = node;
    },

    /**
     * Returns the root node of this tree
     *
     * @return {YAHOO.widget.Node} the root node
     */
    getRoot: function() {
        return this.root;
    },

    /**
     * Configures this tree to dynamically load all child data
     *
     * @param {function} fnDataLoader the function that will be called to get the data
     */
    setDynamicLoad: function(fnDataLoader) {
        // this.root.dataLoader = fnDataLoader;
        // this.root._dynLoad = true;
        this.root.setDynamicLoad(fnDataLoader);
    },

    /**
     * Expands all child nodes.  Note: this conflicts with the "multiExpand"
     * node property.  If expand all is called in a tree with nodes that
     * do not allow multiple siblings to be displayed, only the last sibling
     * will be expanded.
     */
    expandAll: function() {
        if (!this.locked) {
            this.root.expandAll();
        }
    },

    /**
     * Collapses all expanded child nodes in the entire tree.
     */
    collapseAll: function() {
        if (!this.locked) {
            this.root.collapseAll();
        }
    },

    /**
     * Returns a node in the tree that has the specified index (this index
     * is created internally, so this function probably will only be used
     * in html generated for a given node.)
     *
     * @param {int} nodeIndex the index of the node wanted
     * @return {YAHOO.widget.Node} the node with index=nodeIndex, null if no match
     */
    getNodeByIndex: function(nodeIndex) {
        var n = this._nodes[nodeIndex];
        return (n) ? n : null;
    },

    /**
     * Returns a node that has a matching property and value in the data
     * object that was passed into its constructor.  Provides a flexible
     * way for the implementer to get a particular node.
     *
     * @param {object} property the property to search (usually a string)
     * @param {object} value the value we want to find (usuall an int or string)
     * @return {YAHOO.widget.Node} the matching node, null if no match
     */
    getNodeByProperty: function(property, value) {
        for (var i in this._nodes) {
            var n = this._nodes[i];
            if (n.data && value == n.data[property]) {
                return n;
            }
        }

        return null;
    },

    /**
     * Abstract method that is executed when a node is expanded
     *
     * @param node {YAHOO.widget.Node} the node that was expanded
     */
    onExpand: function(node) { },

    /**
     * Abstract method that is executed when a node is collapsed
     *
     * @param node {YAHOO.widget.Node} the node that was collapsed.
     */
    onCollapse: function(node) { }

};

/**
 * Global cache of tree instances
 *
 * @type Array
 * @private
 */
YAHOO.widget.TreeView.trees = [];

/**
 * Global method for getting a tree by its id.  Used in the generated
 * tree html.
 *
 * @param treeId {String} the id of the tree instance
 * @return {TreeView} the tree instance requested, null if not found.
 */
YAHOO.widget.TreeView.getTree = function(treeId) {
	var t = YAHOO.widget.TreeView.trees[treeId];
	return (t) ? t : null;
};

YAHOO.widget.TreeView.nodeCount = 0;

/**
 * Global method for getting a node by its id.  Used in the generated
 * tree html.
 *
 * @param treeId {String} the id of the tree instance
 * @param nodeIndex {String} the index of the node to return
 * @param return {YAHOO.widget.Node} the node instance requested, null if not found
 */
YAHOO.widget.TreeView.getNode = function(treeId, nodeIndex) {
	var t = YAHOO.widget.TreeView.getTree(treeId);
	return (t) ? t.getNodeByIndex(nodeIndex) : null;
};

/**
 * Adds an event.  Replace with event manager when available
 *
 * @param el the elment to bind the handler to
 * @param {string} sType the type of event handler
 * @param {function} fn the callback to invoke
 * @param {boolean} capture if true event is capture phase, bubble otherwise
 */
YAHOO.widget.TreeView.addHandler = function (el, sType, fn, capture) {
	capture = (capture) ? true : false;
	if (el.addEventListener) {
		el.addEventListener(sType, fn, capture);
	} else if (el.attachEvent) {
		el.attachEvent("on" + sType, fn);
	} else {
		el["on" + sType] = fn;
	}
};

/**
 * Attempts to preload the images defined in the styles used to draw the tree by
 * rendering off-screen elements that use the styles.
 */
YAHOO.widget.TreeView.preload = function() {

	var styles = [
		"ygtvtn",
		"ygtvtm",
		"ygtvtmh",
		"ygtvtp",
		"ygtvtph",
		"ygtvln",
		"ygtvlm",
		"ygtvlmh",
		"ygtvlp",
		"ygtvlph",
		"ygtvloading"
		];

	var sb = [];

	for (var i = 0; i < styles.length; ++i) {
		sb[sb.length] = '<span class="' + styles[i] + '">&nbsp;</span>';
	}

	var f = document.createElement("div");
	var s = f.style;
	s.position = "absolute";
	s.top = "-1000px";
	s.left = "-1000px";
	f.innerHTML = sb.join("");

	document.body.appendChild(f);
};

YAHOO.widget.TreeView.addHandler(window,
                "load", YAHOO.widget.TreeView.preload);

/* Copyright (c) 2006 Yahoo! Inc. All rights reserved. */

/**
 * @class Abstract node class
 * @constructor
 * @param oData {object} a string or object containing the data that will
 * be used to render this node
 * @param oParent {YAHOO.widget.Node} this node's parent node
 * @param expanded {boolean} the initial expanded/collapsed state
 */
YAHOO.widget.Node = function(oData, oParent, expanded) {
	if (oParent) { this.init(oData, oParent, expanded); }
};

YAHOO.widget.Node.prototype = {

    /**
     * The index for this instance obtained from global counter in YAHOO.widget.TreeView.
     *
     * @type int
     */
    index: 0,

    /**
     * This node's child node collection.
     *
     * @type YAHOO.widget.Node[]
     */
    children: null,

    /**
     * Tree instance this node is part of
     *
     * @type YAHOO.widget.TreeView
     */
    tree: null,

    /**
     * The data linked to this node.  This can be any object or primitive
     * value, and the data can be used in getNodeHtml().
     *
     * @type object
     */
    data: null,

    /**
     * Parent node
     *
     * @type YAHOO.widget.Node
     */
    parent: null,

    /**
     * The depth of this node.  We start at -1 for the root node.
     *
     * @type int
     */
    depth: -1,

    /**
     * The href for the node's label.  If one is not specified, the href will
     * be set so that it toggles the node.
     *
     * @type string
     */
    href: null,

    /**
     * The label href target, defaults to current window
     *
     * @type string
     */
    target: "_self",

    /**
     * The node's expanded/collapsed state
     *
     * @type boolean
     */
    expanded: false,

    /**
     * Can multiple children be expanded at once?
     *
     * @type boolean
     */
    multiExpand: true,

    /**
     * Should we render children for a collapsed node?  It is possible that the
     * implementer will want to render the hidden data...  @todo verify that we
     * need this, and implement it if we do.
     *
     * @type boolean
     */
    renderHidden: false,

    /**
     * Flag that is set to true the first time this node's children are rendered.
     *
     * @type boolean
     */
    childrenRendered: false,

    /**
     * This node's previous sibling
     *
     * @type YAHOO.widget.Node
     */
    previousSibling: null,

    /**
     * This node's next sibling
     *
     * @type YAHOO.widget.Node
     */
    nextSibling: null,

    /**
     * We can set the node up to call an external method to get the child
     * data dynamically.
     *
     * @type boolean
     * @private
     */
    _dynLoad: false,

    /**
     * Function to execute when we need to get this node's child data.
     *
     * @type function
     */
    dataLoader: null,

    /**
     * This is true for dynamically loading nodes while waiting for the
     * callback to return.
     *
     * @type boolean
     */
    isLoading: false,

    /**
     * The toggle/branch icon will not show if this is set to false.  This
     * could be useful if the implementer wants to have the child contain
     * extra info about the parent, rather than an actual node.
     *
     * @type boolean
     */
    hasIcon: true,

    /**
     * Initializes this node, gets some of the properties from the parent
     *
     * @param oData {object} a string or object containing the data that will
     * be used to render this node
     * @param oParent {YAHOO.widget.Node} this node's parent node
     * @param expanded {boolean} the initial expanded/collapsed state
     */
    init: function(oData, oParent, expanded) {
        this.data		= oData;
        this.children	= [];
        this.index		= YAHOO.widget.TreeView.nodeCount;
        ++YAHOO.widget.TreeView.nodeCount;
        this.expanded	= expanded;

        // oParent should never be null except when we create the root node.
        if (oParent) {
            this.tree			= oParent.tree;
            this.parent			= oParent;
            this.href			= "javascript:" + this.getToggleLink();
            this.depth			= oParent.depth + 1;
            this.multiExpand	= oParent.multiExpand;

            oParent.appendChild(this);
        }
    },

    /**
     * Appends a node to the child collection.
     *
     * @param node {YAHOO.widget.Node} the new node
     * @return {YAHOO.widget.Node} the child node
     * @private
     */
    appendChild: function(node) {
        if (this.hasChildren()) {
            var sib = this.children[this.children.length - 1];
            sib.nextSibling = node;
            node.previousSibling = sib;
        }

        this.tree.regNode(node);
        this.children[this.children.length] = node;
        return node;

    },

    /**
     * Returns a node array of this node's siblings, null if none.
     *
     * @return YAHOO.widget.Node[]
     */
    getSiblings: function() {
        return this.parent.children;
    },

    /**
     * Shows this node's children
     */
    showChildren: function() {
        if (!this.tree.animateExpand(this.getChildrenEl())) {
            if (this.hasChildren()) {
                this.getChildrenEl().style.display = "";
            }
        }
    },

    /**
     * Hides this node's children
     */
    hideChildren: function() {

        if (!this.tree.animateCollapse(this.getChildrenEl())) {
            this.getChildrenEl().style.display = "none";
        }
    },

    /**
     * Returns the id for this node's container div
     *
     * @return {string} the element id
     */
    getElId: function() {
        return "ygtv" + this.index;
    },

    /**
     * Returns the id for this node's children div
     *
     * @return {string} the element id for this node's children div
     */
    getChildrenElId: function() {
        return "ygtvc" + this.index;
    },

    /**
     * Returns the id for this node's toggle element
     *
     * @return {string} the toggel element id
     */
    getToggleElId: function() {
        return "ygtvt" + this.index;
    },

    /**
     * Returns this node's container html element
     *
     * @return {Object} the container html element
     */
    getEl: function() {
        return document.getElementById(this.getElId());
    },

    /**
     * Returns the div that was generated for this node's children
     *
     * @return {Object} this node's children div
     */
    getChildrenEl: function() {
        return document.getElementById(this.getChildrenElId());
    },

    /**
     * Returns the element that is being used for this node's toggle.
     *
     * @return {Object} this node's toggel html element
     */
    getToggleEl: function() {
        return document.getElementById(this.getToggleElId());
    },

    /**
     * Generates the link that will invoke this node's toggle method
     *
     * @return {string} the javascript url for toggling this node
     */
    getToggleLink: function() {
        return "YAHOO.widget.TreeView.getNode(\'" + this.tree.id + "\'," +
            this.index + ").toggle()";
    },

    /**
     * Hides this nodes children (creating them if necessary), changes the
     * toggle style.
     */
    collapse: function() {
        // Only collapse if currently expanded
        if (!this.expanded) { return; }

        if (!this.getEl()) {
            this.expanded = false;
            return;
        }

        // hide the child div
        this.hideChildren();
        this.expanded = false;

        if (this.hasIcon) {
            this.getToggleEl().className = this.getStyle();
        }

        // fire the collapse event handler
        this.tree.onCollapse(this);
    },

    /**
     * Shows this nodes children (creating them if necessary), changes the
     * toggle style, and collapses its siblings if multiExpand is not set.
     */
    expand: function() {
        // Only expand if currently collapsed.
        if (this.expanded) { return; }

        if (!this.getEl()) {
            this.expanded = true;
            return;
        }

        if (! this.childrenRendered) {
            this.getChildrenEl().innerHTML = this.renderChildren();
        }

        this.expanded = true;
        if (this.hasIcon) {
            this.getToggleEl().className = this.getStyle();
        }

        // We do an extra check for children here because the lazy
        // load feature can expose nodes that have no children.

        // if (!this.hasChildren()) {
        if (this.isLoading) {
            this.expanded = false;
            return;
        }

        if (! this.multiExpand) {
            var sibs = this.getSiblings();
            for (var i=0; i<sibs.length; ++i) {
                if (sibs[i] != this && sibs[i].expanded) {
                    sibs[i].collapse();
                }
            }
        }

        this.showChildren();

        // fire the expand event handler
        this.tree.onExpand(this);
    },

    /**
     * Returns the css style name for the toggle
     *
     * @return {string} the css class for this node's toggle
     */
    getStyle: function() {
        if (this.isLoading) {
            return "ygtvloading";
        } else {
            // location top or bottom, middle nodes also get the top style
            var loc = (this.nextSibling) ? "t" : "l";

            // type p=plus(expand), m=minus(collapase), n=none(no children)
            var type = "n";
            if (this.hasChildren(true) || this.isDynamic()) {
                type = (this.expanded) ? "m" : "p";
            }

            return "ygtv" + loc + type;
        }
    },

    /**
     * Returns the hover style for the icon
     * @return {string} the css class hover state
     */
    getHoverStyle: function() {
        var s = this.getStyle();
        if (this.hasChildren(true) && !this.isLoading) {
            s += "h";
        }
        return s;
    },

    /**
     * Recursively expands all of this node's children.
     */
    expandAll: function() {
        for (var i=0;i<this.children.length;++i) {
            var c = this.children[i];
            if (c.isDynamic()) {
                alert("Not supported (lazy load + expand all)");
                break;
            } else if (! c.multiExpand) {
                alert("Not supported (no multi-expand + expand all)");
                break;
            } else {
                c.expand();
                c.expandAll();
            }
        }
    },

    /**
     * Recursively collapses all of this node's children.
     */
    collapseAll: function() {
        for (var i=0;i<this.children.length;++i) {
            this.children[i].collapse();
            this.children[i].collapseAll();
        }
    },

    /**
     * Configures this node for dynamically obtaining the child data
     * when the node is first expanded.
     *
     * @param fmDataLoader {function} the function that will be used to get the data.
     */
    setDynamicLoad: function(fnDataLoader) {
        this.dataLoader = fnDataLoader;
        this._dynLoad = true;
    },

    /**
     * Evaluates if this node is the root node of the tree
     *
     * @return {boolean} true if this is the root node
     */
    isRoot: function() {
        return (this == this.tree.root);
    },

    /**
     * Evaluates if this node's children should be loaded dynamically.  Looks for
     * the property both in this instance and the root node.  If the tree is
     * defined to load all children dynamically, the data callback function is
     * defined in the root node
     *
     * @return {boolean} true if this node's children are to be loaded dynamically
     */
    isDynamic: function() {
        var lazy = (!this.isRoot() && (this._dynLoad || this.tree.root._dynLoad));
        return lazy;
    },

    /**
     * Checks if this node has children.  If this node is lazy-loading and the
     * children have not been rendered, we do not know whether or not there
     * are actual children.  In most cases, we need to assume that there are
     * children (for instance, the toggle needs to show the expandable
     * presentation state).  In other times we want to know if there are rendered
     * children.  For the latter, "checkForLazyLoad" should be false.
     *
     * @param checkForLazyLoad {boolean} should we check for unloaded children?
     * @return {boolean} true if this has children or if it might and we are
     * checking for this condition.
     */
    hasChildren: function(checkForLazyLoad) {
        return ( this.children.length > 0 ||
                (checkForLazyLoad && this.isDynamic() && !this.childrenRendered) );
    },

    /**
     * Expands if node is collapsed, collapses otherwise.
     */
    toggle: function() {
        if (!this.tree.locked && ( this.hasChildren(true) || this.isDynamic()) ) {
            if (this.expanded) { this.collapse(); } else { this.expand(); }
        }
    },

    /**
     * Returns the markup for this node and its children.
     *
     * @return {string} the markup for this node and its expanded children.
     */
    getHtml: function() {
        var sb = [];
        sb[sb.length] = '<div class="ygtvitem" id="' + this.getElId() + '">';
        sb[sb.length] = this.getNodeHtml();
        sb[sb.length] = this.getChildrenHtml();
        sb[sb.length] = '</div>';
        return sb.join("");
    },

    /**
     * Called when first rendering the tree.  We always build the div that will
     * contain this nodes children, but we don't render the children themselves
     * unless this node is expanded.
     *
     * @return {string} the children container div html and any expanded children
     * @private
     */
    getChildrenHtml: function() {
        var sb = [];
        sb[sb.length] = '<div class="ygtvchildren"';
        sb[sb.length] = ' id="' + this.getChildrenElId() + '"';
        if (!this.expanded) {
            sb[sb.length] = ' style="display:none;"';
        }
        sb[sb.length] = '>';

        // Don't render the actual child node HTML unless this node is expanded.
        if (this.hasChildren(true) && this.expanded) {
            sb[sb.length] = this.renderChildren();
        }

        sb[sb.length] = '</div>';

        return sb.join("");
    },

    /**
     * Generates the markup for the child nodes.  This is not done until the node
     * is expanded.
     *
     * @return {string} the html for this node's children
     * @private
     */
    renderChildren: function() {


        var node = this;

        if (this.isDynamic() && !this.childrenRendered) {
            this.isLoading = true;
            this.tree.locked = true;

            if (this.dataLoader) {
                setTimeout(
                    function() {
                        node.dataLoader(node,
                            function() {
                                node.loadComplete();
                            });
                    }, 10);

            } else if (this.tree.root.dataLoader) {

                setTimeout(
                    function() {
                        node.tree.root.dataLoader(node,
                            function() {
                                node.loadComplete();
                            });
                    }, 10);

            } else {
                return "Error: data loader not found or not specified.";
            }

            return "";

        } else {
            return this.completeRender();
        }
    },

    /**
     * Called when we know we have all the child data.
     * @return {string} children html
     */
    completeRender: function() {
        var sb = [];

        for (var i=0; i < this.children.length; ++i) {
            sb[sb.length] = this.children[i].getHtml();
        }

        this.childrenRendered = true;

        return sb.join("");
    },

    /**
     * Load complete is the callback function we pass to the data provider
     * in dynamic load situations.
     */
    loadComplete: function() {
        this.getChildrenEl().innerHTML = this.completeRender();
        this.isLoading = false;
        this.expand();
        this.tree.locked = false;
    },

    /**
     * Returns this node's ancestor at the specified depth.
     *
     * @param {int} depth the depth of the ancestor.
     * @return {YAHOO.widget.Node} the ancestor
     */
    getAncestor: function(depth) {
        if (depth >= this.depth || depth < 0)  {
            return null;
        }

        var p = this.parent;

        while (p.depth > depth) {
            p = p.parent;
        }

        return p;
    },

    /**
     * Returns the css class for the spacer at the specified depth for
     * this node.  If this node's ancestor at the specified depth
     * has a next sibling the presentation is different than if it
     * does not have a next sibling
     *
     * @param {int} depth the depth of the ancestor.
     * @return {string} the css class for the spacer
     */
    getDepthStyle: function(depth) {
        return (this.getAncestor(depth).nextSibling) ?
            "ygtvdepthcell" : "ygtvblankdepthcell";
    },

    /**
     * Get the markup for the node.  This is designed to be overrided so that we can
     * support different types of nodes.
     *
     * @return {string} the html for this node
     */
    getNodeHtml: function() {
        return "";
    }

};

/* Copyright (c) 2006 Yahoo! Inc. All rights reserved. */

/**
 * @class A custom YAHOO.widget.Node that handles the unique nature of
 * the virtual, presentationless root node.
 *
 * @extends YAHOO.widget.Node
 * @constructor
 */
YAHOO.widget.RootNode = function(oTree) {
	// Initialize the node with null params.  The root node is a
	// special case where the node has no presentation.  So we have
	// to alter the standard properties a bit.
	this.init(null, null, true);

	/**
	 * For the root node, we get the tree reference from as a param
	 * to the constructor instead of from the parent element.
	 *
	 * @type YAHOO.widget.TreeView
	 */
	this.tree = oTree;
};
YAHOO.widget.RootNode.prototype = new YAHOO.widget.Node();

// overrides YAHOO.widget.Node
YAHOO.widget.RootNode.prototype.getNodeHtml = function() {
	return "";
};

/* Copyright (c) 2006 Yahoo! Inc. All rights reserved. */

/**
 * @class The default node presentation.  The first parameter should be
 * either a string that will be used as the node's label, or an object
 * that has a string propery called label.  By default, the clicking the
 * label will toggle the expanded/collapsed state of the node.  By
 * changing the href property of the instance, this behavior can be
 * changed so that the label will go to the specified href.
 *
 * @extends YAHOO.widget.Node
 * @constructor
 * @param oData {object} a string or object containing the data that will
 * be used to render this node
 * @param oParent {YAHOO.widget.Node} this node's parent node
 * @param expanded {boolean} the initial expanded/collapsed state
 */
YAHOO.widget.TextNode = function(oData, oParent, expanded) {
	if (oParent) {
		this.init(oData, oParent, expanded);
		this.setUpLabel(oData);
	}
};

YAHOO.widget.TextNode.prototype = new YAHOO.widget.Node();

/**
 * The CSS class for the label href.  Defaults to ygtvlabel, but can be
 * overridden to provide a custom presentation for a specific node.
 *
 * @type string
 */
YAHOO.widget.TextNode.prototype.labelStyle = "ygtvlabel";

/**
 * The derived element id of the label for this node
 *
 * @type string
 */
YAHOO.widget.TextNode.prototype.labelElId = null;

/**
 * The text for the label.  It is assumed that the oData parameter will
 * either be a string that will be used as the label, or an object that
 * has a property called "label" that we will use.
 *
 * @type string
 */
YAHOO.widget.TextNode.prototype.label = null;

/**
 * Sets up the node label
 *
 * @param oData string containing the label, or an object with a label property
 */
YAHOO.widget.TextNode.prototype.setUpLabel = function(oData) {
	if (typeof oData == "string") {
		oData = { label: oData };
	}
	this.label = oData.label;

	// update the link
	if (oData.href) {
		this.href = oData.href;
	}

	// set the target
	if (oData.target) {
		this.target = oData.target;
	}

	this.labelElId = "ygtvlabelel" + this.index;
};

/**
 * Returns the label element
 *
 * @return {object} the element
 */
YAHOO.widget.TextNode.prototype.getLabelEl = function() {
	return document.getElementById(this.labelElId);
};

// overrides YAHOO.widget.Node
YAHOO.widget.TextNode.prototype.getNodeHtml = function() {
	var sb = new Array();

	sb[sb.length] = '<table border="0" cellpadding="0" cellspacing="0">';
	sb[sb.length] = '<tr>';

	for (i=0;i<this.depth;++i) {
		// sb[sb.length] = '<td class="ygtvdepthcell">&nbsp;</td>';
		sb[sb.length] = '<td class="' + this.getDepthStyle(i) + '">&nbsp;</td>';
	}

	var getNode = 'YAHOO.widget.TreeView.getNode(\'' +
					this.tree.id + '\',' + this.index + ')';

	sb[sb.length] = '<td';
	// sb[sb.length] = ' onselectstart="return false"';
	sb[sb.length] = ' id="' + this.getToggleElId() + '"';
	sb[sb.length] = ' class="' + this.getStyle() + '"';
	if (this.hasChildren(true)) {
		sb[sb.length] = ' onmouseover="this.className=';
		sb[sb.length] = getNode + '.getHoverStyle()"';
		sb[sb.length] = ' onmouseout="this.className=';
		sb[sb.length] = getNode + '.getStyle()"';
	}
	sb[sb.length] = ' onclick="javascript:' + this.getToggleLink() + '">&nbsp;';
	sb[sb.length] = '</td>';
	sb[sb.length] = '<td>';
	sb[sb.length] = '<a';
	sb[sb.length] = ' id="' + this.labelElId + '"';
	sb[sb.length] = ' class="' + this.labelStyle + '"';
	sb[sb.length] = ' href="' + this.href + '"';
	sb[sb.length] = ' target="' + this.target + '"';
	if (this.hasChildren(true)) {
		sb[sb.length] = ' onmouseover="document.getElementById(\'';
		sb[sb.length] = this.getToggleElId() + '\').className=';
		sb[sb.length] = getNode + '.getHoverStyle()"';
		sb[sb.length] = ' onmouseout="document.getElementById(\'';
		sb[sb.length] = this.getToggleElId() + '\').className=';
		sb[sb.length] = getNode + '.getStyle()"';
	}
	sb[sb.length] = ' >';
	sb[sb.length] = this.label;
	sb[sb.length] = '</a>';
	sb[sb.length] = '</td>';
	sb[sb.length] = '</tr>';
	sb[sb.length] = '</table>';

	return sb.join("");
};

/* Copyright (c) 2006 Yahoo! Inc. All rights reserved. */

/**
 * @class menu-specific implementation that differs in that only one sibling
 * can be expanded at a time.
 * @extends YAHOO.widget.TextNode
 * @constructor
 */
YAHOO.widget.MenuNode = function(oData, oParent, expanded) {
	if (oParent) {
		this.init(oData, oParent, expanded);
		this.setUpLabel(oData);
	}

	// Menus usually allow only one branch to be open at a time.
	this.multiExpand = false;

};

YAHOO.widget.MenuNode.prototype = new YAHOO.widget.TextNode();

/* Copyright (c) 2006 Yahoo! Inc. All rights reserved. */

/**
 * @class This implementation takes either a string or object for the
 * oData argument.  If is it a string, we will use it for the display
 * of this node (and it can contain any html code).  If the parameter
 * is an object, we look for a parameter called "html" that will be
 * used for this node's display.
 *
 * @extends YAHOO.widget.Node
 * @constructor
 * @param oData {object} a string or object containing the data that will
 * be used to render this node
 * @param oParent {YAHOO.widget.Node} this node's parent node
 * @param expanded {boolean} the initial expanded/collapsed state
 * @param hasIcon {boolean} specifies whether or not leaf nodes should
 * have an icon
 */
YAHOO.widget.HTMLNode = function(oData, oParent, expanded, hasIcon) {
	if (oParent) {
		this.init(oData, oParent, expanded);
		this.initContent(oData, hasIcon);
	}
};

YAHOO.widget.HTMLNode.prototype = new YAHOO.widget.Node();

/**
 * The CSS class for the label href.  Defaults to ygtvlabel, but can be
 * overridden to provide a custom presentation for a specific node.
 *
 * @type string
 */
YAHOO.widget.HTMLNode.prototype.contentStyle = "ygtvhtml";

/**
 * The generated id that will contain the data passed in by the implementer.
 *
 * @type string
 */
YAHOO.widget.HTMLNode.prototype.contentElId = null;

/**
 * The HTML content to use for this node's display
 *
 * @type string
 */
YAHOO.widget.HTMLNode.prototype.content = null;

/**
 * Sets up the node label
 *
 * @param {object} html string or object containing a html field
 * @param {boolean} hasIcon determines if the node will be rendered with an
 * icon or not
 */
YAHOO.widget.HTMLNode.prototype.initContent = function(oData, hasIcon) {
	if (typeof oData == "string") {
		oData = { html: oData };
	}

	this.html = oData.html;
	this.contentElId = "ygtvcontentel" + this.index;
	this.hasIcon = hasIcon;
};

/**
 * Returns the outer html element for this node's content
 *
 * @return {Object} the element
 */
YAHOO.widget.HTMLNode.prototype.getContentEl = function() {
	return document.getElementById(this.contentElId);
};

// overrides YAHOO.widget.Node
YAHOO.widget.HTMLNode.prototype.getNodeHtml = function() {
	var sb = new Array();

	sb[sb.length] = '<table border="0" cellpadding="0" cellspacing="0">';
	sb[sb.length] = '<tr>';

	for (i=0;i<this.depth;++i) {
		sb[sb.length] = '<td class="' + this.getDepthStyle(i) + '">&nbsp;</td>';
	}

	if (this.hasIcon) {
		sb[sb.length] = '<td';
		sb[sb.length] = ' id="' + this.getToggleElId() + '"';
		sb[sb.length] = ' class="' + this.getStyle() + '"';
		sb[sb.length] = ' onclick="javascript:' + this.getToggleLink() + '">&nbsp;';
		if (this.hasChildren(true)) {
			sb[sb.length] = ' onmouseover="this.className=';
			sb[sb.length] = 'YAHOO.widget.TreeView.getNode(\'';
			sb[sb.length] = this.tree.id + '\',' + this.index +  ').getHoverStyle()"';
			sb[sb.length] = ' onmouseout="this.className=';
			sb[sb.length] = 'YAHOO.widget.TreeView.getNode(\'';
			sb[sb.length] = this.tree.id + '\',' + this.index +  ').getStyle()"';
		}
		sb[sb.length] = '</td>';
	}

	sb[sb.length] = '<td';
	sb[sb.length] = ' id="' + this.contentElId + '"';
	sb[sb.length] = ' class="' + this.contentStyle + '"';
	sb[sb.length] = ' >';
	sb[sb.length] = this.html;
	sb[sb.length] = '</td>';
	sb[sb.length] = '</tr>';
	sb[sb.length] = '</table>';

	return sb.join("");
};

/* Copyright (c) 2006 Yahoo! Inc. All rights reserved. */

/**
 * Static factory class for tree view expand/collapse animations
 */
YAHOO.widget.TVAnim = new function() {
	/**
	 * Constant for the fade in animation
	 *
	 * @type string
	 */
	this.FADE_IN  = "YAHOO.widget.TVFadeIn";

	/**
	 * Constant for the fade out animation
	 *
	 * @type string
	 */
	this.FADE_OUT = "YAHOO.widget.TVFadeOut";

	/**
	 * Returns a ygAnim instance of the given type
	 *
	 * @param type {string} the type of animation
	 * @param el {HTMLElement} the element to element (probably the children div)
	 * @param callback {function} function to invoke when the animation is done.
	 * @return {ygAnim} the animation instance
	 */
	this.getAnim = function(type, el, callback) {
		switch (type) {
			case this.FADE_IN:	return new YAHOO.widget.TVFadeIn(el, callback);
			case this.FADE_OUT:	return new YAHOO.widget.TVFadeOut(el, callback);
			default:			return null;
		}
	};

	/**
	 * Returns true if the specified animation class is available
	 *
	 * @param type {string} the type of animation
	 * @return {boolean} true if valid, false if not
	 */
	this.isValid = function(type) {
		return ( "undefined" != eval("typeof " + type) );
	};
};

/* Copyright (c) 2006 Yahoo! Inc. All rights reserved. */

/**
 * 1/2 second fade-in
 *
 * @constructor
 * @param el {HTMLElement} the element to animate
 * @param callback {function} function to invoke when the animation is finished
 */
YAHOO.widget.TVFadeIn = function(el, callback) {
	/**
	 * The animation dom ref
	 */
	this.el = el;

	/**
	 * the callback to invoke when the animation is complete
	 *
	 * @type function
	 */
	this.callback = callback;

	/**
	 * @private
	 */
};

/**
 * Performs the animation
 */
YAHOO.widget.TVFadeIn.prototype = {
    animate: function() {
        var tvanim = this;

        var s = this.el.style;
        s.opacity = 0.1;
        s.filter = "alpha(opacity=10)";
        s.display = "";

        // var dur = ( navigator.userAgent.match(/msie/gi) ) ? 0.05 : 0.4;
        var dur = 0.4;
        // var a = new ygAnim_Fade(this.el, dur, 1);
        // a.setStart(0.1);
        // a.onComplete = function() { tvanim.onComplete(); };

        // var a = new YAHOO.util.Anim(this.el, 'opacity', 0.1, 1);
        var a = new YAHOO.util.Anim(this.el, {opacity: {from: 0.1, to: 1, unit:""}}, dur);
        a.onComplete.subscribe( function() { tvanim.onComplete(); } );
        a.animate();
    },

    /**
     * Clean up and invoke callback
     */
    onComplete: function() {
        this.callback();
    }
};

/* Copyright (c) 2006 Yahoo! Inc. All rights reserved. */

/**
 * 1/2 second fade out
 *
 * @constructor
 * @param el {HTMLElement} the element to animate
 * @param callback {Function} function to invoke when the animation is finished
 */
YAHOO.widget.TVFadeOut = function(el, callback) {
	/**
	 * The animation dom ref
	 */
	this.el = el;

	/**
	 * the callback to invoke when the animation is complete
	 *
	 * @type function
	 */
	this.callback = callback;

	/**
	 * @private
	 */
};

/**
 * Performs the animation
 */
YAHOO.widget.TVFadeOut.prototype = {
    animate: function() {
        var tvanim = this;
        // var dur = ( navigator.userAgent.match(/msie/gi) ) ? 0.05 : 0.4;
        var dur = 0.4;
        // var a = new ygAnim_Fade(this.el, dur, 0.1);
        // a.onComplete = function() { tvanim.onComplete(); };

        // var a = new YAHOO.util.Anim(this.el, 'opacity', 1, 0.1);
        var a = new YAHOO.util.Anim(this.el, {opacity: {from: 1, to: 0.1, unit:""}}, dur);
        a.onComplete.subscribe( function() { tvanim.onComplete(); } );
        a.animate();
    },

    /**
     * Clean up and invoke callback
     */
    onComplete: function() {
        var s = this.el.style;
        s.display = "none";
        // s.opacity = 1;
        s.filter = "alpha(opacity=100)";
        this.callback();
    }
};

