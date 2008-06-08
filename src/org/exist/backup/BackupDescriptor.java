package org.exist.backup;

import org.exist.util.EXistInputSource;

public interface BackupDescriptor {
	public final static String COLLECTION_DESCRIPTOR="__contents__.xml";
	
	public EXistInputSource getInputSource();
	
	public EXistInputSource getInputSource(String describedItem);
	
	public BackupDescriptor getChildBackupDescriptor(String describedItem);
	
	public String getSymbolicPath();
	
	public String getSymbolicPath(String describedItem,boolean isChildDescriptor);
}
