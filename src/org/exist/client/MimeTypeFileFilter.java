package org.exist.client;

import java.io.File;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.filechooser.FileFilter;

import org.exist.util.MimeTable;


/**
 * A FileFilter that filters for files based on their extension
 * Uses the filename extensions defined in mime-types.xml
 * 
 *  Java 6 API has a similar FileNameExtensionFilter
 */
public class MimeTypeFileFilter extends FileFilter
{
	private String description = null;	
	private Vector extensions = null;
	
	public MimeTypeFileFilter(String mimeType)
	{
		description = MimeTable.getInstance().getContentType(mimeType).getDescription();
		extensions = MimeTable.getInstance().getAllExtensions(mimeType);
	}
	
	public boolean accept(File file)
	{
		if(file.isDirectory()) //permit directories to be viewed
			return true;
		
		int extensionOffset = file.getName().lastIndexOf('.');	//do-not allow files without an extension
		if(extensionOffset == -1)
			return false;
		
		//check the extension is that of a file as defined in mime-types.xml
		String fileExtension = file.getName().substring(extensionOffset).toLowerCase();
		
		for(Iterator itExtensions = extensions.iterator(); itExtensions.hasNext();)
		{
			String extension = (String)itExtensions.next();
			
			if(fileExtension.equals(extension))
			{
				return true;
			}
		}
		
		return false;
	}
	
	public String getDescription()
	{
		String description = this.description + " ("; 
		
		for(Iterator itExtensions = extensions.iterator(); itExtensions.hasNext();)
		{
			description += (String)itExtensions.next();
			if(itExtensions.hasNext())
				description += ' ';
		}
		
		return description + ")";
	}
}