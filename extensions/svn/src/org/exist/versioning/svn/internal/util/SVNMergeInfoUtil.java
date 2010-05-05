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
package org.exist.versioning.svn.internal.util;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.exist.versioning.svn.internal.wc.SVNCommitUtil;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNPropertiesManager;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfoUtil {

    public static Map filterCatalogByRanges(Map catalog, long youngestRev, long oldestRev) {
        Map filteredCatalog = new TreeMap();
        for (Iterator catalogIter = catalog.keySet().iterator(); catalogIter.hasNext();) {
            String path = (String) catalogIter.next();
            Map mergeInfo = (Map) catalog.get(path);
            Map filteredMergeInfo = filterMergeInfoByRanges(mergeInfo, youngestRev, oldestRev);
            if (!filteredMergeInfo.isEmpty()) {
                filteredCatalog.put(path, filteredMergeInfo);
            }
        }
        return filteredCatalog;
    }
    
    public static Map filterMergeInfoByRanges(Map mergeInfo, long youngestRev, long oldestRev) {
        Map filteredMergeInfo = new TreeMap();
        if (mergeInfo != null) {
            SVNMergeRange range = new SVNMergeRange(oldestRev, youngestRev, true);
            SVNMergeRangeList filterRangeList = new SVNMergeRangeList(range);
            for (Iterator mergeInfoIter = mergeInfo.keySet().iterator(); mergeInfoIter.hasNext();) {
                String path = (String) mergeInfoIter.next();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(path);
                if (!rangeList.isEmpty()) {
                    SVNMergeRangeList newRangeList = filterRangeList.intersect(rangeList, false);
                    if (!newRangeList.isEmpty()) {
                        filteredMergeInfo.put(path, newRangeList);
                    }
                }
            }
        }
        return filteredMergeInfo;
    }
    
    public static long[] getRangeEndPoints(Map mergeInfo) {
        //long[] { youngestRange, oldestRange }
        long[] rangePoints = { SVNRepository.INVALID_REVISION, SVNRepository.INVALID_REVISION };
        
        if (mergeInfo != null) {
            for (Iterator mergeInfoIter = mergeInfo.keySet().iterator(); mergeInfoIter.hasNext();) {
                String path = (String) mergeInfoIter.next();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(path);
                if (!rangeList.isEmpty()) {
                    SVNMergeRange[] ranges = rangeList.getRanges();
                    SVNMergeRange range = ranges[ranges.length - 1];
                    if (!SVNRevision.isValidRevisionNumber(rangePoints[0]) || range.getEndRevision() > rangePoints[0]) {
                        rangePoints[0] = range.getEndRevision(); 
                    }
                    
                    range = ranges[0];
                    if (!SVNRevision.isValidRevisionNumber(rangePoints[1]) || rangePoints[1] > range.getStartRevision()) {
                        rangePoints[1] = range.getStartRevision();
                    }
                }
            }
        }
        
        return rangePoints;
    }
    
	public static Map elideMergeInfoCatalog(Map mergeInfoCatalog) throws SVNException {
	    Map adjustedMergeInfoCatalog = new TreeMap();
	    for (Iterator pathsIter = mergeInfoCatalog.keySet().iterator(); pathsIter.hasNext();) {
	        String path = (String) pathsIter.next();
	        String adjustedPath = path;
	        if (path.startsWith("/")) {
	            adjustedPath = path.substring(1);
	        }
	        adjustedMergeInfoCatalog.put(adjustedPath, mergeInfoCatalog.get(path));
	    }
	    mergeInfoCatalog = adjustedMergeInfoCatalog;
	    ElideMergeInfoCatalogHandler handler = new ElideMergeInfoCatalogHandler(mergeInfoCatalog);
	    ElideMergeInfoEditor editor = new ElideMergeInfoEditor(mergeInfoCatalog);
	    SVNCommitUtil.driveCommitEditor(handler, mergeInfoCatalog.keySet(), editor, -1);
	    List elidablePaths = handler.getElidablePaths();
	    for (Iterator elidablePathsIter = elidablePaths.iterator(); elidablePathsIter.hasNext();) {
            String elidablePath = (String) elidablePathsIter.next();
            mergeInfoCatalog.remove(elidablePath);
        }
        
	    adjustedMergeInfoCatalog = new TreeMap();
        for (Iterator pathsIter = mergeInfoCatalog.keySet().iterator(); pathsIter.hasNext();) {
            String path = (String) pathsIter.next();
            String adjustedPath = path;
            if (!path.startsWith("/")) {
                adjustedPath = "/" + adjustedPath;
            }
            adjustedMergeInfoCatalog.put(adjustedPath, mergeInfoCatalog.get(path));
        }
	    return adjustedMergeInfoCatalog;
	}
	
    public static Map adjustMergeInfoSourcePaths(Map mergeInfo, String walkPath, Map wcMergeInfo) {
        mergeInfo = mergeInfo == null ? new TreeMap() : mergeInfo;
		for (Iterator paths = wcMergeInfo.keySet().iterator(); paths.hasNext();) {
            String srcMergePath = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) wcMergeInfo.get(srcMergePath); 
            mergeInfo.put(SVNPathUtil.getAbsolutePath(SVNPathUtil.append(srcMergePath, walkPath)), rangeList);
        }
		return mergeInfo;
	}
	
	public static boolean removeEmptyRangeLists(Map mergeInfo) {
		boolean removedSomeRanges = false;
		if (mergeInfo != null) {
			for (Iterator mergeInfoIter = mergeInfo.entrySet().iterator(); mergeInfoIter.hasNext();) {
				Map.Entry mergeInfoEntry = (Map.Entry) mergeInfoIter.next();
				SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfoEntry.getValue();
				if (rangeList.isEmpty()) {
					mergeInfoIter.remove();
					removedSomeRanges = true;
				}
			}
		}
		return removedSomeRanges;
	}
	
    public static Map mergeMergeInfos(Map originalSrcsToRangeLists, Map changedSrcsToRangeLists) throws SVNException {
        originalSrcsToRangeLists = originalSrcsToRangeLists == null ? new TreeMap() : originalSrcsToRangeLists;
        changedSrcsToRangeLists = changedSrcsToRangeLists == null ? Collections.EMPTY_MAP : changedSrcsToRangeLists;
        String[] paths1 = (String[]) originalSrcsToRangeLists.keySet().toArray(new String[originalSrcsToRangeLists.size()]);
        String[] paths2 = (String[]) changedSrcsToRangeLists.keySet().toArray(new String[changedSrcsToRangeLists.size()]);
        int i = 0;
        int j = 0;
        while (i < paths1.length && j < paths2.length) {
            String path1 = paths1[i];
            String path2 = paths2[j];
            int res = path1.compareTo(path2);
            if (res == 0) {
                SVNMergeRangeList rangeList1 = (SVNMergeRangeList) originalSrcsToRangeLists.get(path1);
                SVNMergeRangeList rangeList2 = (SVNMergeRangeList) changedSrcsToRangeLists.get(path2);
                rangeList1 = rangeList1.merge(rangeList2);
                originalSrcsToRangeLists.put(path1, rangeList1);
                i++;
                j++;
            } else if (res < 0) {
                i++;
            } else {
                originalSrcsToRangeLists.put(path2, changedSrcsToRangeLists.get(path2));
                j++;
            }
        }
        
        for (; j < paths2.length; j++) {
            String path = paths2[j];
            originalSrcsToRangeLists.put(path, changedSrcsToRangeLists.get(path));
        }
        return originalSrcsToRangeLists;
    }
    
    public static String combineMergeInfoProperties(String propValue1, String propValue2) throws SVNException {
        Map srcsToRanges1 = parseMergeInfo(new StringBuffer(propValue1), null);
        Map srcsToRanges2 = parseMergeInfo(new StringBuffer(propValue2), null);
        srcsToRanges1 = mergeMergeInfos(srcsToRanges1, srcsToRanges2);
        return formatMergeInfoToString(srcsToRanges1, null);
    }
    
    public static String combineForkedMergeInfoProperties(String fromPropValue, String workingPropValue, 
            String toPropValue) throws SVNException {
        Map leftDeleted = new TreeMap();
        Map leftAdded = new TreeMap();
        Map fromMergeInfo = parseMergeInfo(new StringBuffer(fromPropValue), null);
        diffMergeInfoProperties(leftDeleted, leftAdded, null, fromMergeInfo, workingPropValue, null);
        
        Map rightDeleted = new TreeMap();
        Map rightAdded = new TreeMap();
        diffMergeInfoProperties(rightDeleted, rightAdded, fromPropValue, null, toPropValue, null);
        leftDeleted = mergeMergeInfos(leftDeleted, rightDeleted);
        leftAdded = mergeMergeInfos(leftAdded, rightAdded);
        fromMergeInfo = mergeMergeInfos(fromMergeInfo, leftAdded);
        Map result = removeMergeInfo(leftDeleted, fromMergeInfo);
        return formatMergeInfoToString(result, null);
    }
    
    public static void diffMergeInfoProperties(Map deleted, Map added, String fromPropValue, Map fromMergeInfo, String toPropValue, Map toMergeInfo) throws SVNException {
        if (fromPropValue != null && fromPropValue.equals(toPropValue)) {
            return;
        } 
        fromMergeInfo = fromMergeInfo == null ? parseMergeInfo(new StringBuffer(fromPropValue), null) : fromMergeInfo;
        toMergeInfo = toMergeInfo == null ? parseMergeInfo(new StringBuffer(toPropValue), null) : toMergeInfo;
        diffMergeInfo(deleted, added, fromMergeInfo, toMergeInfo, true);
    }
    
    public static void diffMergeInfo(Map deleted, Map added, Map from, Map to, 
            boolean considerInheritance) {
        from = from == null ? Collections.EMPTY_MAP : from;
        to = to == null ? Collections.EMPTY_MAP : to;
        if (!from.isEmpty() && to.isEmpty()) {
            dupMergeInfo(from, deleted);
        } else if (from.isEmpty() && !to.isEmpty()) {
            dupMergeInfo(to, added);
        } else if (!from.isEmpty() && !to.isEmpty()) {
            walkMergeInfoHashForDiff(deleted, added, from, to, considerInheritance);
        }
    }
    
    public static Map dupCatalog(Map catalog) {
        Map newMergeInfoCatalog = new TreeMap();
        for (Iterator catalogIter = catalog.keySet().iterator(); catalogIter.hasNext();) {
            String path = (String) catalogIter.next();
            Map mergeInfo = (Map) catalog.get(path);
            Map mergeInfoCopy = dupMergeInfo(mergeInfo, null);
            newMergeInfoCatalog.put(path, mergeInfoCopy);
        }
        return newMergeInfoCatalog;
    }
    
    public static Map dupMergeInfo(Map srcsToRangeLists, Map target) {
        if (srcsToRangeLists == null) {
            return null;
        }
        target = target == null ? new TreeMap() : target;
        for (Iterator paths = srcsToRangeLists.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) srcsToRangeLists.get(path);
            target.put(path, rangeList.dup());
        }
        return target;
    }
    
    public static Map parseMergeInfo(StringBuffer mergeInfo, Map srcPathsToRangeLists) throws SVNException {
        srcPathsToRangeLists = srcPathsToRangeLists == null ? new TreeMap() : srcPathsToRangeLists;
        if (mergeInfo.length() == 0) {
            return srcPathsToRangeLists;
        }

        try {
            while (mergeInfo.length() > 0) {
                int eolInd = mergeInfo.indexOf("\n");
                eolInd = eolInd < 0 ? mergeInfo.length() - 1 : eolInd;
                int ind = mergeInfo.lastIndexOf(":", eolInd);
                if (ind == -1) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                            "Pathname not terminated by ':'");
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
                if (ind == 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                    "No pathname preceding ':'");
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);                    
                }
                String path = null;
                if (mergeInfo.charAt(0) =='/') {
                    path = mergeInfo.substring(0, ind);
                } else {
                    String relativePath = mergeInfo.substring(0, ind);
                    path = "/" + relativePath;
                }
                mergeInfo = mergeInfo.delete(0, ind + 1);
                SVNMergeRange[] ranges = parseRevisionList(mergeInfo, path);
                if (mergeInfo.length() != 0 && mergeInfo.charAt(0) != '\n') {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                            "Could not find end of line in range list line in ''{0}''", mergeInfo);
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
                if (mergeInfo.length() > 0) {
                    mergeInfo = mergeInfo.deleteCharAt(0);
                }
                if (ranges.length > 1) {
                    Arrays.sort(ranges);
                    SVNMergeRange lastRange = ranges[0];
                    Collection newRanges = new ArrayList();
                    newRanges.add(lastRange);                    
                    for (int i = 1; i < ranges.length; i++) {
                        SVNMergeRange range = ranges[i];
                        if (lastRange.getStartRevision() <= range.getEndRevision() &&
                                range.getStartRevision() <= lastRange.getEndRevision()) {
                            
                            if (range.getStartRevision() < lastRange.getEndRevision() &&
                                    range.isInheritable() != lastRange.isInheritable()) {
                                // error.
                                String r1 = lastRange.toString();
                                String r2 = range.toString();
                                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                                        "Unable to parse overlapping revision ranges ''{0}'' and ''{1}'' with different inheritance types", 
                                        new Object[] {r1, r2});
                                SVNErrorManager.error(err, SVNLogType.WC);
                            } 
                            
                            if (lastRange.isInheritable() == range.isInheritable()) {
                                lastRange.setEndRevision(Math.max(range.getEndRevision(), lastRange.getEndRevision()));
                                continue;
                            }
                        }
                        newRanges.add(ranges[i]);
                        lastRange = ranges[i];
                    }
                    ranges = (SVNMergeRange[]) newRanges.toArray(new SVNMergeRange[newRanges.size()]); 
                }
                SVNMergeRangeList existingRange = (SVNMergeRangeList) srcPathsToRangeLists.get(path);
                if (existingRange != null) {
                    ranges = existingRange.merge(new SVNMergeRangeList(ranges)).getRanges();
                }
                srcPathsToRangeLists.put(path, new SVNMergeRangeList(ranges));
            }
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.MERGE_INFO_PARSE_ERROR) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                        "Could not parse mergeinfo string ''{0}''", mergeInfo.toString());
                SVNErrorManager.error(err, svne, SVNLogType.DEFAULT);
            }
            throw svne;
        }
        return srcPathsToRangeLists;
    }

    /**
     * Note: Make sure that this method is used only for making up an error message. 
     */
    public static String formatMergeInfoCatalogToString(Map catalog, String keyPrefix, String valuePrefix) {
        StringBuffer buffer = null;
        if (catalog != null && !catalog.isEmpty()) {
            buffer = new StringBuffer();
            for (Iterator catalogIter = catalog.keySet().iterator(); catalogIter.hasNext();) {
                String path1 = (String) catalogIter.next();
                if (path1.startsWith("/")) {
                    path1 = path1.substring(1);
                }
                Map mergeInfo = (Map) catalog.get(path1);
                if (keyPrefix != null) {
                    buffer.append(keyPrefix);
                }
                buffer.append(path1);
                buffer.append('\n');
                buffer.append(formatMergeInfoToString(mergeInfo, valuePrefix != null ? valuePrefix : ""));
                buffer.append('\n');
            }
        }
        return buffer != null ? buffer.toString() : "\n";
    }
    
    /**
     * Each element of the resultant array is formed like this:
     * %s:%ld-%ld,.. where the first %s is a merge src path 
     * and %ld-%ld is startRev-endRev merge range.
     */
    public static String[] formatMergeInfoToArray(Map srcsToRangeLists, String prefix) {
        srcsToRangeLists = srcsToRangeLists == null ? Collections.EMPTY_MAP : srcsToRangeLists;
        String[] pathRanges = new String[srcsToRangeLists.size()];
        int k = 0;
        for (Iterator paths = srcsToRangeLists.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) srcsToRangeLists.get(path);
            String output = (prefix != null ? prefix : "") + (path.startsWith("/") ? "" : "/") + path + ':' + rangeList;  
            pathRanges[k++] = output;
        }
        return pathRanges;
    }

    public static String formatMergeInfoToString(Map srcsToRangeLists, String prefix) {
        String[] infosArray = formatMergeInfoToArray(srcsToRangeLists, prefix);
        String result = "";
        for (int i = 0; i < infosArray.length; i++) {
            result += infosArray[i];
            if (i < infosArray.length - 1) {
                result += '\n';
            }
        }
        return result;
    }

    public static boolean shouldElideMergeInfo(Map parentMergeInfo, Map childMergeInfo, String pathSuffix) {
        boolean elides = false;
        if (childMergeInfo != null) {
            if (childMergeInfo.isEmpty()) {
                if (parentMergeInfo == null || parentMergeInfo.isEmpty()) {
                    elides = true;
                }
            } else if (!(parentMergeInfo == null || parentMergeInfo.isEmpty())) {
                Map pathTweakedMergeInfo = parentMergeInfo;
                if (pathSuffix != null) {
                    pathTweakedMergeInfo = new TreeMap();
                    for (Iterator paths = parentMergeInfo.keySet().iterator(); paths.hasNext();) {
                        String mergeSrcPath = (String) paths.next();
                        pathTweakedMergeInfo.put(SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeSrcPath, 
                                pathSuffix)), parentMergeInfo.get(mergeSrcPath));
                    }
                } 
                elides = mergeInfoEquals(pathTweakedMergeInfo, childMergeInfo, true);
            }
        }
        return elides;
    }
    
    public static void elideMergeInfo(Map parentMergeInfo, Map childMergeInfo, File path, 
            String pathSuffix, SVNWCAccess access) throws SVNException {
        boolean elides = shouldElideMergeInfo(parentMergeInfo, childMergeInfo, pathSuffix);
        if (elides) {
            SVNPropertiesManager.setProperty(access, path, SVNProperty.MERGE_INFO, null, true);
        }
    }
    
    public static boolean mergeInfoEquals(Map mergeInfo1, Map mergeInfo2, 
            boolean considerInheritance) {
        mergeInfo1 = mergeInfo1 == null ? Collections.EMPTY_MAP : mergeInfo1;
        mergeInfo2 = mergeInfo2 == null ? Collections.EMPTY_MAP : mergeInfo2;
        
        if (mergeInfo1.size() == mergeInfo2.size()) {
            Map deleted = new SVNHashMap();
            Map added = new SVNHashMap();
            diffMergeInfo(deleted, added, mergeInfo1, mergeInfo2, considerInheritance);
            return deleted.isEmpty() && added.isEmpty();
        }
        return false;
    }
    
    public static String[] findMergeSources(long revision, Map mergeInfo) {
        LinkedList mergeSources = new LinkedList();
        for (Iterator paths = mergeInfo.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(path);
            if (rangeList.includes(revision)) {
                mergeSources.add(path);
            }
        }
        return (String[]) mergeSources.toArray(new String[mergeSources.size()]);
    }
    
    public static Map getInheritableMergeInfo(Map mergeInfo, String path, long startRev, long endRev) {
        Map inheritableMergeInfo = new TreeMap();
        if (mergeInfo != null) {
            for (Iterator paths = mergeInfo.keySet().iterator(); paths.hasNext();) {
                String mergeSrcPath = (String) paths.next();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(mergeSrcPath);
                SVNMergeRangeList inheritableRangeList = null;
                if (path == null || path.equals(mergeSrcPath)) {
                    inheritableRangeList = rangeList.getInheritableRangeList(startRev, endRev);
                } else {
                    inheritableRangeList = rangeList.dup();
                }
                if (!inheritableRangeList.isEmpty()) {
                    inheritableMergeInfo.put(mergeSrcPath, inheritableRangeList);
                }
            }
        }
        return inheritableMergeInfo;
    }
    
    public static Map removeMergeInfo(Map eraser, Map whiteBoard) {
        return removeMergeInfo(eraser, whiteBoard, true);
    }

    public static Map removeMergeInfo(Map eraser, Map whiteBoard, boolean considerInheritance) {
        Map mergeInfo = new TreeMap();
        walkMergeInfoHashForDiff(mergeInfo, null, whiteBoard, eraser, considerInheritance);
        return mergeInfo;
    }

    public static Map intersectMergeInfo(Map mergeInfo1, Map mergeInfo2) {
        return intersectMergeInfo(mergeInfo1, mergeInfo2, true);
    }

    public static Map intersectMergeInfo(Map mergeInfo1, Map mergeInfo2, boolean considerInheritance) {
        Map mergeInfo = new TreeMap();
        for (Iterator pathsIter = mergeInfo1.keySet().iterator(); pathsIter.hasNext();) {
            String path = (String) pathsIter.next();
            SVNMergeRangeList rangeList1 = (SVNMergeRangeList) mergeInfo1.get(path);
            SVNMergeRangeList rangeList2 = (SVNMergeRangeList) mergeInfo2.get(path);
            if (rangeList2 != null) {
                rangeList2 = rangeList2.intersect(rangeList1, considerInheritance);
                if (!rangeList2.isEmpty()) {
                    mergeInfo.put(path, rangeList2.dup());
                }
            }
        }
        return mergeInfo;
    }
    
    public static SVNMergeRange[] parseRevisionList(StringBuffer mergeInfo, String path) throws SVNException {
        Collection ranges = new LinkedList();
        while (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n' && 
                Character.isWhitespace(mergeInfo.charAt(0))) {
            mergeInfo = mergeInfo.deleteCharAt(0);
        }
        if (mergeInfo.length() == 0 || mergeInfo.charAt(0) == '\n') {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                    "Mergeinfo for ''{0}'' maps to an empty revision range", path);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        while (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n') {
            long startRev = parseRevision(mergeInfo);
            if (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n' && 
                mergeInfo.charAt(0) != '-' && mergeInfo.charAt(0) != ',' && 
                mergeInfo.charAt(0) != '*') {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                        "Invalid character ''{0}'' found in revision list", 
                        new Character(mergeInfo.charAt(0)));
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            
            SVNMergeRange range = new SVNMergeRange(startRev - 1, startRev, true);
            if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == '-') {
                mergeInfo = mergeInfo.deleteCharAt(0);
                long endRev = parseRevision(mergeInfo);
                if (startRev > endRev) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                            "Unable to parse reversed revision range ''{0}-{1}''",
                            new Object[] { new Long(startRev), new Long(endRev) });
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                } else if (startRev == endRev) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                            "Unable to parse revision range ''{0}-{1}'' with same start and end revisions",
                            new Object[] { new Long(startRev), new Long(endRev) });
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
                range.setEndRevision(endRev);
            }
            
            if (mergeInfo.length() == 0 || mergeInfo.charAt(0) == '\n') {
                ranges.add(range);
                return (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
            } else if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == ',') {
                ranges.add(range);
                mergeInfo = mergeInfo.deleteCharAt(0);
            } else if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == '*') {
                range.setInheritable(false);
                mergeInfo = mergeInfo.deleteCharAt(0);
                if (mergeInfo.length() == 0 || mergeInfo.charAt(0) == ',' || mergeInfo.charAt(0) == '\n') {
                    ranges.add(range);
                    if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == ',') {
                        mergeInfo = mergeInfo.deleteCharAt(0);
                    } else {
                        return (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
                    }
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                                                                 "Invalid character ''{0}'' found in range list", 
                                                                 mergeInfo.length() > 0 ?  mergeInfo.charAt(0) + "" : "");
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                                                             "Invalid character ''{0}'' found in range list", 
                                                             mergeInfo.length() > 0 ?  mergeInfo.charAt(0) + "" : "");
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
        }
        
        if (mergeInfo.length() == 0 || mergeInfo.charAt(0) != '\n' ) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, "Range list parsing ended before hitting newline");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        return (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
    }

    /**
     * @return [deletedList, addedList]
     */
    public static SVNMergeRangeList[] diffMergeRangeLists(SVNMergeRangeList fromRangeList, SVNMergeRangeList toRangeList, 
            boolean considerInheritance) {
        SVNMergeRangeList deletedRangeList = fromRangeList.diff(toRangeList, considerInheritance);
        SVNMergeRangeList addedRangeList = toRangeList.diff(fromRangeList, considerInheritance);
        return new SVNMergeRangeList[] { deletedRangeList, addedRangeList };
    }
    
    private static long parseRevision(StringBuffer mergeInfo) throws SVNException {
        int ind = 0;
        while (ind < mergeInfo.length() && Character.isDigit(mergeInfo.charAt(ind))) {
            ind++;
        }
        
        if (ind == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REVISION_NUMBER_PARSE_ERROR, 
                                                         "Invalid revision number found parsing ''{0}''", 
                                                         mergeInfo.toString());
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        String numberStr = mergeInfo.substring(0, ind);
        long rev = -1;
        try {
            rev = Long.parseLong(numberStr);
        } catch (NumberFormatException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REVISION_NUMBER_PARSE_ERROR, 
                                                         "Invalid revision number found parsing ''{0}''", 
                                                         mergeInfo.toString());
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }

        if (rev < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REVISION_NUMBER_PARSE_ERROR, 
                                                         "Negative revision number found parsing ''{0}''", 
                                                         mergeInfo.toString());
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        mergeInfo = mergeInfo.delete(0, ind);
        return rev;
    }
    
    private static void walkMergeInfoHashForDiff(Map deleted, Map added, Map from, Map to, 
            boolean considerInheritance) {
        for (Iterator paths = from.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList fromRangeList = (SVNMergeRangeList) from.get(path);
            SVNMergeRangeList toRangeList = (SVNMergeRangeList) to.get(path);
            if (toRangeList != null) {
                SVNMergeRangeList[] rangeListDiff = diffMergeRangeLists(fromRangeList, toRangeList, considerInheritance);
                
                SVNMergeRangeList deletedRangeList = rangeListDiff[0];
                SVNMergeRangeList addedRangeList = rangeListDiff[1];
                if (deleted != null && deletedRangeList.getSize() > 0) {
                    deleted.put(path, deletedRangeList);
                }
                if (added != null && addedRangeList.getSize() > 0) {
                    added.put(path, addedRangeList);
                }
            } else if (deleted != null) {
                deleted.put(path, fromRangeList.dup());
            }
        }
        
        if (added == null) {
            return;
        }
        
        for (Iterator paths = to.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList toRangeList = (SVNMergeRangeList) to.get(path);
            if (!from.containsKey(path)) {
                added.put(path, toRangeList.dup());
            }
        }        
    }

	private static class ElideMergeInfoCatalogHandler implements ISVNCommitPathHandler {
        private Map myMergeInfoCatalog;
        private List myElidablePaths;
        
        public ElideMergeInfoCatalogHandler(Map mergeInfoCatalog) {
            myMergeInfoCatalog = mergeInfoCatalog;
            myElidablePaths = new LinkedList();
        }
        
        public boolean handleCommitPath(String path, ISVNEditor editor) throws SVNException {
            ElideMergeInfoEditor elideEditor = (ElideMergeInfoEditor) editor;
	        String inheritedMergeInfoPath = elideEditor.getInheritedMergeInfoPath();
	        if (inheritedMergeInfoPath == null || "/".equals(path)) {
	            return false;
	        }
	        String pathSuffix = SVNPathUtil.getPathAsChild(inheritedMergeInfoPath, path);
	        if (pathSuffix == null) {
	            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "path suffix is null");
	            SVNErrorManager.error(err, SVNLogType.DEFAULT);
	        }
	        boolean elides = shouldElideMergeInfo((Map) myMergeInfoCatalog.get(inheritedMergeInfoPath), 
	                (Map) myMergeInfoCatalog.get(path), pathSuffix);
	        if (elides) {
	            myElidablePaths.add(path);
	        }
	        return false;
	    }
        
        public List getElidablePaths() {
            return myElidablePaths;
        }
	}
	
	private static class ElideMergeInfoEditor implements ISVNEditor {

	    private Map myMergeInfoCatalog;
	    private ElideMergeInfoCatalogDirBaton myCurrentDirBaton;
	    
	    public ElideMergeInfoEditor(Map mergeInfoCatalog) {
	        myMergeInfoCatalog = mergeInfoCatalog;
	    }
	    
        public void abortEdit() throws SVNException {
        }

        public void absentDir(String path) throws SVNException {
        }

        public void absentFile(String path) throws SVNException {
        }

        public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        }

        public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        }

        public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        }

        public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        }

        public void closeDir() throws SVNException {
        }

        public SVNCommitInfo closeEdit() throws SVNException {
            return null;
        }

        public void closeFile(String path, String textChecksum) throws SVNException {
        }

        public void deleteEntry(String path, long revision) throws SVNException {
        }

        public void openDir(String path, long revision) throws SVNException {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            
            ElideMergeInfoCatalogDirBaton dirBaton = new ElideMergeInfoCatalogDirBaton();
            if (myMergeInfoCatalog.get(path) != null) {
                dirBaton.myInheritedMergeInfoPath = path;
            } else {
                dirBaton.myInheritedMergeInfoPath = myCurrentDirBaton.myInheritedMergeInfoPath;
            }
            myCurrentDirBaton = dirBaton;
        }

        public void openFile(String path, long revision) throws SVNException {
        }

        public void openRoot(long revision) throws SVNException {
            myCurrentDirBaton = new ElideMergeInfoCatalogDirBaton();
        }

        public void targetRevision(long revision) throws SVNException {
        }

        public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        }

        public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
            return SVNFileUtil.DUMMY_OUT;
        }

        public void textDeltaEnd(String path) throws SVNException {
        }

        public String getInheritedMergeInfoPath() {
            return myCurrentDirBaton.myInheritedMergeInfoPath;
        }
        
        private class ElideMergeInfoCatalogDirBaton {
            private String myInheritedMergeInfoPath;
        }
	    
	}

}
