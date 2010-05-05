/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.wc;

/**
 * <b>SVNStatusType</b> provides information about versioned items' 
 * status type. This class contains a set of predefined constants each of that 
 * should be compared with a refrence to an <b>SVNStatusType</b> to find 
 * out the item's status type. That is done either in event handlers
 * (implementing <b>ISVNEventHandler</b>) registered for <b>SVN</b>*<b>Client</b>
 * objects like this:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNEventHandler;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNStatusType;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNEventAction;
 * ...
 * 
 * <span class="javakeyword">public class</span> MyCustomEventHandler <span class="javakeyword">implements</span> ISVNEventHandler {
 *     <span class="javakeyword">public void</span> handleEvent(SVNEvent event, <span class="javakeyword">double</span> progress){    
 *         ...
 *         
 *         <span class="javakeyword">if</span>(event.getAction() == SVNEventAction.UPDATE_UPDATE){
 *            <span class="javacomment">//get contents status type</span>
 *            SVNStatusType contentsStatus = event.getContentsStatus();
 *            <span class="javacomment">//parse it</span>
 *            <span class="javakeyword">if</span>(contentsStatus != SVNStatusType.INAPPLICABLE){
 *                <span class="javakeyword">if</span>(contentsStatus == SVNStatusType.CONFLICTED){
 *                    ...
 *                }
 *            }      
 *         
 *            <span class="javacomment">//get properties status type</span>
 *            SVNStatusType propertiesStatus = event.getPropertiesStatus();
 *            <span class="javacomment">//parse it</span>
 *            <span class="javakeyword">if</span>(propertiesStatus != SVNStatusType.INAPPLICABLE){
 *                <span class="javakeyword">if</span>(contentsStatus == SVNStatusType.CONFLICTED){
 *                    ...
 *                }
 *            }
 *         }
 *         ...
 *     }
 *     ...
 * }</pre>
 * <br>
 * or in a status handler (implementing <b>ISVNStatusHandler</b>) registered 
 * for an <b>SVNStatusClient</b> like this:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNStatusHandler;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNStatus;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNStatusType;
 * ...
 * 
 * <span class="javakeyword">public class</span> MyCustomStatusHandler <span class="javakeyword">implements</span> ISVNStatusHandler {
 *     <span class="javakeyword">public void</span> handleStatus(SVNStatus status){    
 *         ...
 *         
 *         <span class="javacomment">//get contents status type</span>
 *         SVNStatusType contentsStatus = status.getContentsStatus();
 *         <span class="javacomment">//parse it</span>
 *         <span class="javakeyword">if</span>(contentsStatus == SVNStatusType.STATUS_MODIFIED){
 *             ...
 *         }<span class="javakeyword">else if</span>(contentsStatus == SVNStatusType.STATUS_CONFLICTED){
 *             ...
 *         }      
 *         ...
 *         <span class="javacomment">//get properties status type</span>
 *         SVNStatusType propertiesStatus = status.getPropertiesStatus();
 *         <span class="javacomment">//parse it</span>
 *         <span class="javakeyword">if</span>(contentsStatus == SVNStatusType.STATUS_MODIFIED){
 *             ...
 *         }<span class="javakeyword">else if</span>(contentsStatus == SVNStatusType.STATUS_CONFLICTED){
 *             ...
 *         }
 *         ...
 *     }
 *     ...
 * }</pre>
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     SVNEvent
 * @see     SVNStatus
 */
public class SVNStatusType {

    private int myID;
    private String myName;
    private char myCode;

    private SVNStatusType(int id, String name) {
        this(id, name, ' ');
    }

    private SVNStatusType(int id, String name, char code) {
        myID = id;
        myName = name;
        myCode = code;
    }
    
    /**
     * Returns this object's identifier as an integer nbumber.
     * Each constant field of the <b>SVNStatusType</b> class is also an 
     * <b>SVNStatusType</b> object with its own id. 
     * 
     * @return id of this object 
     */
    public int getID() {
        return myID;
    }
    
    /**
     * Returns id of this object. 
     * 
     * @return id code
     */
    public char getCode() {
        return myCode;
    }
    
