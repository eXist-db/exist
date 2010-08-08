<?xml version="1.0"?>
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" xmlns:compress="http://exist-db.org/xquery/compression" xmlns:xproc="http://xproc.net/xproc" name="aaa">
	<p:input port="source"/>
	<p:output port="result"/>

	<p:declare-step type="compress:zip">
		<p:output port="result" primary="true"/>
        <p:option/>
		<p:xquery xproc:preserve-context="true">
			<p:input port="query">
				<p:inline>
					<d:query xmlns:d="http://www.w3.org/ns/xproc-step" xproc:escape="true">
						<d:result>{compression:zip(<entry name="/db/xproc/examples" type="collection"/>, true())}</d:result>
					</d:query>
				</p:inline>
			</p:input>
		</p:xquery>
	</p:declare-step>

	<compress:zip name="a"/>
	
</p:declare-step>