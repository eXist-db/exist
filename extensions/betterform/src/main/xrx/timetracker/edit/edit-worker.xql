xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace session="http://exist-db.org/xquery/session";
import module namespace util="http://exist-db.org/xquery/util";
declare option exist:serialize "method=xhtml media-type=text/xml";


declare function local:getWorker() as xs:string{
      let $id := request:get-parameter("id", "")
      let $path2resource := concat("/exist/rest/db/betterform/apps/timetracker/data?_query=/*/worker",encode-for-uri('['), "@id='" ,$id,"'",encode-for-uri(']'))
      return $path2resource
};

declare function local:mode() as xs:string{
    let $mode := request:get-parameter("mode", "")
    return $mode
};



<html   xmlns="http://www.w3.org/1999/xhtml"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:exist="http://exist.sourceforge.net/NS/exist"
        xmlns:ev="http://www.w3.org/2001/xml-events">
   <head>
      <title>Edit Worker</title>
    </head>
    <body>
    	<div id="xforms" style="height:360px;">
        <div style="display:none">
            <xf:model>
              <xf:instance id="i-worker" src="/exist/rest/db/betterform/apps/timetracker/data/worker.xml"/>

             <xf:instance id="i-controller"  src="/exist/rest/db/betterform/apps/timetracker/data/controller.xml"/>


        <xf:submission id="s-add"
                       method="put"
                       replace="none">
		    <xf:resource value="concat('/exist/rest/db/betterform/apps/timetracker/data/worker.xml')"/>

            <xf:header>
                <xf:name>username</xf:name>
                <xf:value>admin</xf:value>
            </xf:header>
            <xf:header>
                <xf:name>password</xf:name>
                <xf:value>admin</xf:value>
            </xf:header>
            <xf:header>
                <xf:name>realm</xf:name>
                <xf:value>exist</xf:value>
            </xf:header>

			<xf:message level="ephemeral" ev:event="xforms-submit-done">Saved changes</xf:message>
			<xf:message level="modal" ev:event="xforms-submit-error">Failure saving changes</xf:message>
        </xf:submission>


		  <xf:submission id="s-get-worker"
						 method="get"
						 resource="{local:getWorker()}"
						 validate="false"
						 replace="instance">
		 </xf:submission>

		<xf:action ev:event="xforms-ready" >
			<xf:send submission="s-get-worker" if="'{local:mode()}' = 'edit'"/>
		</xf:action>
        </xf:model>
    </div>

        <xf:group id="worker-table" appearance="bf:verticalTable" >
                <xf:input id="worker" ref="worker">
                    <xf:label>Name: </xf:label>
                    <xf:alert>Username is not valid</xf:alert>
                </xf:input>

				<xf:trigger style="padding-right:0;">
					<xf:label>Save</xf:label>
					<xf:toggle case="doIt"/>
				</xf:trigger>
		</xf:group>

		<xf:switch>
			<xf:case id="default" selected="true"/>
			<xf:case id="doIt" selected="false">
				<div style="font-weight:bold;">Really add?</div>
				<xf:trigger>
					<xf:label>Yes</xf:label>
					<xf:send submission="s-add"/>
				</xf:trigger>
				<xf:trigger>
					<xf:label>Cancel</xf:label>
					<xf:toggle case="default"/>
				</xf:trigger>
			</xf:case>
		</xf:switch>
        </div>
    </body>
</html>