    /**
     * Returns a string representation of this object. As a matter of fact
     * this is a string representation of this object's id.
     * 
     * @return a string representing this object
     */
    public String toString() {
        return myName == null ? Integer.toString(myID) : myName;
    }
    
    /**
     * During some operations denotes that status info of item contents or
     * properties is inapplicable. For example, this takes place during a 
     * commit operation - if there is any {@link ISVNEventHandler} registered
     * for an {@link SVNCommitClient} then events that are dispatched to that event 
     * handler will have contents and properties status types set to <i>INAPPLICABLE</i>:
     * <pre class="javacode">
     * <span class="javakeyword">public class</span> MyCommitEventHandler <span class="javakeyword">implements</span> ISVNEventHandler{
     * ...    
     *     
     *     <span class="javakeyword">public void</span> handleEvent(SVNEvent event, <span class="javakeyword">double</span> progress){
     *         <span class="javacomment">//both are SVNStatusType.INAPPLICABLE</span>
     *         SVNStatusType contentsStatus = event.getContentsStatus();
     *         SVNStatusType propsStatus = event.getPropertiesStatus();
     *     }
     * ...
     * }</pre> 
     *  
     */
    public static final SVNStatusType INAPPLICABLE = new SVNStatusType(0, "inapplicable");
    
    /**
     * Denotes that the resultant status of the operation is for some
     * reason unknown.
     */
    public static final SVNStatusType UNKNOWN = new SVNStatusType(1, "unknown");
    
    /**
     * During an operation denotes that file item contents or file/directory
     * item properties are not changed.  For example, in a Working Copy-to-URL copying.
     */
    public static final SVNStatusType UNCHANGED = new SVNStatusType(2, "unchanged");
    
    /**
     * Denotes that the item is versioned but missing (deleted from the 
     * fylesystem).
     */
    public static final SVNStatusType MISSING = new SVNStatusType(3, "missing");
    
    /**
     * Denotes that the item has an unexpected kind or somehow damaged or
     * can not be managed by an operation.
     */
    public static final SVNStatusType OBSTRUCTED = new SVNStatusType(4, "obstructed");
    
    /**
     * During an operation (like an update) denotes that the item contents
     * or item properties were changed.
     */
    public static final SVNStatusType CHANGED = new SVNStatusType(5, "changed");

    /**
     * During an operation (like an update or merge) denotes that the file 
     * item contents or file/directory item properties were merged 
     * with changes that came from the repository, so that local modifications 
     * and arrived ones do not overlap. 
     */
    public static final SVNStatusType MERGED = new SVNStatusType(6, "merged");

    /**
     * During an operation (like an update) denotes that the file item contents 
     * or file/directory item properties are in conflict with those changes that
     * came from the repository. 
     */
    public static final SVNStatusType CONFLICTED = new SVNStatusType(7, "conflicted");
    
    /**
     * Denotes that the conflict state on the item is still unresolved.
     * For example, it can be set when trying to merge into a file that is
     * in conflict with the repository.  
     */
    public static final SVNStatusType CONFLICTED_UNRESOLVED = new SVNStatusType(8, "conflicted_unresolved");
    
    /**
     * During some operations denotes that lock status is inapplicable. 
     * For example, this takes place during a commit operation - if there 
     * is any {@link ISVNEventHandler} registered for {@link SVNCommitClient} 
     * then events that are dispatched to that event handler will have the 
     * lock status type set to <i>LOCK_INAPPLICABLE</i>:
     * <pre class="javacode">
     * <span class="javakeyword">public class</span> MyCommitEventHandler <span class="javakeyword">implements</span> ISVNEventHandler{
     * ...    
     *     
     *     <span class="javakeyword">public void</span> handleEvent(SVNEvent event, <span class="javakeyword">double</span> progress){
     *         <span class="javacomment">//is SVNStatusType.LOCK_INAPPLICABLE</span>
     *         SVNStatusType lockStatus = event.getLockStatus();
     *     }
     * ...
     * }</pre> 
     */
    public static final SVNStatusType LOCK_INAPPLICABLE = new SVNStatusType(0, "lock_inapplicable");
    
