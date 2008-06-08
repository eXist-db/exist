package org.exist.backup;

import java.io.File;
import java.io.FileNotFoundException;

import org.exist.util.EXistInputSource;
import org.exist.util.FileInputSource;

public class FileSystemBackupDescriptor
	implements BackupDescriptor
{
	protected File descriptor;
	
	public FileSystemBackupDescriptor(File theDesc)
		throws FileNotFoundException
	{
		if(!theDesc.getName().equals(BackupDescriptor.COLLECTION_DESCRIPTOR) || !theDesc.isFile() || !theDesc.canRead()) {
			throw new FileNotFoundException(theDesc.getAbsolutePath()+" is not a valid collection descriptor");
		}
		descriptor=theDesc;
	}
	
	public BackupDescriptor getChildBackupDescriptor(String describedItem) {
		File child=new File(new File(descriptor.getParentFile(),describedItem),BackupDescriptor.COLLECTION_DESCRIPTOR);
		BackupDescriptor bd=null;
		try {
			bd=new FileSystemBackupDescriptor(child);
		} catch(FileNotFoundException fnfe) {
			// DoNothing(R)
		}
		return bd;
	}

	public EXistInputSource getInputSource() {
		return new FileInputSource(descriptor);
	}

	public EXistInputSource getInputSource(String describedItem) {
		File child=new File(descriptor.getParentFile(),describedItem);
		EXistInputSource is=null;
		if(child.isFile() && child.canRead()) {
			is=new FileInputSource(child);
		}
		
		return is;
	}
	
	public String getSymbolicPath() {
		return descriptor.getAbsolutePath();
	}
	
	public String getSymbolicPath(String describedItem,boolean isChildDescriptor) {
		File resbase=new File(descriptor.getParentFile(),describedItem);
		if(isChildDescriptor)
			resbase=new File(resbase,BackupDescriptor.COLLECTION_DESCRIPTOR);
		return resbase.getAbsolutePath();
	}
}
