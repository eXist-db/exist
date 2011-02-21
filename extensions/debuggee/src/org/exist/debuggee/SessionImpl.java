/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.debuggee;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.ReadFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.TransportMetadata;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.core.write.WriteRequestQueue;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SessionImpl implements IoSession, Session {

	private long creationTime;
	private boolean closed = false;
	
	Map<Object, Object> attributes = new HashMap<Object, Object>();
	
	public SessionImpl() {
		creationTime = System.currentTimeMillis();
	}
	
	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#close()
	 */
	@Override
	public CloseFuture close() {
		closed = true;
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#close(boolean)
	 */
	@Override
	public CloseFuture close(boolean arg0) {
		closed = true;
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#containsAttribute(java.lang.Object)
	 */
	@Override
	public boolean containsAttribute(Object arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getAttachment()
	 */
	@Override
	public Object getAttachment() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getAttribute(java.lang.Object)
	 */
	@Override
	public Object getAttribute(Object arg0) {
		return attributes.get(arg0);
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getAttribute(java.lang.Object, java.lang.Object)
	 */
	@Override
	public Object getAttribute(Object arg0, Object arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getAttributeKeys()
	 */
	@Override
	public Set<Object> getAttributeKeys() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getBothIdleCount()
	 */
	@Override
	public int getBothIdleCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getCloseFuture()
	 */
	@Override
	public CloseFuture getCloseFuture() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getConfig()
	 */
	@Override
	public IoSessionConfig getConfig() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getCreationTime()
	 */
	@Override
	public long getCreationTime() {
		return creationTime;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getCurrentWriteMessage()
	 */
	@Override
	public Object getCurrentWriteMessage() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getCurrentWriteRequest()
	 */
	@Override
	public WriteRequest getCurrentWriteRequest() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getFilterChain()
	 */
	@Override
	public IoFilterChain getFilterChain() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getHandler()
	 */
	@Override
	public IoHandler getHandler() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getId()
	 */
	@Override
	public long getId() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getIdleCount(org.apache.mina.core.session.IdleStatus)
	 */
	@Override
	public int getIdleCount(IdleStatus arg0) {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getLastBothIdleTime()
	 */
	@Override
	public long getLastBothIdleTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getLastIdleTime(org.apache.mina.core.session.IdleStatus)
	 */
	@Override
	public long getLastIdleTime(IdleStatus arg0) {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getLastIoTime()
	 */
	@Override
	public long getLastIoTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getLastReadTime()
	 */
	@Override
	public long getLastReadTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getLastReaderIdleTime()
	 */
	@Override
	public long getLastReaderIdleTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getLastWriteTime()
	 */
	@Override
	public long getLastWriteTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getLastWriterIdleTime()
	 */
	@Override
	public long getLastWriterIdleTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getLocalAddress()
	 */
	@Override
	public SocketAddress getLocalAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getReadBytes()
	 */
	@Override
	public long getReadBytes() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getReadBytesThroughput()
	 */
	@Override
	public double getReadBytesThroughput() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getReadMessages()
	 */
	@Override
	public long getReadMessages() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getReadMessagesThroughput()
	 */
	@Override
	public double getReadMessagesThroughput() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getReaderIdleCount()
	 */
	@Override
	public int getReaderIdleCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getRemoteAddress()
	 */
	@Override
	public SocketAddress getRemoteAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getScheduledWriteBytes()
	 */
	@Override
	public long getScheduledWriteBytes() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getScheduledWriteMessages()
	 */
	@Override
	public int getScheduledWriteMessages() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getService()
	 */
	@Override
	public IoService getService() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getServiceAddress()
	 */
	@Override
	public SocketAddress getServiceAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getTransportMetadata()
	 */
	@Override
	public TransportMetadata getTransportMetadata() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getWriteRequestQueue()
	 */
	@Override
	public WriteRequestQueue getWriteRequestQueue() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getWriterIdleCount()
	 */
	@Override
	public int getWriterIdleCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getWrittenBytes()
	 */
	@Override
	public long getWrittenBytes() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getWrittenBytesThroughput()
	 */
	@Override
	public double getWrittenBytesThroughput() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getWrittenMessages()
	 */
	@Override
	public long getWrittenMessages() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#getWrittenMessagesThroughput()
	 */
	@Override
	public double getWrittenMessagesThroughput() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#isBothIdle()
	 */
	@Override
	public boolean isBothIdle() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#isClosing()
	 */
	@Override
	public boolean isClosing() {
		return closed;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#isConnected()
	 */
	@Override
	public boolean isConnected() {
		return !closed;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#isIdle(org.apache.mina.core.session.IdleStatus)
	 */
	@Override
	public boolean isIdle(IdleStatus arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#isReadSuspended()
	 */
	@Override
	public boolean isReadSuspended() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#isReaderIdle()
	 */
	@Override
	public boolean isReaderIdle() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#isWriteSuspended()
	 */
	@Override
	public boolean isWriteSuspended() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#isWriterIdle()
	 */
	@Override
	public boolean isWriterIdle() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#read()
	 */
	@Override
	public ReadFuture read() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#removeAttribute(java.lang.Object)
	 */
	@Override
	public Object removeAttribute(Object arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#removeAttribute(java.lang.Object, java.lang.Object)
	 */
	@Override
	public boolean removeAttribute(Object arg0, Object arg1) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#replaceAttribute(java.lang.Object, java.lang.Object, java.lang.Object)
	 */
	@Override
	public boolean replaceAttribute(Object arg0, Object arg1, Object arg2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#resumeRead()
	 */
	@Override
	public void resumeRead() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#resumeWrite()
	 */
	@Override
	public void resumeWrite() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#setAttachment(java.lang.Object)
	 */
	@Override
	public Object setAttachment(Object arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#setAttribute(java.lang.Object)
	 */
	@Override
	public Object setAttribute(Object arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#setAttribute(java.lang.Object, java.lang.Object)
	 */
	@Override
	public Object setAttribute(Object arg0, Object arg1) {
		return attributes.put(arg0, arg1);
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#setAttributeIfAbsent(java.lang.Object)
	 */
	@Override
	public Object setAttributeIfAbsent(Object arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#setAttributeIfAbsent(java.lang.Object, java.lang.Object)
	 */
	@Override
	public Object setAttributeIfAbsent(Object arg0, Object arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#setCurrentWriteRequest(org.apache.mina.core.write.WriteRequest)
	 */
	@Override
	public void setCurrentWriteRequest(WriteRequest arg0) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#suspendRead()
	 */
	@Override
	public void suspendRead() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#suspendWrite()
	 */
	@Override
	public void suspendWrite() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#updateThroughput(long, boolean)
	 */
	@Override
	public void updateThroughput(long arg0, boolean arg1) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#write(java.lang.Object)
	 */
	@Override
	public WriteFuture write(Object arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.core.session.IoSession#write(java.lang.Object, java.net.SocketAddress)
	 */
	@Override
	public WriteFuture write(Object arg0, SocketAddress arg1) {
		// TODO Auto-generated method stub
		return null;
	}

}
