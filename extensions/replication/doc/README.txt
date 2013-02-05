$Id$

This document provides a short introduction on the document replication function
of eXist-db.

   "You want to configure two or more eXist instances to work together to 
    automatically synchronize collection-specific data sets. This allows you 
    to scale your eXist server capacity. For example, with multiple eXist servers 
    configured to stay in sync as described below, you could add a load-balancer 
    to distribute the load of incoming queries across the pool of servers 
    and still maintain high performance."

    Quoted from http://en.wikibooks.org/wiki/XQuery/eXist_Clustering 

Fortunately the steps are not too complex.


Preparation
===========

ActimeMQ
--------
- Download recent version from ActiveMQ from http://activemq.apache.org/download.html ; 
  Note that the TGZ file has additional unix (linux, MacOsX) support, the ZIP file
  is for Windows. The contents of the archives actually differ.
- Extract content to disk, refered as ACTIVEMQ_HOME
- Copy the activemq-all-X.Y.Z.jar file to EXIST_HOME/lib/user

eXistdb
-------
- Build replication extension (modify extensions/local.build.properties) or copy the
  pre-built version of exist-replication.jar to lib/extensions



Get Started
===========
- For 'Master' server (publisher)
  - Create collection '/db/mycollection/' that shall be monitored for document changes
  - Create collection '/db/system/config/db/mycollection/'
  - Create in there a document 'collection.xconf' and add the following content to 
    the document:

    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <triggers>
            <trigger class="org.exist.replication.jms.publish.ReplicationTrigger">

                <parameter name="java.naming.factory.initial" value="org.apache.activemq.jndi.ActiveMQInitialContextFactory"/>
                <parameter name="java.naming.provider.url" value="tcp://myserver.local:61616"/>

                <parameter name="connectionfactory" value="ConnectionFactory"/>
                <parameter name="topic" value="dynamicTopics/eXistdb"/>

                <!-- Set value -->
                <parameter name="client-id" value="SetPublisherId"/>  

            </trigger>
        </triggers>
    </collection>

  - Set the correct value for 'java.naming.provider.url' that matches your message broker
  - Set a unique value for the "client-id" parameter.
  

- For each 'Slave' (subscriber)
  - Add a startup trigger to conf.xml:

    <trigger class="org.exist.replication.jms.subscribe.MessageReceiverStartupTrigger">>

        <parameter name="java.naming.factory.initial" value="org.apache.activemq.jndi.ActiveMQInitialContextFactory"/>
        <parameter name="java.naming.provider.url" value="tcp://myserver.local:61616"/>

        <parameter name="connectionfactory" value="ConnectionFactory"/>
        <parameter name="topic" value="dynamicTopics/eXistdb"/>

        <!-- set values -->
        <parameter name="client-id" value="SetSubscriberId"/> 
        <parameter name="subscriber-name" value="SetSubscriptionId"/> 

    </trigger>

  - Set the correct value for 'java.naming.provider.url' that matches your message broker
  - Set unique values for "client-id" and 'subscriber-name'

  - Create the collection '/db/mycollection/' , this is the collection that receives the 
    documents that are updated in the same collection on the 'Master' server.

    


Start-up
--------
- Start ActiveMQ server:
    cd ACTIVEMQ_HOME
    ./bin/activemq start [for mac, use the bin/macosx wrapper directory]
    
- Start Slave 
    cd EXISTSLAVE_HOME
    ./bin/startup.sh

- Start Master
    cd EXISTMASTER_HOME
    ./bin/startup.sh

    
Distribute
----------
- Create a document in the master server in '/db/mycollection/' (e.g. using the 
  java client or eXide; login as admin);  The document will be automatically 
  replicated to the slave servers.

 
Performance Test
----------------
- With eXide upload a +- 50k XML document store as /db/mydoc.xml
  Execute the query, check the timing on the slave (see exist.log)

  let $doc := doc('/db/mydoc.xml')
  for $i in (1000 to 3000)
  return
    xmldb:store('/db/mycollection', concat('mydoc', $i , ".xml"), $doc)
