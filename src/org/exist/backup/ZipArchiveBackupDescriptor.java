package org.exist.backup;

import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.exist.util.EXistInputSource;
import org.exist.util.ZipEntryInputSource;

public class ZipArchiveBackupDescriptor
	implements BackupDescriptor
{
	protected ZipFile archive;
	protected ZipEntry descriptor;
	protected String base;
	
	public ZipArchiveBackupDescriptor(File fileArchive)
		throws ZipException,IOException,FileNotFoundException
	{
		archive=new ZipFile(fileArchive);
		base="db/";
		descriptor=archive.getEntry(base+BackupDescriptor.COLLECTION_DESCRIPTOR);
		if(descriptor==null || descriptor.isDirectory()) {
			throw new FileNotFoundException("Archive "+fileArchive.getAbsolutePath()+" is not a valid eXist backup archive");
		}
	}
	
	private ZipArchiveBackupDescriptor(ZipFile archive,String base)
		throws FileNotFoundException
	{
		this.archive=archive;
		this.base=base;
		descriptor=archive.getEntry(base+BackupDescriptor.COLLECTION_DESCRIPTOR);
		if(descriptor==null || descriptor.isDirectory()) {
			throw new FileNotFoundException(archive.getName()+" is a bit corrupted ("+base+" descriptor not found): not a valid eXist backup archive");
		}
	}
	
	public BackupDescriptor getChildBackupDescriptor(String describedItem) {
		BackupDescriptor bd=null;
		try {
			bd=new ZipArchiveBackupDescriptor(archive,base+describedItem+"/");
		} catch(FileNotFoundException fnfe) {
			// DoNothing(R)
		}
		
		return bd;
	}

	public EXistInputSource getInputSource() {
		return new ZipEntryInputSource(archive,descriptor);
	}

	public EXistInputSource getInputSource(String describedItem) {
		ZipEntry ze = archive.getEntry(base+describedItem);
		EXistInputSource retval=null;
		if(ze!=null && !ze.isDirectory()) {
			retval=new ZipEntryInputSource(archive,ze);
		}
		
		return retval;
	}

	public String getSymbolicPath() {
		return archive.getName()+"#"+descriptor.getName();
	}
	
	public String getSymbolicPath(String describedItem,boolean isChildDescriptor) {
		String retval=archive.getName()+"#"+base+describedItem;
		if(isChildDescriptor)
			retval+="/"+BackupDescriptor.COLLECTION_DESCRIPTOR;
		return retval;
	}
}
