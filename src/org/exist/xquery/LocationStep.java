/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xquery;

import java.util.Iterator;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.VirtualNodeSet;
import org.exist.dom.XMLUtil;
import org.exist.storage.ElementIndex;
import org.exist.storage.ElementValue;
import org.exist.storage.NotificationService;
import org.exist.storage.UpdateListener;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

/**
 * Processes all location path steps (like descendant::*, ancestor::XXX).
 * 
 * The results of the first evaluation of the expression are cached for the 
 * lifetime of the object and only reloaded if the context sequence
 * (as passed to the {@link #eval(Sequence, Item)} method) has changed.
 * 
 * @author wolf
 */
public class LocationStep extends Step {

	protected NodeSet currentSet = null;
	protected DocumentSet currentDocs = null;
	protected UpdateListener listener = null;
	
	protected Expression parent = null;
	
	// Fields for caching the last result
	protected CachedResult cached = null;
	
	protected int parentDeps = Dependency.UNKNOWN_DEPENDENCY;
	protected boolean preload = false;
	protected boolean inUpdate = false;
    
    //Cache for the current NodeTest type 
    private Integer nodeTestType = null;
    
	public LocationStep(XQueryContext context, int axis) {
		super(context, axis);
	}

	public LocationStep(XQueryContext context, int axis, NodeTest test) {
		super(context, axis, test);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		int deps = Dependency.CONTEXT_SET;
		for(Iterator i = predicates.iterator(); i.hasNext(); ) {
			deps |= ((Predicate)i.next()).getDependencies();
		}
		return deps;
	}
	
	/**
	 * If the current path expression depends on local variables
	 * from a for expression, we can optimize by preloading 
	 * entire element or attribute sets.
	 *  
	 * @return
	 */
	protected boolean preloadNodeSets() {
        //TODO : log elsewhere ?
        if (preload) {
            LOG.debug("Preloaded NodeSets");
            return true;
        }
        if (inUpdate)
            return false;        
        if ((parentDeps & Dependency.LOCAL_VARS) == Dependency.LOCAL_VARS) {
            LOG.debug("Preloaded NodeSets");
            return true;
        }           
        return false;
	}
	
	protected Sequence applyPredicate(
		Sequence outerSequence,
		Sequence contextSequence)
		throws XPathException {
		if(contextSequence == null)
			return Sequence.EMPTY_SEQUENCE;
        if (predicates.size() == 0)
            //Nothing to apply
            return contextSequence;
		Predicate pred;
		Sequence result = contextSequence;
		for (Iterator i = predicates.iterator(); i.hasNext();) {
            //TODO : log and/or profile ?
			pred = (Predicate) i.next();
			result = pred.evalPredicate(outerSequence, result, axis);
		}
		return result;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Step#analyze(org.exist.xquery.Expression)
     */
    public void analyze(Expression parent, int flags) throws XPathException {
        this.parent = parent;
        parentDeps = parent.getDependencies();
        if ((flags & IN_UPDATE) > 0)
            inUpdate = true;
        if((flags & SINGLE_STEP_EXECUTION) > 0) {
            preload = true;
        }
        //TODO : log somewhere ?
        super.analyze(parent, flags);
    }
    
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }            
        
        Sequence temp = NodeSet.EMPTY_SET;    
        
        if (contextItem != null)
			contextSequence = contextItem.toSequence();
        
		if(contextSequence == null) {
            if (context.getProfiler().isEnabled()) 
                context.getProfiler().end(this, "", temp);        
			return temp; 
        }
        
        //Try to return cached results
		if(cached != null && cached.isValid(contextSequence)) {            
			if (predicates.size() > 0) {
                applyPredicate(contextSequence, cached.getResult());  
                //TODO : return now ? -pb
            } else {
                if (context.getProfiler().isEnabled()) 
                    context.getProfiler().end(this, "Using cached result", cached.getResult());
                return cached.getResult();
            }           
		}        
		    
