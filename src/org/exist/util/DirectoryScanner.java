package org.exist.util;

import java.io.File;
import java.util.ArrayList;

public class DirectoryScanner {

	
	private final static String extractBaseDir(String pattern) {
		int p = 0;
		char ch;
		for(int i = 0; i < pattern.length(); i++) {
			ch = pattern.charAt(i);
			if(ch == File.separatorChar || ch == ':') {
				p = i;
				continue;
			} else if(ch == '*' || ch == '?') {
				if(p > 0)
					return pattern.substring(0, p + 1);
			}
		}
		return null;
	}
	
	public final static File[] scanDir(String pattern) {
        //TODO : why this test ? File should make it ! -pb
		pattern = pattern.replace('/', File.separatorChar).replace('\\',File.separatorChar);
		String baseDir = extractBaseDir(pattern);
		if(baseDir == null) {
                        // Dizzzz ##### Why this dependancy?
			baseDir = System.getProperty("user.dir");
			pattern = baseDir + File.separator + pattern;
		}
		
		File base = new File(baseDir);
		return scanDir(base, pattern.substring(baseDir.length()));
	}
	
	public final static File[] scanDir(File baseDir, String pattern) {
        ///TODO : why this test ? File should make it ! -pb
		pattern = pattern.replace('/', File.separatorChar).replace('\\',File.separatorChar);
		ArrayList list = new ArrayList();
		scanDir(list, baseDir, "", pattern);
		File[] files = new File[list.size()];
		return (File[])list.toArray(files);
	}
	
	private final static void scanDir(ArrayList list, File dir, String vpath, String pattern) {
		String files[] = dir.list();
		if (files == null) {
			return;
		}
		File file;
		String name;
		for(int i = 0; i < files.length; i++) {
			file = new File(dir, files[i]);
			name = vpath + files[i];
			if(file.isDirectory() && matchStart(pattern, name)) {
				scanDir(list, file, name + File.separator, pattern);
			} else if(match(pattern, name))
				list.add(file);
		}
	}
				
	public final static boolean match(String pattern, String name) {
		return SelectorUtils.matchPath(pattern, name);
	}

	public final static boolean matchStart(String pattern, String name) {
		return SelectorUtils.matchPatternStart(pattern, name);
	}
	
	public static void main(String args[]) {
		File files[] = scanDir("/home/*/xml/**/*.xml");
		for(int i = 0; i < files.length; i++)
			System.out.println(files[i].getAbsolutePath());
		
		files = scanDir("/does-not-exist/*.xml");
		for(int i = 0; i < files.length; i++)
			System.out.println(files[i].getAbsolutePath());
	}
}
				
