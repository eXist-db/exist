	<tr bgcolor="#DDDDDD">
		<td align="left">
			<form action="<%= response.encodeURL(request.getRequestURI()) %>"
				method="GET">
				Refresh every
				<select name="refresh" size="1">
					<option>15</option>
					<option>30</option>
					<option selected="true">60</option>
					<option>120</option>
					<option>240</option>
				</select>
				seconds.
				<input type="submit" value="Refresh"/>
			</form>
		</td>
	</tr>