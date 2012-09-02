/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.scheduler;

import java.text.ParseException;

import org.exist.Database;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.AbstractAccount;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

//import static org.quartz.JobBuilder.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("job")
public class Job implements Configurable {

	@ConfigurationFieldAsElement("name")
	private String name;

	@ConfigurationFieldAsElement("group")
	private String group;

	@ConfigurationFieldAsElement("class")
	private String clazz;
	
	@ConfigurationFieldAsElement("run-as-account")
	private String account;

	@ConfigurationFieldAsElement("script-uri")
	protected String scriptURI;

	@ConfigurationFieldAsElement("cron-expression")
	private String cronExpression;
	
	private Configuration configuration = null;
	private SchedulerManager manager;

	public Job(SchedulerManager manager, Configuration config) throws ConfigurationException {

		this.manager = manager;
		
        configuration = Configurator.configure(this, config);

		try {
			manager.scheduler.scheduleJob(getJobDetail(), getTrigger());
		} catch (SchedulerException e) {
			throw new ConfigurationException(e);
		}
	}
	
	private JobDetail getJobDetail() throws ConfigurationException {
		return new JobDetail(name, group, getJobClass());
	}
	
	private Trigger getTrigger() throws ConfigurationException {
        try {
			return new CronTrigger(name + " Trigger", group, cronExpression);
		} catch (ParseException e) {
			throw new ConfigurationException(e);
		}
	}
	
	private Class<?> getJobClass() throws ConfigurationException {
		try {
			if (scriptURI != null)
				return Class.forName("org.exist.scheduler.XQueryJob");

			return Class.forName(clazz);
		} catch (ClassNotFoundException e) {
			throw new ConfigurationException(e);
		}
	}

	protected Subject getSubject() {
    	SecurityManager sm = getDatabase().getSecurityManager();
    	AbstractAccount acc = (AbstractAccount) sm.getAccount(account);
    	if (acc == null)
    		//UNDERSTAND: error better here?
    		return sm.getGuestSubject();
    	
        return new SubjectAccreditedImpl(acc, this);
    }
	
	protected Database getDatabase() {
		return manager.db;
	}

	@Override
	public boolean isConfigured() {
		return configuration != null;
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}
}
