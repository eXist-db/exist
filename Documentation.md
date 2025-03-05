# Documentation

Here are some diagrams for a better understanding of the project

They are done in UML language.

## Context Diagram

This is a general diagram of eXist that show as some important details of what is eXist.

<img src="Diagrams/Context Diagram.png" alt="Context Diagram" width="500" height="450"></img>

## Use Case Diagram

<img src="Diagrams/CaseOfUsesDiagram.jpg" alt="Case Of Use Diagram" width="550" height="400"></img>

Database Configuration: eXist-db allows the configuration and modification of important database properties related to indexes, triggers, and validation through the /db/system/config collection.

User, Group, and Permission Management: The security of the eXist database is based on an authentication system with users, groups, and permissions. A default configuration is provided, but for applications handling sensitive data, proper permission management is necessary to ensure system security.

Modification of Collection and Resource Properties: Everything stored in the database is associated with a set of properties that can be viewed and modified through different mechanisms, either using functions in the extension module or tools like the eXist Client Tool.

Access to Stored Data: To work with database content, eXist-db offers various ways to access collections, resources, and documents: via URIs (with standard functions to convert names to URIs and vice versa), using database paths—preferably absolute whenever possible, as relative paths can be confusing; through XMLDB URIs, which are equivalent to traditional paths but more explicit and self-documented, using the xmldb:exist:// prefix; and by accessing files outside the database (on the file system) with the file:// prefix.

Storage and Updating of Resources: In eXist, documents can be replaced entirely with new and updated versions, though this may not be efficient for large documents. To address this, eXist provides mechanisms to update XML documents directly, including an XQuery extension, XQuery Update Facility (XQUF), and XUpdate. Updates can also be performed via PUT and POST requests through REST.

Creation and Deletion of Collections and Resources: This can be done via PUT, POST, and DELETE requests through the REST interface or by using the functions provided by eXist.

REST Interface Management: As previously mentioned, the most common and straightforward way to access database content is through the REST interface. This access is regulated by eXist’s security and authentication rules, respecting permissions. However, direct access to the REST server can be disabled if needed.

## Components Diagram

<img src="Diagrams/ComponentsDiagram.png" alt="Component Diagram" width="600" height="400"></img>

Tools: A component designed for developers to carry out additional eXist-DB development tasks.

Configuration: This component includes functions for installing and setting up the system to run the database properly. It is also responsible for configuring Jetty, allowing web applications to run without external servers. Additionally, it handles the configuration of Docker for its usage.

Miscellaneous: This component gathers a variety of functions. Notable features include database initialization—whether manually, automatically, or as a service—and the ability to conduct tests to validate XQuery implementation.

Core: This is the main component of eXist. It includes the query engines, data storage, and interfaces for data access. Its implementation is detailed below.

Extensions: eXist currently has the capability to add external functionalities through extensions, which can be highly useful for handling different scenarios or adapting to an application that uses eXist. One example is an SQL module, which allows the use of SQL instead of the traditional XQuery for this type of database.

### A component diagram of the core

<img src="Diagrams/CoreDiagram.jpg" alt="Core Diagram" width="600" height="400"></img>

Query Engine: The query engine primarily implements two functions. First, XQuery, a query language that efficiently retrieves XML data while also enabling document updates through insertions, deletions, or modifications. Second, XSLT (Extensible Stylesheet Language Transformations), which transforms XML documents into other formats and filters or restructures XML data.

Update Modules: In eXist, these modules are responsible for modifying XML documents. They handle adding, modifying, or deleting nodes within the database while optimizing structured data updates. These updates are carried out using XUpdate or XQUF.

Storage and Indexing System: This is divided into two main functionalities:

  -Transaction Manager: Handles database transactions, ensuring compliance with ACID properties. In case of an error, it relies on the recovery and backup component to restore the database.

  -Index Manager: Manages and optimizes indexes to enhance performance, as well as coordinates data indexing. It includes several types of indexes: tree-based indexes, full-text indexes, sorting indexes, structural indexes, NGram indexes, FT indexes, and spatial indexes.

Recovery and Backup: A component responsible for restoring the system in case of database failures or other incidents.

Access Interfaces: These interfaces allow interaction with the database to perform queries or manipulate stored data. Different components provide access interfaces, including: REST Server, SOAP Server, RestXQ, WebDAV, XML:DB Remote API, XML/RPC API, and Axis API.

Security Module: eXist's security module is responsible for protecting XML data and controlling user access through mechanisms such as authentication, authorization, and encryption.
