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

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.AbstractAccount;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.internal.SubjectAccreditedImpl;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("job")
public class Job implements Configurable {
	
    protected static final String DETAILS = "DETAILs";

    @ConfigurationFieldAsAttribute("id")
    private String id;

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

    private Configuration configuration;
    private SchedulerManager manager;

    public Job(final SchedulerManager manager, final Configuration config) throws ConfigurationException {

        this.manager = manager;	
        this.configuration = Configurator.configure(this, config);

        final JobDescription jobDescription;
        if(scriptURI != null && !scriptURI.isEmpty()) {
            jobDescription = new UserXQueryJob(name, scriptURI, getSubject());
        } else {
            //TODO implement support for Java Jobs
            try {
                final Class<?> jobClass = Class.forName(clazz);
                final Object jobObject = jobClass.newInstance();
                if(jobObject instanceof UserJavaJob) {
                   jobDescription = (JobDescription)jobClass.newInstance();
                    if(jobDescription.getName() == null) {
                        jobDescription.setName(name);
                    }
                } else {
                     throw new ConfigurationException("Java Jobs must extend org.exist.scheduler.UserJavaJob");
                }
            } catch(final ClassNotFoundException cnfe) {
                throw new ConfigurationException("No such class: " + clazz, cnfe);
            } catch(final InstantiationException ie) {
                throw new ConfigurationException("Cannot instantiate class: " + clazz, ie);
            } catch(final IllegalAccessException ie) {
                throw new ConfigurationException("Cannot instantiate class: " + clazz, ie);
            }
        }
        
        manager.getScheduler().createCronJob(cronExpression, jobDescription, null, true);
    }

    private Subject getSubject() {
        final Subject subject;
        final SecurityManager sm = manager.getDatabase().getSecurityManager();
        if(account == null || account.isEmpty()) {
            subject = manager.getDatabase().getSecurityManager().getGuestSubject();
        } else {
            final AbstractAccount acc = (AbstractAccount) sm.getAccount(account);
            if(acc == null) {
                //UNDERSTAND: error better here?
                subject = sm.getGuestSubject();
            } else {
                subject = new SubjectAccreditedImpl(acc, this);
            }
        }
        return subject;
        
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