    /**
     * No lock information is known.
     */
    public static final SVNStatusType LOCK_UNKNOWN = new SVNStatusType(1, "lock_unknown");
    
    /**
     * During an operation denotes that the lock status wasn't changed. For example, in a 
     * Working Copy-to-URL copying.
     */
    public static final SVNStatusType LOCK_UNCHANGED = new SVNStatusType(2, "lock_unchanged");
    
    /**
     * During an operation denotes that the file item's locked. 
     */
    public static final SVNStatusType LOCK_LOCKED = new SVNStatusType(3, "lock_locked");
    
    /**
     * During an operation (like an update) denotes that the file item's lock 
     * was broken in the repositry by some other user.
     */
    public static final SVNStatusType LOCK_UNLOCKED = new SVNStatusType(4, "lock_unlocked");
    
    /**
     * In a status operation denotes that no status type information is 
     * available. 
     */
    public static final SVNStatusType STATUS_NONE = new SVNStatusType(0, "none");

    /**
     * In a status operation (if it's being running with an option to report
     * of all items set to <span class="javakeyword">true</span>) denotes that the 
     * item in the Working Copy being currently processed has no local changes 
     * (in a normal state).  
     */
    public static final SVNStatusType STATUS_NORMAL = new SVNStatusType(1, "normal", ' ');
    
    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed has local modifications.
     */
    public static final SVNStatusType STATUS_MODIFIED = new SVNStatusType(2, "modified", 'M');

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is scheduled for addition to the repository.
     */
    public static final SVNStatusType STATUS_ADDED = new SVNStatusType(3, "added", 'A');

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is scheduled for deletion from the repository.
     */
    public static final SVNStatusType STATUS_DELETED = new SVNStatusType(4, "deleted", 'D');

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is not under version control.
     */
    public static final SVNStatusType STATUS_UNVERSIONED = new SVNStatusType(5, "unversioned", '?');

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is under version control but is missing  - for example, 
     * removed from the filesystem with a non-SVN, non-SVNKit or 
     * any other SVN non-compatible delete command).
     */
    public static final SVNStatusType STATUS_MISSING = new SVNStatusType(6, "missing", '!');
    
    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed was replaced by another item with the same name (within
     * a single revision the item was scheduled for deletion and then a new one with
     * the same name was scheduled for addition). Though they may have the same name
     * the items have their own distinct histories. 
     */
    public static final SVNStatusType STATUS_REPLACED = new SVNStatusType(7, "replaced", 'R');
    
    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is in a conflict state (local changes overlap those 
     * that came from the repository). The conflicting overlaps need to be manually
     * resolved.
     */
    public static final SVNStatusType STATUS_CONFLICTED = new SVNStatusType(9, "conflicted", 'C');
    
    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed has a non-expected kind. For example, a file is 
     * considered to be obstructed if it was deleted (with an SVN client non-compatible 
     * delete operation) and a directory with the same name as the file had had was added 
     * (but again with an SVN client non-compatible operation).
     */
    public static final SVNStatusType STATUS_OBSTRUCTED = new SVNStatusType(10, "obstructed", '~');

    /**
     * In a status operation denotes that the file item in the Working Copy being 
     * currently processed was set to be ignored (was added to svn:ignore property).
     */
    public static final SVNStatusType STATUS_IGNORED = new SVNStatusType(11, "ignored", 'I');

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is under version control but is somehow incomplete - 
     * for example, it may happen when the previous update was interrupted. 
     */
    public static final SVNStatusType STATUS_INCOMPLETE = new SVNStatusType(12, "incomplete", '!');

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is not under version control but is related to 
     * externals definitions. 
     */
    public static final SVNStatusType STATUS_EXTERNAL = new SVNStatusType(13, "external", 'X');
    
    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed was merged - that is it was applied the differences
     * (delta) between two sources in a merge operation.
     * 
     * @deprecated this status is never reported by 'status' operation 
     * in this version, 'update' and 'merge' uses {@link SVNStatusType#MERGED} instead. 
     *  
     */
    public static final SVNStatusType STATUS_MERGED = new SVNStatusType(8, "merged", 'G');

}
