package org.exist.xquery.modules.cache;

import java.util.HashMap;

import org.exist.xquery.value.Sequence;

/**
 * Static Global cache model
 * 
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 * @version 1.0
 */
public class Cache extends HashMap<String, Sequence>{
	
	private static HashMap<String, Cache> globalCache = new HashMap<String, Cache>();
	
    public Cache(String name){
    	super();
    	globalCache.put(name, this);
    }
    
	public static Cache getInstance(String name){
		Cache cache = globalCache.get(name);
		if (cache == null){
			cache = new Cache(name);
		}
		return cache;
	}
	
	public Sequence put(String key, Sequence value){
		Sequence v = super.put(key, value); 
		return (v==null) ? Sequence.EMPTY_SEQUENCE : v;
	}
	
	public static Sequence put(String name, String key, Sequence value){
		return getInstance(name).put(key, value);
	}
	
	public Sequence get(String key){
		Sequence v = super.get(key); 
		return (v==null) ? Sequence.EMPTY_SEQUENCE : v;
	}
	
	public static Sequence get(String name, String key){
		return getInstance(name).get(key);
	}
	
	public Sequence remove(String key){
		Sequence v = super.remove(key); 
		return (v==null) ? Sequence.EMPTY_SEQUENCE : v;
	}
	
	public static Sequence remove(String name, String key){
		return getInstance(name).remove(key);
	}
	
	public static void clear(String name){
		getInstance(name).clear();
	}
	
	public static void clearGlobal(){
		globalCache.clear();
	}
	
}
