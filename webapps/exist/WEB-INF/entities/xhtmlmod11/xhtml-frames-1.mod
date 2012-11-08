<!-- ...................................................................... -->
<!-- XHTML Frames Module  ................................................. -->
<!-- file: xhtml-frames-1.mod

     This is XHTML, a reformulation of HTML as a modular XML application.
     Copyright 1998-2005 W3C (MIT, ERCIM, Keio), All Rights Reserved.
     Revision: $Id: xhtml-frames-1.mod,v 4.0 2001/04/02 22:42:49 altheim Exp $ SMI

     This DTD module is identified by the PUBLIC and SYSTEM identifiers:

       PUBLIC "-//W3C//ELEMENTS XHTML Frames 1.0//EN"
       SYSTEM "http://www.w3.org/MarkUp/DTD/xhtml-frames-1.mod"

     Revisions:
     (none)
     ....................................................................... -->

<!-- Frames 

        frameset, frame, noframes

     This module declares frame-related element types and attributes.
-->

<!ENTITY % frameset.qname  "frameset" >
<!ENTITY % frame.qname  "frame" >
<!ENTITY % noframes.qname  "noframes" >

<!-- comma-separated list of MultiLength -->
<!ENTITY % MultiLengths.datatype "CDATA" >

<!-- The content model for XHTML documents depends on whether 
     the <head> is followed by a <frameset> or <body> element. 
--> 
 
<!ENTITY % frameset.element  "INCLUDE" >
<![%frameset.element;[
<!ENTITY % frameset.content 
     "(( %frameset.qname; | %frame.qname; )+, %noframes.qname;? )" >
<!ELEMENT %frameset.qname;  %frameset.content; >
<!-- end of frameset.element -->]]>

<!ENTITY % frameset.attlist  "INCLUDE" >
<![%frameset.attlist;[
<!ATTLIST %frameset.qname; 
      %Core.attrib;
      rows         %MultiLengths.datatype;  #IMPLIED
      cols         %MultiLengths.datatype;  #IMPLIED
> 
<!-- end of frameset.attlist -->]]>
<![%xhtml-events.module;[
<!ATTLIST %frameset.qname;
      onload       %Script.datatype;        #IMPLIED
      onunload     %Script.datatype;        #IMPLIED
>
]]>
 
<!-- reserved frame names start with "_" otherwise starts with letter --> 

<!ENTITY % frame.element  "INCLUDE" >
<![%frame.element;[
<!ENTITY % frame.content  "EMPTY" >
<!ELEMENT %frame.qname;  %frame.content; >
<!-- end of frame.element -->]]>

<!ENTITY % frame.attlist  "INCLUDE" >
<![%frame.attlist;[
<!ATTLIST %frame.qname; 
      %Core.attrib;
      longdesc     %URI.datatype;           #IMPLIED
      src          %URI.datatype;           #IMPLIED
      frameborder  ( 1 | 0 )                '1'
      marginwidth  %Pixels.datatype;        #IMPLIED
      marginheight %Pixels.datatype;        #IMPLIED
      noresize     ( noresize )             #IMPLIED
      scrolling    ( yes | no | auto )      'auto'
> 
<!-- end of frame.attlist -->]]>
 
<!-- changes to other declarations .................... -->

<!-- redefine content model for html element,
     substituting frameset for body  -->
<!ENTITY % html.content  
     "( %head.qname;, %frameset.qname; )"
>

<!-- alternate content container for non frame-based rendering --> 
 
<!ENTITY % noframes.element  "INCLUDE" >
<![%noframes.element;[
<!ENTITY % noframes.content "( %body.qname; )"> 
<!ELEMENT %noframes.qname;  %noframes.content; >
<!-- end of noframes.element -->]]>

<!ENTITY % noframes.attlist  "INCLUDE" >
<![%noframes.attlist;[
<!ATTLIST %noframes.qname; 
      %Common.attrib;
> 
<!-- end of noframes.attlist -->]]>

<!-- end of xhtml-frames-1.mod -->
