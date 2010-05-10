package org.exist.versioning.svn.core.io.diff;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;

import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowApplyBaton;

public class FakeSVNDiffWindowApplyBaton {

    public static SVNDiffWindowApplyBaton create(File source, File target, MessageDigest digest) throws SVNException {
    	SVNDiffWindowApplyBaton baton = SVNDiffWindowApplyBaton.create(source, target, digest);

    	Field field;
		try {
			field = SVNDiffWindowApplyBaton.class.getDeclaredField("mySourceStream");
			field.setAccessible(true);
			field.set(baton, source.exists() ? SVNFileUtil.openFileForReading(source) : SVNFileUtil.DUMMY_IN);
	    	
	    	field = SVNDiffWindowApplyBaton.class.getDeclaredField("myTargetStream");
			field.setAccessible(true);
			field.set(baton, SVNFileUtil.openFileForWriting(target, true));
	
	        return baton;
		} catch (SecurityException e) {
		} catch (NoSuchFieldException e) {
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		}
		return null;
    }

    public static SVNDiffWindowApplyBaton create(InputStream source, OutputStream target, MessageDigest digest) {
        return SVNDiffWindowApplyBaton.create(source, target, digest);
    }
}
