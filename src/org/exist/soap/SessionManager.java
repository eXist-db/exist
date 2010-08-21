package org.exist.soap;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.exist.security.Subject;

public class SessionManager {

	private static long TIMEOUT = 3600000;
	private static volatile SessionManager instance = null;

	public static final SessionManager getInstance() {
		if (instance == null)
			instance = new SessionManager();
		return instance;
	}

	public static long getTimeout() {
		return TIMEOUT;
	}

	public static void setTimeout(long timeout) {
		TIMEOUT = timeout;
	}

	Map<String, Session> sessions = new TreeMap<String, Session>();
	
	public synchronized String createSession(Subject user) {
		Session session = new Session(user);
		String id = String.valueOf(session.hashCode());
		sessions.put(id, session);
		return id;
	}
	
	public synchronized Session getSession(String id) {
		if(id == null)
			return null;
		Session session = (Session)sessions.get(id);
		if(session != null)
			session.updateLastAccessTime();
		return session;
	}

	public synchronized void disconnect(String id) {
		sessions.remove(id);
	}
	
	@SuppressWarnings("unused")
	private void checkResultSets() {
		for (Iterator i = sessions.values().iterator(); i.hasNext();) {
			Session session = (Session)i.next();
			if(System.currentTimeMillis() - session.getLastAccessTime() > TIMEOUT)
				i.remove();
		}
	}
}
