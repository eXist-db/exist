package org.exist.backup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.Date;

import org.exist.util.EXistInputSource;
import org.exist.util.FileInputSource;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.DateTimeValue;

public class FileSystemBackupDescriptor extends AbstractBackupDescriptor
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

    public BackupDescriptor getBackupDescriptor(String describedItem) {
        String topDir = descriptor.getParentFile().getParentFile().getAbsolutePath();
        String subDir = topDir + describedItem;
        String desc = subDir + '/' + BackupDescriptor.COLLECTION_DESCRIPTOR;
        BackupDescriptor bd=null;
        try {
            bd=new FileSystemBackupDescriptor(new File(desc));
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

    public Properties getProperties() throws IOException {
        File dir = descriptor.getParentFile();
        if (dir != null) {
            File parentDir = dir.getParentFile();
            if (parentDir != null) {
                File propFile = new File(parentDir, BACKUP_PROPERTIES);
                try {
                    InputStream is = new BufferedInputStream(new FileInputStream(propFile));
                    Properties properties = new Properties();
                    try {
                        properties.load(is);
                    } finally {
                        is.close();
                    }
                    return properties;
                } catch (FileNotFoundException e) {
                    // do nothing, return null
                }
            }
        }
        return null;
    }

    public File getParentDir() {
        return descriptor.getParentFile().getParentFile().getParentFile();
    }

    public String getName() {
        return descriptor.getParentFile().getParentFile().getName();
    }
}
