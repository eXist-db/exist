/*
 * FileUtils.java
 *
 * Created on December 10, 2007, 1:11 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.exist.util;

import java.io.File;
import java.io.FileFilter;

/**
 *
 * @author alex
 */
public class FileUtils
{
   
   // Why is this here?  Because we can't use generics because we're
   // still in the dark ages of Java 1.4
   
   static class FileRef {
      File file;
      FileRef next;
      FileRef(FileRef next,File file) {
         this.next = next;
         this.file = file;
      }
      FileRef(File file) {
         this.next = null;
         this.file = file;
      }
   }
   static class DeleteDir {
      FileRef current;
      boolean ok;
      DeleteDir(File dir) {
         current = new FileRef(dir);
         ok = true;
      }
      public boolean delete() {
         while (ok && current!=null) {
            FileRef work = current;
            current.file.listFiles(new FileFilter() {
               public boolean accept(File file) {
                  if (file.isDirectory()) {
                     current = new FileRef(current,file);
                  } else {
                     ok = file.delete();
                  }
                  return false;
               }
            });
            if (current==work) {
               ok = current.file.delete();
               current = current.next;
            }
         }
         return ok;
      }
   }
   /** Creates a new instance of FileUtils */
   private FileUtils()
   {
   }
   
   public static boolean delete(File dir)
   {
      if (!dir.isDirectory()) {
         return dir.delete();
      }
      DeleteDir doDelete = new DeleteDir(dir);
      return doDelete.delete();
      
   }
   
   /**
    * @param path a path or uri
    * @return the directory portion of a path by stripping the last '/' and
    * anything following, unless the path has no '/', in which case '.' is returned,
    * or ends with '/', in 
    * which case return the path unchanged.
    */
   public static String dirname (String path) {
       int islash = path.lastIndexOf('/');
       if (islash >= 0 && islash < path.length() - 1)
           return path.substring(0, islash);
       else if (islash >= 0)
           return path;
       else
           return ".";
   }

   /**
    * @param path1
    * @param path2
    * @return path1 + path2, joined by a single file separator (or /, if a slash is already present).
    */
   public static String addPaths(String path1, String path2) {
       if (path1.endsWith("/") || path2.endsWith (File.separator)) {
           if (path2.startsWith("/") || path2.startsWith(File.separator)) {
               return path1 + path2.substring (1);
           } else {
               return path1 + path2;
           }
       } else {
           if (path2.startsWith("/") || path2.startsWith(File.separator)) {
               return path1 + path2;
           } else {
               return path1 + File.separatorChar + path2;
           }
       }
   }
   
}
