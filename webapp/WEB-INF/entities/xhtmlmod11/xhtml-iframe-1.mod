<!-- ...................................................................... -->
<!-- XHTML IFrame Module  ................................................. -->
<!-- file: xhtml-iframe-1.mod

     This is XHTML, a reformulation of HTML as a modular XML application.
     Copyright 1998-2005 W3C (MIT, ERCIM, Keio), All Rights Reserved.
     Revision: $Id: xhtml-iframe-1.mod,v 4.0 2001/04/02 22:42:49 altheim Exp $ SMI

     This DTD module is identified by the PUBLIC and SYSTEM identifiers:

       PUBLIC "-//W3C//ELEMENTS XHTML Inline Frame Element 1.0//EN"
       SYSTEM "http://www.w3.org/MarkUp/DTD/xhtml-iframe-1.mod"

     Revisions:
#2000-10-22: added #PCDATA to <iframe> content model as per HTML 4
     ....................................................................... -->

<!-- Inline Frames

        iframe

     This module declares the iframe element type and its attributes,
     used to create an inline frame within a document.
-->

<!-- Inline Frames .................................... -->

<!ENTITY % iframe.content  "( #PCDATA | %Flow.mix; )*" >
<!ENTITY % iframe.qname  "iframe" >
<!ELEMENT %iframe.qname;  %iframe.content; >
<!ATTLIST %iframe.qname;
      %Core.attrib;
      longdesc     %URI.datatype;           #IMPLIED
      src          %URI.datatype;           #IMPLIED
      frameborder  ( 1 | 0 )                '1'
      marginwidth  %Pixels.datatype;        #IMPLIED
      marginheight %Pixels.datatype;        #IMPLIED
      scrolling    ( yes | no | auto )      'auto'
      height       %Length.datatype;        #IMPLIED
      width        %Length.datatype;        #IMPLIED
>

<!-- end of xhtml-iframe-1.mod -->