		if (needsComputation()) { 
    		switch (axis) {
    			case Constants.DESCENDANT_AXIS :
    			case Constants.DESCENDANT_SELF_AXIS :
    				temp = getDescendants(context, contextSequence.toNodeSet());
    				break;
    			case Constants.CHILD_AXIS :
    				temp = getChildren(context, contextSequence.toNodeSet());
    				break;			
                case Constants.ANCESTOR_SELF_AXIS : 
    			case Constants.ANCESTOR_AXIS  :
    				temp = getAncestors(context, contextSequence.toNodeSet());
    				break;
                case Constants.PARENT_AXIS :
                    temp = getParents(context, contextSequence.toNodeSet());
                    break;                    
    			case Constants.SELF_AXIS :
    				temp = getSelf(context, contextSequence.toNodeSet());
    				break;
    			case Constants.ATTRIBUTE_AXIS :    				
    			case Constants.DESCENDANT_ATTRIBUTE_AXIS :
    				temp = getAttributes(context, contextSequence.toNodeSet());
    				break;
                case Constants.PRECEDING_AXIS:
                    temp = getPreceding(context, contextSequence.toNodeSet());
                    break;                    
                case Constants.FOLLOWING_AXIS:
                    temp = getFollowing(context, contextSequence.toNodeSet());
                    break;                    
    			case Constants.PRECEDING_SIBLING_AXIS :
    			case Constants.FOLLOWING_SIBLING_AXIS :
    				temp = getSiblings(context, contextSequence.toNodeSet());
    				break;
    			default :
                    throw new IllegalArgumentException("Unsupported axis specified");
    		}               
        }      	
        
        //Caches the result
        if(contextSequence instanceof NodeSet) {
            //TODO : cache *after* removing duplicates ?
            cached = new CachedResult((NodeSet)contextSequence, temp);
            registerUpdateListener();
        }
        //Remove duplicate nodes
        temp.removeDuplicates(); 
        //Apply the predicate
        temp =  applyPredicate(contextSequence, temp);                 

        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", temp);
        
