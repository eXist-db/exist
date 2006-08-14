/*
 * Date.java
 *
 * Created on July 6, 2006, 9:30 PM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author R. Alexander Milowski
 */
public class DateFormatter {
   
   /** Creates a new instance of Date */
   private DateFormatter() {
   }
   
   static DateFormat xsdFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
   
   public static String toXSDDateTime(long value)
   {
      Date d = new Date(value);
      return toXSDDateTime(d);
   }
   
   public static String toXSDDateTime(Date d)
   {
      String result = xsdFormat.format(d);
      result = result.substring(0, result.length()-2)
               + ":" + result.substring(result.length()-2);            
      return result;
   }
   
}
