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
   
}
