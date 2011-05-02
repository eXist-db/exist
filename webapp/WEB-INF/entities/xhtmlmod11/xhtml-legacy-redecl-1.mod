<!-- ...................................................................... -->
<!-- XHTML Legacy Redeclarations Module ................................... -->
<!-- file: xhtml-legacy-redecl-1.mod

     This is an extension of XHTML, a reformulation of HTML as a modular XML application.
     Copyright 1998-2005 W3C (MIT, ERCIM, Keio), All Rights Reserved.
     Revision: $Id: xhtml-legacy-redecl-1.mod,v 4.4 2001/04/10 09:42:30 altheim Exp $ SMI

     This DTD module is identified by the PUBLIC and SYSTEM identifiers:

       PUBLIC "-//W3C//ELEMENTS XHTML Legacy Redeclarations 1.0//EN"
       SYSTEM "http://www.w3.org/MarkUp/DTD/xhtml-legacy-redecl-1.mod"

     Revisions:
     (none)
     ....................................................................... -->


<!-- Legacy Redeclarations

     This optional module replaces the Modular Framework module, interspersing
     redeclarations of various parameter entities to allow for inclusions of
     Transitional markup in the XHTML 1.1 document model. This instantiates
     the modules needed to support the XHTML modularization model, including:

        +  notations
        +  datatypes
        +  namespace-qualified names
        +  common attributes
        +  document model
        +  character entities

     By default, the Object module is included, with Frames and IFrames ignored.

     The Intrinsic Events module is ignored by default but
     occurs in this module because it must be instantiated
     prior to Attributes but after Datatypes.
-->
<!ENTITY % xhtml-legacy.module "INCLUDE" >

<!ENTITY % xhtml-arch.module "IGNORE" >
<![%xhtml-arch.module;[
<!ENTITY % xhtml-arch.mod
     PUBLIC "-//W3C//ELEMENTS XHTML Base Architecture 1.0//EN"
            "xhtml-arch-1.mod" >
%xhtml-arch.mod;]]>

<!ENTITY % xhtml-notations.module "INCLUDE" >
<![%xhtml-notations.module;[
<!ENTITY % xhtml-notations.mod
     PUBLIC "-//W3C//NOTATIONS XHTML Notations 1.0//EN"
            "xhtml-notations-1.mod" >
%xhtml-notations.mod;]]>

<!-- Datatypes Module ............................................ -->
<!ENTITY % xhtml-datatypes.module "INCLUDE" >
<![%xhtml-datatypes.module;[
<!ENTITY % xhtml-datatypes.mod
     PUBLIC "-//W3C//ENTITIES XHTML Datatypes 1.0//EN"
            "xhtml-datatypes-1.mod" >
%xhtml-datatypes.mod;]]>

<!-- Qualified Names Module ...................................... -->
<!ENTITY % xhtml-qname.module "INCLUDE" >
<![%xhtml-qname.module;[
<!ENTITY % xhtml-qname.mod
     PUBLIC "-//W3C//ENTITIES XHTML Qualified Names 1.0//EN"
            "xhtml-qname-1.mod" >
%xhtml-qname.mod;]]>

<!-- Additional Qualified Names .................................. -->

<!-- xhtml-legacy-1.mod -->
<!ENTITY % font.qname     "%XHTML.pfx;font" >
<!ENTITY % basefont.qname "%XHTML.pfx;basefont" >
<!ENTITY % center.qname   "%XHTML.pfx;center" >
<!ENTITY % s.qname        "%XHTML.pfx;s" >
<!ENTITY % strike.qname   "%XHTML.pfx;strike" >
<!ENTITY % u.qname        "%XHTML.pfx;u" >
<!ENTITY % dir.qname      "%XHTML.pfx;dir" >
<!ENTITY % menu.qname     "%XHTML.pfx;menu" >
<!ENTITY % isindex.qname  "%XHTML.pfx;isindex" >

<!-- xhtml-frames-1.mod -->
<!ENTITY % frameset.qname "%XHTML.pfx;frameset" >
<!ENTITY % frame.qname    "%XHTML.pfx;frame" >
<!ENTITY % noframes.qname "%XHTML.pfx;noframes" >

<!-- xhtml-iframe-1.mod -->
<!ENTITY % iframe.qname   "%XHTML.pfx;iframe" >

<!ENTITY % xhtml-events.module "IGNORE" >
<![%xhtml-events.module;[
<!ENTITY % xhtml-events.mod
     PUBLIC "-//W3C//ENTITIES XHTML Intrinsic Events 1.0//EN"
            "xhtml-events-1.mod" >
%xhtml-events.mod;]]>

<!-- Additional Common Attributes ................................ -->

<!-- include historical 'lang' attribute (which should
     always match the value of 'xml:lang')
-->
<!ENTITY % lang.attrib
     "xml:lang     %LanguageCode.datatype;  #IMPLIED
      lang         %LanguageCode.datatype;  #IMPLIED"
>

<!-- Common Attributes Module .................................... -->
<!ENTITY % xhtml-attribs.module "INCLUDE" >
<![%xhtml-attribs.module;[
<!ENTITY % xhtml-attribs.mod
     PUBLIC "-//W3C//ENTITIES XHTML Common Attributes 1.0//EN"
            "xhtml-attribs-1.mod" >
%xhtml-attribs.mod;]]>

<!-- placeholder for content model redeclarations -->
<!ENTITY % xhtml-model.redecl "" >
%xhtml-model.redecl;

<!-- Document Model Redeclarations ............................... -->

<!ENTITY % InlPres.class
     "| %tt.qname; | %i.qname; | %b.qname; | %big.qname;
      | %small.qname; | %sub.qname; | %sup.qname;
      | %font.qname; | %basefont.qname; | %iframe.qname;
      | %s.qname; | %strike.qname; | %u.qname;"
>

<!ENTITY % InlSpecial.class
     "| %img.qname; | %map.qname; 
      | %applet.qname; | %object.qname;" >

<!ENTITY % BlkPres.class
     "| %hr.qname; | %center.qname;"
>

<!ENTITY % BlkSpecial.class
     "| %table.qname; | %form.qname; | %fieldset.qname;
      | %noframes.qname; | %isindex.qname;"
>

<!ENTITY % List.class
     "%ul.qname; | %ol.qname; | %dl.qname;
      | %dir.qname; | %menu.qname;"
>

<!-- Document Model Module  ...................................... -->
<!ENTITY % xhtml-model.module "INCLUDE" >
<![%xhtml-model.module;[
<!-- instantiate the Document Model module declared in the DTD driver
-->
%xhtml-model.mod;]]>

<!ENTITY % blockquote.content
     "( #PCDATA | %Flow.mix; )*"
>

<!ENTITY % noscript.content
      "( #PCDATA | %Flow.mix; )*"
>

<!ENTITY % body.content
     "( #PCDATA | %Flow.mix; )*"
>

<!-- redeclare content model of <html> to allow for either
     body or frameset content. The SGML markup minimization
     features used in HTML 4 do not apply, so the ambiguity
     that necessitated separation into the separate Frameset
     and Transitional DTDs is eliminated.
-->
<!ENTITY % html.content
     "( %head.qname;, ( %body.qname; | %frameset.qname; ) )"
>


<!ENTITY % xhtml-charent.module "INCLUDE" >
<![%xhtml-charent.module;[
<!ENTITY % xhtml-charent.mod
     PUBLIC "-//W3C//ENTITIES XHTML Character Entities 1.0//EN"
            "xhtml-charent-1.mod" >
%xhtml-charent.mod;]]>

<!-- end of xhtml-legacy-redecl-1.mod -->
