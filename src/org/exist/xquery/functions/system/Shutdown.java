/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  $Id:
 */
package org.exist.xquery.functions.system;


import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.storage.BrokerPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Shutdown the eXist server (must be dba)
 * 
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class Shutdown extends BasicFunction
{
	protected final static Logger logger = Logger.getLogger(Shutdown.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("shutdown", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Shutdown eXist immediately.  This method is only available to the DBA role.",
			null,
			new SequenceType(Type.ITEM, Cardinality.EMPTY)
		),
		
		new FunctionSignature(
			new QName("shutdown", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Shutdown eXist.  This method is only available to the DBA role.",
			new SequenceType[] {
					new FunctionParameterSequenceType("delay", Type.LONG, Cardinality.EXACTLY_ONE, "The delay in milliseconds before eXist starts to shutdown.")
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)
		)
	};
		

	public Shutdown(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		if(context.getSubject().hasDbaRole())
		{
			//determine the shutdown delay
			long delay = 0;
			if(args.length == 1)
			{
				if(!args[0].isEmpty())
				{
					delay = ((NumericValue)args[0].itemAt(0)).getLong();
				}
			}
			
			//get the broker pool and shutdown
			final BrokerPool pool = context.getBroker().getBrokerPool();
				
			if(delay > 0)
			{
				logger.info("Shuttting down in " + delay + " milliseconds.");
				final TimerTask task = new DelayedShutdownTask(pool);
				final Timer timer = new Timer();
				timer.schedule(task, delay);
			}
			else
			{
				logger.info("Shutting down now.");
				pool.shutdown();
			}
		}
		else
		{
			final XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to shutdown the database");
			logger.error("Invalid user", xPathException);
			throw xPathException;
		}
			
		return Sequence.EMPTY_SEQUENCE;
	}
	
	private class DelayedShutdownTask extends TimerTask
	{
		private BrokerPool pool = null;
		
		public DelayedShutdownTask(BrokerPool pool)
		{
			super();
			this.pool = pool;
		}
		
		public void run()
		{
			logger.info("Shutting down now.");
			pool.shutdown();
		}
	}
}
