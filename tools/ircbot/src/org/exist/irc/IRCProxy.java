package org.exist.irc;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;

public class IRCProxy extends HttpServlet {

	private String server = "irc.freenode.net";
	private String channel = "#testaabb";
	
	private Map channels = new HashMap();
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String sessionId = request.getParameter("session");
		if (sessionId == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "No session specified");
			return;
		}
		IRCSession session = (IRCSession) channels.get(sessionId);
		if (session == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "Session " + sessionId + " not found");
			return;
		}
		
		session.run(response);
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		String sessionId = request.getParameter("session");
		String nick = request.getParameter("nick");
		String channelParam = request.getParameter("channel");
		String close = request.getParameter("close");
		String send = request.getParameter("send");
		if (sessionId == null) {
			if (channelParam != null && channelParam.length() > 0)
				channel = channelParam;
			try {
				IRCSession session = new IRCSession(server, channel, nick);
				sessionId = session.getSessionId();
				synchronized(channels) {
					channels.put(sessionId, session);
				}
				response.setContentType("text/text");
				response.setContentLength(0);
				response.setHeader("X-IRC-Session", sessionId);
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
			if (close != null) {
				log("Closing session " + sessionId);
				session.quit();
			} else if (send != null) {
				log("Sending message: " + send + "; id: " + sessionId);
				session.send(send);
			}
		}
	}
	
	public void destroy() {
		for (Iterator i = channels.values().iterator(); i.hasNext(); ) {
			IRCSession session = (IRCSession) i.next();
			session.quit();
		}
	}
}
