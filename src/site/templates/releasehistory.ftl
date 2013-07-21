<#include "macros.ftl" >

<!-- HISTORY -->
<#if (releases!?size > 0)>
	<p></p>
	<h2>All Releases</h2>
	<table class="table">
		<tbody>
		<!-- RELEASE HISTORY -->
		<#list releases?sort_by("date")?reverse as log>
		<tr id="${log.id}">
			<td style="width:100px" id="${log.id}">
				<b><a href="#${log.id}">${log.id}</a></b><br/>
				${log.date?string("yyyy-MM-dd")}
			</td>
			<td><@LogDescriptionMacro log=log /></td>
		</tr>
		</#list>
		</tbody>
	</table>
</#if>