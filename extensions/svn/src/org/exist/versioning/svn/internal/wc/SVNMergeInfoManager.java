/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.internal.wc;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSEntry;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSParentPath;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfoManager {
    
    public Map getMergeInfo(String[] paths, FSRevisionRoot root, SVNMergeInfoInheritance inherit, 
            boolean includeDescendants) throws SVNException {

        if (!root.getOwner().supportsMergeInfo()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                    "Querying mergeinfo requires version {0} of the FSFS filesystem schema;" +
                    " filesystem ''{1}'' uses only version {2}", 
                    new Object[] { new Integer(FSFS.MIN_MERGE_INFO_FORMAT), root.getOwner().getDBRoot(), 
                    new Integer(root.getOwner().getDBFormat()) });
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        Map mergeInfoAsHashes = getMergeInfoForPaths(root, paths, inherit, includeDescendants);
        Map mergeInfo = new TreeMap();
        for (Iterator mergeInfoIter = mergeInfoAsHashes.keySet().iterator(); mergeInfoIter.hasNext();) {
            String path = (String) mergeInfoIter.next();
            Map pathMergeInfo = (Map) mergeInfoAsHashes.get(path);
            mergeInfo.put(path, new SVNMergeInfo(path, pathMergeInfo));
        }
        return mergeInfo;
    }
    
    private Map getMergeInfoForPaths(FSRevisionRoot root, String[] paths, 
            SVNMergeInfoInheritance inherit, boolean includeDescendants) throws SVNException {
        Map result = new TreeMap();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            Map pathMergeInfo = getMergeInfoForPath(root, path, inherit);
            if (pathMergeInfo != null) {
                result.put(path, pathMergeInfo);
            }
            if (includeDescendants) {
                addDescendantMergeInfo(result, root, path);
            }
        }    
        return result;
    }

    private void addDescendantMergeInfo(Map result, FSRevisionRoot root, String path) throws SVNException {
        FSRevisionNode node = root.getRevisionNode(path);
        if (node.hasDescendantsWithMergeInfo()) {
            crawlDirectoryForMergeInfo(root, path, node, result);
        }
    }
    
    private Map crawlDirectoryForMergeInfo(FSRevisionRoot root, String path, FSRevisionNode node, 
            Map result) throws SVNException {
        FSFS fsfs = root.getOwner();
        Map entries = node.getDirEntries(fsfs);
        for (Iterator entriesIter = entries.values().iterator(); entriesIter.hasNext();) {
            FSEntry entry = (FSEntry) entriesIter.next();
            String kidPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(path, entry.getName()));
            FSRevisionNode kidNode = root.getRevisionNode(kidPath);
            if (kidNode.hasMergeInfo()) {
                SVNProperties propList = kidNode.getProperties(fsfs);
                String mergeInfoString = propList.getStringValue(SVNProperty.MERGE_INFO);
                if (mergeInfoString == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, 
                            "Node-revision #''{0}'' claims to have mergeinfo but doesn''t", entry.getId());
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }
                Map kidMergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(mergeInfoString), null);
                result.put(kidPath, kidMergeInfo);
            }
            if (kidNode.hasDescendantsWithMergeInfo()) {
                crawlDirectoryForMergeInfo(root, kidPath, kidNode, result);
            }
        }
        return result;
    }
    
    private Map getMergeInfoForPath(FSRevisionRoot revRoot, String path, SVNMergeInfoInheritance inherit) throws SVNException {
        Map mergeInfo = null;
        path = SVNPathUtil.canonicalizeAbsolutePath(path);
        FSParentPath parentPath = revRoot.openPath(path, true, true);
        if (inherit == SVNMergeInfoInheritance.NEAREST_ANCESTOR && parentPath.getParent() == null) {
            return mergeInfo;
        }
        
        FSParentPath nearestAncestor = null;
        if (inherit == SVNMergeInfoInheritance.NEAREST_ANCESTOR) {
            nearestAncestor = parentPath.getParent();
        } else {
            nearestAncestor = parentPath;
        }
        
        FSFS fsfs = revRoot.getOwner();
        while (true) {
            boolean hasMergeInfo = nearestAncestor.getRevNode().hasMergeInfo();
            if (hasMergeInfo) {
                break;
            }
            
            if (inherit == SVNMergeInfoInheritance.EXPLICIT) {
                return mergeInfo;
            }
            nearestAncestor = nearestAncestor.getParent();
            if (nearestAncestor == null) {
                return mergeInfo;
            }
        }
        
        SVNProperties propList = nearestAncestor.getRevNode().getProperties(fsfs);
        String mergeInfoString = propList.getStringValue(SVNProperty.MERGE_INFO);
        if (mergeInfoString == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, 
                    "Node-revision ''{0}@{1}'' claims to have mergeinfo but doesn''t", 
                    new Object[] { nearestAncestor.getAbsPath(), new Long(revRoot.getRevision()) });
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        if (nearestAncestor == parentPath) {
            return SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(mergeInfoString), null);
        } 
        
        Map tmpMergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(mergeInfoString), null); 
        tmpMergeInfo = SVNMergeInfoUtil.getInheritableMergeInfo(tmpMergeInfo, null, 
                SVNRepository.INVALID_REVISION, SVNRepository.INVALID_REVISION);
        mergeInfo = appendToMergedFroms(tmpMergeInfo, parentPath.getRelativePath(nearestAncestor));
        return mergeInfo;
    }
    
    private Map appendToMergedFroms(Map mergeInfo, String pathComponent) {
        Map result = new TreeMap(); 
        for (Iterator pathsIter = mergeInfo.keySet().iterator(); pathsIter.hasNext();) {
            String path = (String) pathsIter.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(path);
            result.put(SVNPathUtil.append(path, pathComponent), rangeList.dup());
        }
        return result;
    }
    
}
