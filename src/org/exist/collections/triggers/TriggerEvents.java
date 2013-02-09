/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2012 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.collections.triggers;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class TriggerEvents {
	
	//3 bits?
	public static short CREATE 	= 1;
	public static short UPDATE 	= 2;
	public static short COPY 	= 3;
	public static short MOVE 	= 4;
	public static short DELETE 	= 5;
	
	//1 bit
	public static short BEFORE 	= -1;
	public static short AFTER 	= 1;

	//2 bits (because of metadata?)
	public static short COLLECTION 	= 0;
	public static short DOCUMENT 	= 10;

	// -1 (BEFORE) * 1 (CREATE) + 1 (COLLECTION) //TODO: bit it
	public static short BEFORE_CREATE_COLLECTION 	= -1; 
	public static short AFTER_CREATE_COLLECTION 	=  1;
	public static short BEFORE_UPDATE_COLLECTION 	= -2;
	public static short AFTER_UPDATE_COLLECTION 	=  2;
	public static short BEFORE_COPY_COLLECTION 		= -3;
	public static short AFTER_COPY_COLLECTION 		=  3;
	public static short BEFORE_MOVE_COLLECTION 		= -4;
	public static short AFTER_MOVE_COLLECTION 		=  4;
	public static short BEFORE_DELETE_COLLECTION 	= -5;
	public static short AFTER_DELETE_COLLECTION 	=  5;

	public static short BEFORE_CREATE_DOCUMENT 	= -11; 
	public static short AFTER_CREATE_DOCUMENT 	=  11;
	public static short BEFORE_UPDATE_DOCUMENT 	= -12;
	public static short AFTER_UPDATE_DOCUMENT 	=  12;
	public static short BEFORE_COPY_DOCUMENT 	= -13;
	public static short AFTER_COPY_DOCUMENT 	=  13;
	public static short BEFORE_MOVE_DOCUMENT	= -14;
	public static short AFTER_MOVE_DOCUMENT 	=  14;
	public static short BEFORE_DELETE_DOCUMENT 	= -15;
	public static short AFTER_DELETE_DOCUMENT 	=  15;
	
	public enum EVENTS {
		CREATE_COLLECTION,
		UPDATE_COLLECTION,
		COPY_COLLECTION,
		MOVE_COLLECTION,
		DELETE_COLLECTION,

		CREATE_DOCUMENT,
		UPDATE_DOCUMENT,
		COPY_DOCUMENT,
		MOVE_DOCUMENT,
		DELETE_DOCUMENT
	}

	private final static String EVENTS_STRING []  = {
		"CREATE-COLLECTION",
		"UPDATE-COLLECTION",
		"COPY-COLLECTION",
		"MOVE-COLLECTION",
		"DELETE-COLLECTION",

		"CREATE-DOCUMENT",
		"UPDATE-DOCUMENT",
		"COPY-DOCUMENT",
		"MOVE-DOCUMENT",
		"DELETE-DOCUMENT"
	};

	private final static EVENTS _EVENTS_ []  = {
		EVENTS.CREATE_COLLECTION,
		EVENTS.UPDATE_COLLECTION,
		EVENTS.COPY_COLLECTION,
		EVENTS.MOVE_COLLECTION,
		EVENTS.DELETE_COLLECTION,

		EVENTS.CREATE_DOCUMENT,
		EVENTS.UPDATE_DOCUMENT,
		EVENTS.COPY_DOCUMENT,
		EVENTS.MOVE_DOCUMENT,
		EVENTS.DELETE_DOCUMENT
	};

	public static Set<EVENTS> convertFromString(String events) throws TriggerException {
		
		final Set<EVENTS> result = new HashSet<EVENTS>();
		
	    final StringTokenizer tok = new StringTokenizer(events, ", ");
	    String event;
	    while(tok.hasMoreTokens()) {
	        event = tok.nextToken();
	        
	        int i=0;
	        while (i < EVENTS_STRING.length){
	        	if (event.equalsIgnoreCase(EVENTS_STRING[i])){
	        		result.add(_EVENTS_[i]);
	                break;
	        	}
	        	i++;
	        }
	
	        if ( i > EVENTS_STRING.length){
	        	throw new TriggerException(
		    			"Unknown event type '" + event);
			}
	    }
	    
	    return result;
	}

	public final static String OLD_EVENTS_STRING []  = {
		"STORE",
		"UPDATE",
		"REMOVE",
	};

	public static Set<EVENTS> convertFromOldDesign(String events) throws TriggerException {
		
		final Set<EVENTS> result = new HashSet<EVENTS>();
		
	    final StringTokenizer tok = new StringTokenizer(events, ", ");
	    String event;
	    while(tok.hasMoreTokens()) {
	        event = tok.nextToken();
	        
	        int i=0;
	        while (i<OLD_EVENTS_STRING.length){
	        	if (event.equalsIgnoreCase(OLD_EVENTS_STRING[i])){
	                break;
	        	}
	        	i++;
	        }
	
            switch (i) {
			case 0:
				result.add(EVENTS.CREATE_DOCUMENT);
				break;
			
			case 1:
				result.add(EVENTS.UPDATE_DOCUMENT);
				break;

			case 2:
				result.add(EVENTS.DELETE_DOCUMENT);
				break;

			default:
//	        	throw new CollectionConfigurationException(
//		    			"Unknown event type '" + event);
			}
	    }
	    
	    return result;
	}
}