        return temp;
	}
    
	//Avoid unnecessary tests (these should be detected by the parser)
    private boolean needsComputation() {
        //TODO : log this ?
        switch (axis) {
            //Certainly not exhaustive
            case Constants.ANCESTOR_SELF_AXIS :
            case Constants.PARENT_AXIS :  
            case Constants.SELF_AXIS :
                if (nodeTestType == null)
                    nodeTestType = new Integer(test.getType());                
                if (nodeTestType.intValue() != Type.NODE && nodeTestType.intValue() != Type.ELEMENT )           
                    return false; 
        }   
        return true;
    }          

	/**
	 * @param context
	 * @param contextSet
	 * @return
	 */
	protected Sequence getSelf(XQueryContext context, NodeSet contextSet) {
		if(test.isWildcardTest()) {
            if (nodeTestType == null)
                nodeTestType = new Integer(test.getType());   
			if(nodeTestType.intValue() == Type.NODE) {
				if (inPredicate) {
					if (contextSet instanceof VirtualNodeSet) {
						((VirtualNodeSet) contextSet).setInPredicate(true);
						((VirtualNodeSet) contextSet).setSelfIsContext();
                    } else if(Type.subTypeOf(contextSet.getItemType(), Type.NODE)) {
						NodeProxy p;
						for (Iterator i = contextSet.iterator(); i.hasNext();) {
							p = (NodeProxy) i.next();
							if (test.matches(p))
								p.addContextNode(p);
						}
					}
				}
				return contextSet;
			} else {
//                NodeSet result = new ExtArrayNodeSet();
//                NodeProxy p;
//                for (Iterator i = contextSet.iterator(); i.hasNext(); ) {
//                    p = (NodeProxy) i.next();
//                    if (test.matches(p)) {
//                        result.add(p);
//                        p.addContextNode(p);
//                    }
//                }
//                return result;
				VirtualNodeSet vset = new VirtualNodeSet(axis, test, contextSet);
				vset.setInPredicate(inPredicate);
				return vset;
			}
		} else {            
			DocumentSet docs = getDocumentSet(contextSet);
		    NodeSelector selector = new SelfSelector(contextSet, inPredicate);
            ElementIndex index = context.getBroker().getElementIndex();
		    return index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), selector);
		}
	}

	protected NodeSet getAttributes(XQueryContext context, NodeSet contextSet) {		
		if (test.isWildcardTest()) {
            NodeSet result = new VirtualNodeSet(axis, test, contextSet);
			((VirtualNodeSet) result).setInPredicate(inPredicate);
            return result;
		// if there's just a single known node in the context, it is faster
	    // do directly search for the attribute in the parent node.
        } else if(axis == Constants.ATTRIBUTE_AXIS && contextSet.getLength() == 1
                && !(contextSet instanceof VirtualNodeSet)) {
            NodeProxy proxy = contextSet.get(0);
            if (proxy.getInternalAddress() != NodeProxy.UNKNOWN_NODE_ADDRESS)                
                return contextSet.directSelectAttribute(test.getName(), inPredicate);
        }       
        if (preloadNodeSets()) {
            DocumentSet docs = getDocumentSet(contextSet);
            if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) { 
                ElementIndex index = context.getBroker().getElementIndex();                          
                //TODO : why a null selector here ? We have one below !
                currentSet = index.findElementsByTagName(ElementValue.ATTRIBUTE, docs, test.getName(), null);  
                currentDocs = docs;      
                registerUpdateListener();
            }
            switch (axis) {
                case Constants.ATTRIBUTE_AXIS :
                    return currentSet.selectParentChild(contextSet, NodeSet.DESCENDANT, inPredicate);                              
                case Constants.DESCENDANT_ATTRIBUTE_AXIS :             
                    return currentSet.selectAncestorDescendant(contextSet, NodeSet.DESCENDANT, false, inPredicate);                    
                default:
                    throw new IllegalArgumentException("Unsupported axis specified");                   
            }       
		} else {
			NodeSelector selector;            
            DocumentSet docs = getDocumentSet(contextSet);
            //TODO : why a selector here ? We havn't one above !
            switch (axis) {
                case Constants.ATTRIBUTE_AXIS :
                    selector = new ChildSelector(contextSet, inPredicate);  
                    //TODO : find when this condition must return true !
                    //It should for :
                    //let $a := (<c id="1"><d id="2">d</d><e>e</e></c>)/descendant-or-self::node()
                    //for $b in $a/attribute::id
                    //return <b>{$b}</b>
                    //Note that it also works with selector = null
                    if (false) 
                        ((ChildSelector)selector).setMysteriousCondition();
                    break;
                case Constants.DESCENDANT_ATTRIBUTE_AXIS : 
                    selector = new DescendantSelector(contextSet, inPredicate); 
                    break;
               default:
                   throw new IllegalArgumentException("Unsupported axis specified");                   
			}    			
            ElementIndex index = context.getBroker().getElementIndex();
            return index.getAttributesByName(docs, test.getName(), selector);
            
		}    
	}

	protected NodeSet getChildren(XQueryContext context, NodeSet contextSet) {
		if (test.isWildcardTest()) {
			// test is one out of *, text(), node()
			VirtualNodeSet vset = new VirtualNodeSet(axis, test, contextSet);
			vset.setInPredicate(inPredicate);
			return vset;
		} else if (preloadNodeSets()) {            
			DocumentSet docs = getDocumentSet(contextSet);
			//TODO : understand why this one is different from the other ones
			if (currentSet == null || currentDocs == null || !(docs == currentDocs || docs.equals(currentDocs))) {
                ElementIndex index = context.getBroker().getElementIndex();                
                currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
                currentDocs = docs;
                registerUpdateListener();
            }
            return currentSet.selectParentChild(contextSet, NodeSet.DESCENDANT, inPredicate);
		} else {
            ElementIndex index = context.getBroker().getElementIndex();
		    DocumentSet docs = getDocumentSet(contextSet);
		    NodeSelector selector = new ChildSelector(contextSet, inPredicate);
		    return index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), selector);
		}
	}

	protected NodeSet getDescendants(XQueryContext context,	NodeSet contextSet) {
		if (test.isWildcardTest()) {
			// test is one out of *, text(), node()
			VirtualNodeSet vset = new VirtualNodeSet(axis, test, contextSet);
			vset.setInPredicate(inPredicate);
			return vset;
		} else if (preloadNodeSets()) {             
		    DocumentSet docs = getDocumentSet(contextSet);
            //TODO : understand why this one is different from the other ones
			if (currentSet == null || currentDocs == null || !(docs == currentDocs || docs.equals(currentDocs))) {
                ElementIndex index = context.getBroker().getElementIndex();                
                currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
                currentDocs = docs;
                registerUpdateListener();
            }            
            switch (axis) {
                case Constants.DESCENDANT_SELF_AXIS :                 
                    return currentSet.selectAncestorDescendant(contextSet, NodeSet.DESCENDANT, true, inPredicate);
                case Constants.DESCENDANT_AXIS :                
                    return currentSet.selectAncestorDescendant(contextSet, NodeSet.DESCENDANT, false, inPredicate);
                default:
                    throw new IllegalArgumentException("Unsupported axis specified");
            }            
		} else {            
            NodeSelector selector;
			DocumentSet docs = contextSet.getDocumentSet();            
            switch (axis) {
                case Constants.DESCENDANT_SELF_AXIS : 
                    selector = new DescendantOrSelfSelector(contextSet, inPredicate);
                    break;
                case Constants.DESCENDANT_AXIS :
                    selector = new DescendantSelector(contextSet, inPredicate);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported axis specified");                          
            }
            ElementIndex index = context.getBroker().getElementIndex();
			return index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), selector);
		}
	}

	protected NodeSet getSiblings(XQueryContext context, NodeSet contextSet) {		
		if (!test.isWildcardTest()) {            
		    DocumentSet docs = getDocumentSet(contextSet);
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
                ElementIndex index = context.getBroker().getElementIndex();				
				currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
                currentDocs = docs;
                registerUpdateListener();
			} 
            switch (axis) {
                case Constants.PRECEDING_SIBLING_AXIS :             
                    return currentSet.selectSiblings(contextSet, NodeSet.PRECEDING);                    
                case Constants.FOLLOWING_SIBLING_AXIS :
                    return currentSet.selectSiblings(contextSet, NodeSet.FOLLOWING);                   
                default :
                    throw new IllegalArgumentException("Unsupported axis specified");                   
            }			
		} else {
			NodeSet result = new ArraySet(contextSet.getLength());
			NodeProxy p, sib;
			NodeImpl n;
			for (Iterator i = contextSet.iterator(); i.hasNext();) {
				p = (NodeProxy) i.next();
				n = (NodeImpl) p.getNode();
				while ((n = getNextSibling(n)) != null) {
					if (test.matches(n)) {
						sib = new NodeProxy((DocumentImpl) n.getOwnerDocument(), n.getGID(),
								n.getInternalAddress());
                        if (inPredicate)
                            sib.addContextNode(p);
                        else
                            sib.copyContext(p);
                        result.add(sib);
					}
				}
			}
            return result;
		}		
	}

	protected NodeImpl getNextSibling(NodeImpl last) {
        switch (axis) {
            case Constants.FOLLOWING_SIBLING_AXIS :
                return (NodeImpl) last.getNextSibling();
            case Constants.PRECEDING_SIBLING_AXIS :             
                return (NodeImpl) last.getPreviousSibling();
            default :
                throw new IllegalArgumentException("Unsupported axis specified");                   
        }
	}

	protected NodeSet getFollowing(XQueryContext context, NodeSet contextSet) throws XPathException {		
		if(!test.isWildcardTest()) {            
		    DocumentSet docs = getDocumentSet(contextSet);
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
                ElementIndex index = context.getBroker().getElementIndex();				
				currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
                currentDocs = docs;
                registerUpdateListener();
			}
			return currentSet.selectFollowing(contextSet);
		}
        //TODO : throw an exception here ! Don't let this pass through
		return NodeSet.EMPTY_SET;
	}
	
    protected NodeSet getPreceding(XQueryContext context, NodeSet contextSet) throws XPathException {        
        if(!test.isWildcardTest()) {            
            DocumentSet docs = getDocumentSet(contextSet);
            if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
                ElementIndex index = context.getBroker().getElementIndex();                
                currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
                currentDocs = docs;
                registerUpdateListener();
            }
            return currentSet.selectPreceding(contextSet);
        }
        //TODO : throw an exception here ! Don't let this pass through
        return NodeSet.EMPTY_SET;
    }
    
	protected NodeSet getAncestors(XQueryContext context, NodeSet contextSet) {		
		if (test.isWildcardTest()) {
            NodeSet result = new ExtArrayNodeSet();
            NodeProxy p, ancestor;
            for (Iterator i = contextSet.iterator(); i.hasNext();) {
                p = (NodeProxy) i.next();
                if (axis == Constants.ANCESTOR_SELF_AXIS && test.matches(p)) {
                    ancestor = new NodeProxy(p.getDocument(), p.getGID(), p.getInternalAddress());
                    if (inPredicate)
                        ancestor.addContextNode(p);
                    else
                        ancestor.copyContext(p);
                    result.add(ancestor);
                }
                long parentID = XMLUtil.getParentId(p.getDocument(), p.getGID());               
                while (parentID > 0) {
                    ancestor = new NodeProxy(p.getDocument(), parentID);
                    ancestor.setNodeType(Node.ELEMENT_NODE);
                    if (test.matches(ancestor)) {
                        if (inPredicate)
                            ancestor.addContextNode(p);
                        else
                            ancestor.copyContext(p);
                        result.add(ancestor);                        
                    }
                    parentID = XMLUtil.getParentId(ancestor);
                }
            }
            return result;
        } else if (preloadNodeSets()) {            
            DocumentSet docs = getDocumentSet(contextSet);
            if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
                ElementIndex index = context.getBroker().getElementIndex();                
                currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
                currentDocs = docs;
                registerUpdateListener();
            }           
            switch (axis) {
                case Constants.ANCESTOR_SELF_AXIS :
                    return currentSet.selectAncestors(contextSet, true, inPredicate);                   
                case Constants.ANCESTOR_AXIS :             
                    return currentSet.selectAncestors(contextSet, false, inPredicate);                    
                default :
                    throw new IllegalArgumentException("Unsupported axis specified");                   
            }        
		} else {
            NodeSelector selector;            
            DocumentSet docs = getDocumentSet(contextSet);
            switch (axis) {
                case Constants.ANCESTOR_SELF_AXIS :
                    selector = new AncestorSelector(contextSet, inPredicate, true); 
                    break;
                case Constants.ANCESTOR_AXIS :             
                    selector = new AncestorSelector(contextSet, inPredicate, false);
                    break;
                default :
                    throw new IllegalArgumentException("Unsupported axis specified");                   
            }
            ElementIndex index = context.getBroker().getElementIndex();
            return index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), selector);
		}		
	}

	protected NodeSet getParents(XQueryContext context, NodeSet contextSet) {        
		if(test.isWildcardTest()) {
			return contextSet.getParents(inPredicate);
		} else {            
		    DocumentSet docs = getDocumentSet(contextSet);
		    NodeSelector selector = new ParentSelector(contextSet, inPredicate);
            ElementIndex index = context.getBroker().getElementIndex();
		    return index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), selector);			
		}
	}

	protected DocumentSet getDocumentSet(NodeSet contextSet) {
	    DocumentSet ds = getContextDocSet();
	    if(ds == null)
            ds = contextSet.getDocumentSet();
	    return ds;
	}
	
	protected void registerUpdateListener() {
		if (listener == null) {
			listener = new UpdateListener() {
				public void documentUpdated(DocumentImpl document, int event) {
					if (event == UpdateListener.ADD) {
						// clear all
						currentDocs = null;
						currentSet = null;
						cached = null;
					} else {
						if (currentDocs != null && currentDocs.contains(document.getDocId())) {
							currentDocs = null;
							currentSet = null;
						}
						if (cached != null && cached.getResult().getDocumentSet().contains(document.getDocId()))
							cached = null;
					}
				};
			};
			NotificationService service = context.getBroker().getBrokerPool().getNotificationService();
			service.subscribe(listener);
		}
	}
	
	protected void deregisterUpdateListener() {
		if (listener != null) {
			NotificationService service = context.getBroker().getBrokerPool().getNotificationService();
			service.unsubscribe(listener);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Step#resetState()
	 */
	public void resetState() {
        //TODO : uncomment some comments ? 
		super.resetState();
		currentSet = null;
		currentDocs = null;
        //listener = null; 
		//parent = null;        
        cached = null;   
        //parentDeps = Dependency.UNKNOWN_DEPENDENCY;
        //preload = false; 
        //inUpdate = false; 
        //nodeTestType = null;       
	}
}
