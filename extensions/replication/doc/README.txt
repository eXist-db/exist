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
  is for Windows.
- Extract content to disk, refered as ACTIVEMQ_HOME
- Copy the activemq-all-X.Y.Z.jar file to EXIST_HOME/lib/user

eXistdb
-------
- Build replication extension (modify extensions/build.properties) or copy the
  pre-built version of exist-replication.jar to lib/extensions

- For 'Master' server (publisher)
  - Create collection '/db/mycollection' that shall be monitored for document changes
  - Create collection '/db/system/config/db/mycollection/', add the file collection.xconf
  - Add a collection.xconf file for the directory for which the content below;
    Fill in the right hostname/url of the activemq message broker:
  
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <triggers>
            <trigger class="org.exist.replication.jms.publish.ClusterTrigger">
                <parameter name="java.naming.factory.initial" value="org.apache.activemq.jndi.ActiveMQInitialContextFactory"/>
                <parameter name="java.naming.provider.url" value="tcp://miniserver.local:61616"/>
                <parameter name="connectionfactory" value="ConnectionFactory"/>
                <parameter name="topic" value="dynamicTopics/eXistdb"/>
                <parameter name="client.connection.client-id" value="PublisherId"/>  
                <parameter name="producer.time-to-live" value="0"/>    
		<parameter name="producer.priority" value="4"/> 
            </trigger>
        </triggers>
    </collection>
  
- For each 'Slave' (subscriber), a job must be started via conf.xml; the URL must 
  match the 'Master' configuration of the previous section. Create the collection 
  '/db/mycollection' since the location of the distributed documents will be mirrored 
  from the 'Master' server.

    <job type="startup" name="replication" class="org.exist.replication.jms.subscribe.JMSReceiveJob">
        <parameter name="java.naming.factory.initial" value="org.apache.activemq.jndi.ActiveMQInitialContextFactory"/>
        <parameter name="java.naming.provider.url" value="tcp://miniserver.local:61616"/>

        <parameter name="connectionfactory" value="ConnectionFactory"/>

        <parameter name="topic" value="dynamicTopics/eXistdb"/>

        <parameter name="client.connection.client-id" value="SubscriberId"/>
        <parameter name="client.topicsession.subscriber-name" value="SubscriptionId"/>

        <parameter name="client.nolocal" value="yes"/>
        <parameter name="client.messageselector" value="Foo='Bar'"/>
    </job>


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
- Create a document in the master server in /db/mycollection/ (e.g. using the 
  java client, or eXide ; login as admin);  The document will be automatically 
  replicated to the slave servers.

 
Performance Test
----------------
- With eXide upload a +- 50k XML document store as /db/mydoc.xml
  Execute the query, check the timing on the slave (see exist.log)

  let $doc := doc('/db/mydoc.xml')
  for $i in (1000 to 3000)
  return
    xmldb:store('/db/mycollection', concat('mydoc', $i , ".xml"), $doc)


    
    
    