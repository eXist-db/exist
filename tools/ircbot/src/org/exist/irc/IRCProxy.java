package org.exist.irc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;

public class IRCProxy extends HttpServlet {

	// private String server = "irc.freenode.net";

	private String server = "localhost";
	private String channel = "#testaabb";

	private boolean modProxyHack = false;
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		String param = config.getInitParameter("mod-proxy");
		if (param != null)
			modProxyHack = param.equalsIgnoreCase("true");
        param = config.getInitParameter("server");
        if (param != null)
            server = param;
        Map channels = new HashMap();
        getServletContext().setAttribute("org.exist.irc.sessions", channels);
    }
	
	/**
	 * The GET method opens a connection to the client and keeps it open, i.e. the
	 * method will only return if the client does explicitely close the session (via a POST)
	 * or the session is killed. The server sends all input it receives from the IRC server down
	 * this stream.
	 * 
	 * Before calling GET, the client has to create a session by sending a POST request
	 * with just the channel and the nick as parameters (but no 'session' parameter). After
	 * the session was created successfully, the client can call GET and start listening.
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String sessionId = request.getParameter("session");
		if (sessionId == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "No session specified");
			return;
		}

        Map channels = (Map) getServletContext().getAttribute("org.exist.irc.sessions");
        IRCSession session = (IRCSession) channels.get(sessionId);
		if (session == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "Session " + sessionId + " not found");
			return;
		}
        log("Starting/refreshing session ...");
        if (session.started()) {
            session.closeTunnel();
        }
        session.run(response);
    }
	
	/**
	 * The POST method is used to initialize a session and to process
	 * commands. The client will use POST to pass the user input to the
	 * server asynchronously.
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		String sessionId = request.getParameter("session");
		String nick = request.getParameter("nick");
		String channelParam = request.getParameter("channel");
		String close = request.getParameter("close");
		String send = request.getParameter("send");
		String pong = request.getParameter("pong");
		String reconnect = request.getParameter("refresh");
        Map channels = (Map) getServletContext().getAttribute("org.exist.irc.sessions");
        if (sessionId == null) {
			// No session yet: connect and create a new one
			if (channelParam != null && channelParam.length() > 0)
				channel = channelParam;
			try {
				IRCSession session = new IRCSession(server, channel, nick, modProxyHack);
				sessionId = session.getSessionId();
				log("New session created: " + sessionId);
				// add the session to the list of channels
				synchronized(channels) {
					channels.put(sessionId, session);
				}
				response.setContentType("text/text");
				response.setContentLength(0);
				response.setHeader("X-IRC-Session", sessionId);
				response.setHeader("X-IRC-Nick", session.getName());
			} catch (NickAlreadyInUseException e) {
				response.sendError(HttpServletResponse.SC_CONFLICT, "Nick is already in use");
			} catch (IOException e) {
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
			} catch (IrcException e) {
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
			}
		} else {
			IRCSession session = (IRCSession) channels.get(sessionId);
			if (session == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND, "Session " + sessionId + " not found");
				return;
			}
			// we have a valid session, so check for commands and process them
			if (pong != null) {
				log("Received pong from client.");
				session.pingResponse();
			} else if (close != null) {
				log("Closing session " + sessionId);
				session.quit();
			} else if (reconnect != null) {
				try {
					session.attemptReconnect();
				} catch (NickAlreadyInUseException e) {
					response.sendError(HttpServletResponse.SC_CONFLICT, "Nick is already in use");
				} catch (IOException e) {
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
				} catch (IrcException e) {
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
				}
			} else if (send != null) {
				log("Sending message: " + send + "; id: " + sessionId);
				session.send(send);
			}
		}
	}
	
	public void destroy() {
        Map channels = (Map) getServletContext().getAttribute("org.exist.irc.sessions");
        for (Iterator i = channels.values().iterator(); i.hasNext(); ) {
			IRCSession session = (IRCSession) i.next();
			session.quit();
		}
	}
}
