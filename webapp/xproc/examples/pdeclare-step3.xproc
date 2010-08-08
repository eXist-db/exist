<?xml version="1.0" encoding="UTF-8"?>
<p:pipeline xmlns:c="http://www.w3.org/ns/xproc-step" xmlns:p="http://www.w3.org/ns/xproc"
   xmlns:xproc="http://xproc.net/xproc" xmlns:mine="http://www.example.org/mine" name="pipeline">

   <p:declare-step type="mine:teststep">
       <p:output port="result" primary="true"/>
        <p:xquery xproc:preserve-context="true">
            <p:input port="source"/>
            <p:input port="query">
               <p:inline>
                   <c:query xproc:escape="true">
                       <request hostname="">
                            <urlparamnames>{request:get-parameter-names()}</urlparamnames>
                            <cookienames>{request:get-cookie-names()}</cookienames>
                            <headernames>{request:get-header-names()}</headernames>
                       </request>
                   </c:query>
               </p:inline>
            </p:input>
         </p:xquery>
   </p:declare-step>

   <mine:teststep name="gggg"/>
</p:pipeline>