<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  xmlns:status="http://exist.sourceforge.net/generators/status" 
  version="1.0">

	<xsl:param name="display"/>
	<xsl:param name="refresh"/>
	
	<xsl:variable name="_display">
		<xsl:choose>
			<xsl:when test="$display"><xsl:value-of select="$display"/></xsl:when>
			<xsl:otherwise>system</xsl:otherwise>
		</xsl:choose>
	</xsl:variable>
	
	<xsl:variable name="_refresh">
		<xsl:choose>
			<xsl:when test="$refresh"><xsl:value-of select="$refresh"/></xsl:when>
			<xsl:otherwise>60</xsl:otherwise>
		</xsl:choose>
	</xsl:variable>
	
	<xsl:template match="status:status">
    	<html>
	      	<head>
	      		<META HTTP-EQUIV="refresh" 
	      			CONTENT="{$_refresh}; URL=status?refresh={$_refresh}&amp;display={$_display}"/>
	        	<style type="text/css">
		          body {
		          	font-family: Arial, Helvetica, sans-serif;
		          	color: black;
		          }
		          
		          table.status {
		          	margin-top: 25px;
		          	border: 1px solid black;
		          }
		          
		          td.used {
		          	background: #AA4444;
		          	color: #AA4444;
		          }
		          
		          td.free {
		          	background: #44AA44;
		          	color: #44AA44;
		          }
		          
		          td.max {
		          	background: #C0C0C0;
		          	color: #C0C0C0;
		          }
		          
		          td.heading {
		          	color: #3333FF;
		          	font-size: 16pt;
		          }
		          
				  table.shutdown {
					  background-color: #6666AA;
				  }

		          span.display {
		          	color: #FFFFFF;
		          }
		        </style>
	      		<link rel="shortcut icon" href="resources/exist_icon_16x16.ico"/>
	      		<link rel="icon" href="resources/exist_icon_16x16.png" type="image/png"/>
				<title><xsl:value-of select="@title"/></title>
			</head>
	
			<body>
				<table border="0" width="650">
					<tr>
						<td><img src="logo.jpg"/></td>
					</tr>
					<tr>
						<td class="heading">Database Status</td>
					</tr>
					<tr>
						<td>
							<form method="get" action="status" name="main">
								<table border="0" width="100%" cellspacing="0" cellpadding="2">
									<tr bgcolor="#6666AA">
										<td align="left">
											<span class="display">Display: </span>
											<select name="display" size="1" onChange="submit()">
												<option>
													<xsl:if test="$_display='system'">
														<xsl:attribute name="selected">true</xsl:attribute>
													</xsl:if>
													system
												</option>
												<option>
													<xsl:if test="$_display='instances'">
														<xsl:attribute name="selected">true</xsl:attribute>
													</xsl:if>
													instances
												</option>
												<option>
													<xsl:if test="$_display='buffers'">
														<xsl:attribute name="selected">true</xsl:attribute>
													</xsl:if>
													buffers
												</option>
											</select>
										</td>
										<td align="right">
											<span class="display"> Refresh every: </span>
											<select name="refresh" size="1" onChange="submit()">
												<option>
													<xsl:if test="$_refresh='15'">
														<xsl:attribute name="selected">true</xsl:attribute>
													</xsl:if>
													15
												</option>
												<option>
													<xsl:if test="$_refresh='30'">
														<xsl:attribute name="selected">true</xsl:attribute>
													</xsl:if>
													30
												</option>
												<option>
													<xsl:if test="$_refresh='60'">
														<xsl:attribute name="selected">true</xsl:attribute>
													</xsl:if>
													60
												</option>
												<option>
													<xsl:if test="$_refresh='120'">
														<xsl:attribute name="selected">true</xsl:attribute>
													</xsl:if>
													120
												</option>
											</select>
											<span class="display"> sec.</span>
										</td>
									</tr>
								</table>
							</form>
							<xsl:choose>
								<xsl:when test="$_display='system'">
									<xsl:apply-templates select="status:system"/>
								</xsl:when>
								<xsl:when test="$_display='instances'">
									<xsl:apply-templates select=".//status:database-instance[@name='exist']"/>
								</xsl:when>
								<xsl:when test="$_display='buffers'">
									<xsl:apply-templates select=".//status:database-instance[@name='exist']/status:buffers"/>
								</xsl:when>
							</xsl:choose>
						</td>
					</tr>
				</table>
				<p></p>
				<table class="shutdown" border="0" width="650" cellpadding="0" cellspacing="0">
					<tr bgcolor="6666AA">
						<form action="shutdown" method="POST">
						<td>
							<span class="display">User (dba group): </span> 
							<input type="text" name="user" size="15"/>
							<span class="display"> Password: </span> 
							<input type="password" name="password"
								size="15"/>
						</td>
						<td>
							<input type="submit" value="Shutdown"/>
						</td>
						</form>
					</tr>
				</table>
			</body>
    	</html>
	</xsl:template>
  
  	<xsl:template match="status:system">
  		<table class="status" border="0" cellspacing="7">
  			<tr>
  				<th align="left" colspan="2">System</th>
  			</tr>
  			<xsl:apply-templates select="status:os"/>
			<xsl:apply-templates select="status:jvm"/>
  			<tr>
  				<td width="20%">Memory</td>
  				<td><xsl:apply-templates select="status:memory"/></td>
  			</tr>
  		</table>
  	</xsl:template>
  	
  	<xsl:template match="status:jvm">
  		<tr>
  			<td width="20%">Java Version</td>
  			<td><xsl:value-of select="status:version"/></td>
  		</tr>
  		<tr>
  			<td width="20%">Java Vendor</td>
  			<td><xsl:value-of select="status:vendor"/></td>
  		</tr>
		<tr>
			<td width="20%">Default Locale</td>
			<td><xsl:value-of select="status:locale"/></td>
		</tr>
		<tr>
			<td width="20%">Default Charset</td>
			<td><xsl:value-of select="status:charset"/></td>
		</tr>
  	</xsl:template>
  	
  	<xsl:template match="status:os">
  		<tr>
  			<td width="20%">Operating System</td>
  			<td><xsl:value-of select="status:name"/></td>
  		</tr>
  		<tr>
  			<td width="20%">Architecture</td>
  			<td><xsl:value-of select="status:architecture"/></td>
  		</tr>
  		<tr>
  			<td width="20%">OS Version</td>
  			<td><xsl:value-of select="status:version"/></td>
  		</tr>
  	</xsl:template>
  	
  	<xsl:template match="status:database-instances">
  		<xsl:apply-templates/>
  	</xsl:template>
  	
  	<xsl:template match="status:database-instance">
  		<table class="status" border="0" width="70%" cellspacing="7">
  			<tr>
  				<th align="left" colspan="2">Database &quot;<xsl:value-of select="@name"/>&quot;</th>
  			</tr>
  			<tr>
  				<td width="20%" valign="top">Configuration</td>
  				<td><xsl:value-of select="status:configuration"/></td>
  			</tr>
  			<tr>
  				<td width="20%" valign="top">Data Directory</td>
  				<td><xsl:value-of select="status:data-directory"/></td>
  			</tr>
  			<tr>
  				<td width="20%" valign="top">Pool</td>
  				<td><xsl:apply-templates select="status:pool"/></td>
  			</tr>
  		</table>
  	</xsl:template>
  	
  	<xsl:template match="status:buffers">
  
		<!-- added section for Cache Used monitoring -->
  		<xsl:variable name="psize" select="number(../status:page-size)"/>
		<xsl:variable name="tused" select="format-number(sum(status:file[not(contains(@name, 'collections'))]/status:buffer/status:used)*$psize div 1048576, '###.#')"/>
  		<xsl:variable name="tsize" select="format-number(sum(status:file[not(contains(@name, 'collections'))]//status:buffer/status:size)*$psize div 1048576, '###.#')"/>
		<xsl:variable name="tmaxcol" select="number(../status:collection-cache-mem)"/>
		<xsl:variable name="tmax" select="number(../status:cache-size) - $tmaxcol"/>
		<xsl:variable name="tpcnt" select="format-number(($tused div $tmax) * 100, '#0')"/>

  		<xsl:variable name="tusedpcnt" select="round(($tused div $tmax) * 100)"/>
  		<xsl:variable name="tsizepcnt" select="round(($tsize div $tmax) * 100)"/>
		
		<table class="status" border="0" width="400" cellspacing="7">
  			<tr>
  				<th align="left" colspan="4">Cache Usage: <xsl:value-of select="$tpcnt"/> %</th>
   			</tr>
		 	<tr>
				<td align="left">
					<table border="0" width="400" cellpadding="0" cellspacing="0">
						<tr>
							<td/>
							<td width="{$tusedpcnt * 4}" class="used"></td>
							<td width="{($tsizepcnt - $tusedpcnt) * 4}" class="free"></td>
							<td width="{(100 - ($tsizepcnt)) * 4}" class="max">:</td>
						</tr>
						 <tr>
							 <td/>
							 <td colspan="3">
								 <small>
									Used: <xsl:value-of select="$tused"/> Mb; 
									Size: <xsl:value-of select="$tsize"/> Mb; 
									Max: <xsl:value-of select="$tmax"/> Mb 
								</small>
							</td>
						</tr>
					</table>
				</td>	
			</tr> 
  		</table>

		<xsl:variable name="tusedcol" select="format-number(sum(status:file[contains(@name, 'collections')]/status:buffer/status:used)*$psize div 1048576, '###.#')"/>
		<xsl:variable name="tsizecol" select="format-number(sum(status:file[contains(@name, 'collections')]/status:buffer/status:size)*$psize div 1048576, '###.#')"/>
   		<xsl:variable name="tusedcolpcnt" select="round(($tusedcol div $tmaxcol) * 100)"/>
  		<xsl:variable name="tsizecolpcnt" select="round(($tsizecol div $tmaxcol) * 100)"/>

		<xsl:variable name="tpcntcol" select="format-number(($tusedcol div $tmaxcol) * 100, '#0')"/>

 		<table class="status" border="0" width="400" cellspacing="7">
  			<tr>
  				<th align="left" colspan="4">Collection Cache Usage: <xsl:value-of select="$tpcntcol"/> %</th>
   			</tr>
		 	<tr>
				<td align="left">
					<table border="0" width="400" cellpadding="0" cellspacing="0">
						<tr>
							<td/>
							<td width="{$tusedcolpcnt * 4}" class="used"></td>
							<td width="{($tsizecolpcnt - $tusedcolpcnt) * 4}" class="free"></td>
							<td width="{(100 - ($tsizecolpcnt)) * 4}" class="max">:</td>
						</tr>
						 <tr>
							 <td/>
							 <td colspan="3">
								 <small>
									Used: <xsl:value-of select="$tusedcol"/> Mb; 
									Size: <xsl:value-of select="$tsizecol"/> Mb; 
									Max: <xsl:value-of select="$tmaxcol"/> Mb 
								</small>
							</td>
						</tr>
					</table>
				</td>	
			</tr> 
  		</table>

		<table class="status" border="0" width="70%" cellspacing="7">
  			<tr>
  				<th align="left" colspan="2">Buffer Statistics</th>
  			</tr>
  			<xsl:apply-templates/>
  		</table>
  	</xsl:template>
  	
  	<xsl:template match="status:file">
  		<tr>
  			<td width="20%" valign="top"><xsl:value-of select="@name"/></td>
  			<td>
  				<table width="100%" border="0" cellspacing="0">
  					<xsl:apply-templates select="status:buffer"/>
  				</table>
  			</td>
  		</tr>
  	</xsl:template>
  	
  	<xsl:template match="status:buffer">
  		<xsl:variable name="psize" select="number(status:size) div 100"/>
  		<xsl:variable name="pused" select="round(number(status:used) div $psize)"/>
  		<xsl:variable name="thits" select="(status:hits + status:fails) div 100"/>
  		<xsl:variable name="phits" select="round(status:hits div $thits)"/>
  		<xsl:variable name="pfails" select="round(status:fails div $thits)"/>
  		<tr>
  			<td valign="top" align="left"><xsl:value-of select="@type"/></td>
  			<td align="left">
  				<table border="0" width="400" cellpadding="0" cellspacing="0">
  					<tr>
  						<td width="{$pused * 4}" class="used"></td>
  						<td width="{(100 - $pused) * 4}" class="max">:</td>
  					</tr>
  					<tr>
  						<td colspan="2">
  							<small>
  								Used: <xsl:value-of select="status:used"/>;
  								Size: <xsl:value-of select="status:size"/>
  							</small>
  						</td>
  					</tr>
  				</table>
  				<table border="0" width="400" cellpadding="0" cellspacing="0">
  					<tr>
  						<td width="{$pfails * 4}" class="used">:</td>
  						<td width="{$phits * 4}" class="free"></td>
  					</tr>
  					<tr>
  						<td colspan="2">
  							<small>
  								Fails: <xsl:value-of select="status:fails"/>
  								Hits: <xsl:value-of select="status:hits"/>;
  							</small>
  						</td>
  					</tr>
  				</table>
  			</td>
  		</tr>
  	</xsl:template>
  	
  	<xsl:template match="status:pool">
  		<xsl:variable name="pmax" select="number(status:max) div 100"/>
		<xsl:variable name="pactive" select="round(number(status:active) div $pmax)"/>
		<xsl:variable name="pavail" select="round(number(status:available) div $pmax)"/>
		<table border="0" width="500" cellpadding="0" cellspacing="0">
  			<tr>
  				<td width="{($pactive - $pavail) * 5}" class="used"></td>
  				<td width="{$pavail * 5}" class="free"></td>
  				<td width="{(100 - $pactive) * 5}" class="max">:</td>
  			</tr>
  			<tr>
  				<td colspan="3">
  					<small>
  						Running = <xsl:value-of select="status:active"/>;
  						Available = <xsl:value-of select="status:available"/>;
  						Max = <xsl:value-of select="status:max"/>
  					</small>
  				</td>
  			</tr>
  		</table>
  	</xsl:template>
  	
	<xsl:template match="status:memory">
		<table border="0" width="100%" cellspacing="7">
			<tr>
				<td>
					<xsl:variable name="max" select="number(status:max) div 100"/>
					<xsl:variable name="pfree" select="round(number(status:free) div $max)"/>
					<xsl:variable name="ptotal" select="round(number(status:total) div $max)"/>
					<table border="0" width="400" cellpadding="0" cellspacing="0">
			  			<tr>
			  				<td width="{($ptotal - $pfree) * 4}" class="used"></td>
			  				<td width="{$pfree * 4}" class="free"></td>
			  				<td width="{(100 - $ptotal) * 4}" class="max">:</td>
			  			</tr>
			  			<tr>
			  				<td colspan="3">
			  					<small>
			  						Used = <xsl:value-of select="round((status:total - status:free) div 1024)"/>K;
			  						Free = <xsl:value-of select="round(number(status:free) div 1024)"/>K;
			  						Max = <xsl:value-of select="round(number(status:max) div 1024)"/>K
			  					</small>
			  				</td>
			  			</tr>
			  		</table>
			  	</td>
			</tr>
		</table>
  	</xsl:template>
</xsl:stylesheet>
        
