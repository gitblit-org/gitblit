<#macro LogMacro title version date description log logTitle="">
	<#if log??>
	<h3 id="${version}" class="section"><a href="#${version}" class="sectionlink"><i class="icon-share-alt"> </i></a>${title} (${version}) <small>${description}</small></h3>
	<table class="table">
		<tbody>
			<tr>
				<td style="background-color:inherit;width:100px">${date}</td>
				<td style="background-color:inherit;"><@LogDescriptionMacro log=log title=logTitle /></td>
			</tr>
		</tbody>
	</table>
	</#if>
</#macro>

<#macro LogDescriptionMacro log title=log.title>
	<#if (title!?length > 0)>
		<p class="lead">${title}</p>		
	</#if>
	
	<#if (log.html!?length > 0)>
		<p>${log.html}</p>
	</#if>
	
	<#if (log.text!?length > 0)>
		<blockquote><p>${log.text!?html?replace("\n", "<br />")}</p></blockquote>		
	</#if>

	<#if (log.note!?length > 0)>
		<div class="alert alert-info">
			<h4>Note</h4>
			${log.note?html?replace("\n", "<p />")}
		</div>
	</#if>

	<#if (log.security!?size > 0)>
		<@SecurityListMacro title="security" list=log.security/>
	</#if>
	<#if (log.fixes!?size > 0)>
		<@UnorderedListMacro title="fixes" list=log.fixes />
	</#if>
	<#if (log.changes!?size > 0)>
		<@UnorderedListMacro title="changes" list=log.changes />
	</#if>
	<#if (log.additions!?size > 0)>
		<@UnorderedListMacro title="additions" list=log.additions />
	</#if>
	<#if (log.settings!?size > 0)>
		<@SettingsTableMacro title="new settings" list=log.settings />		
	</#if>
	<#if (log.dependencyChanges!?size > 0)>
		<@UnorderedListMacro title="dependency changes" list=log.dependencyChanges />
	</#if>
	<#if (log.contributors!?size > 0)>
		<@UnorderedListMacro title="contributors" list=log.contributors?sort />
	</#if>	
</#macro>

<#macro SecurityListMacro list title>
   	<h4 style="color:red;">${title}</h4>
	<ul>
	<#list list as item>
		<li>${item?html?replace("\n", "<br/>")}</li>
	</#list>
	</ul>
</#macro>

<#macro UnorderedListMacro list title>
   	<h4>${title}</h4>
	<ul>
	<#list list as item>
		<li>${item?html?replace("\n", "<br/>")}</li>
	</#list>
	</ul>
</#macro>

<#macro SettingsTableMacro list title>
   	<h4>${title}</h4>
	<table class="table">
		<#list list as item>
		<tr>
			<td><em>${item.name}</em></td><td>${item.defaultValue}</td>
		</tr>
		</#list>
	</table>
</#macro>

<#macro RssMacro posts posturl>
<?xml version="1.0" standalone='yes'?>
<rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
	<channel>
		<title><![CDATA[${project.name}]]></title>
		<link>${project.url}</link>
		<description><![CDATA[${project.description}]]></description>
		<generator>Moxie Toolkit</generator>
		<#list posts as post>
  		<item>
    		<title><![CDATA[${post.title}]]></title>
    		<link><![CDATA[${posturl}${post.id}]]></link>
    		<guid isPermaLink="true">${posturl}${post.id}</guid>
	   		<#if (post.text!?length > 0)>
    		<description><![CDATA[${post.text}]]></description>
    		</#if>
    		<#if (post.keywords!?size > 0)>
   			<#list post.keywords as keyword>
			<category><![CDATA[${keyword}]]></category>
   			</#list>
    		</#if>
    		<#if (post.author!?length > 0)>
  			<dc:creator><![CDATA[${post.author}]]></dc:creator>
    		<#else>
   			<dc:creator><![CDATA[${project.name}]]></dc:creator>
    		</#if>
    		<pubDate>${post.date?string("EEE, dd MMM yyyy HH:mm:ss Z")}</pubDate>
  		</item>
  		</#list>
  	</channel>
</rss>
</#macro>

<#macro AtomMacro posts posturl>
<?xml version="1.0" standalone='yes'?>
<feed xmlns="http://www.w3.org/2005/Atom">
	<generator uri="${project.url}" version="${project.version}">${project.name}</generator>
	<title><![CDATA[${project.name}]]></title>
	<updated>${project.releaseDate}</updated>
	<#list posts as post>
	<entry>
		<content type="text/plain" />
   		<title type="text"><![CDATA[${post.title}]]></title>
   		<#if (post.text!?length > 0)>
   		<summary type="text"><![CDATA[${post.text}]]></summary>
   		</#if>
   		<link href="${posturl}${post.id}" rel="via" />
   		<guid isPermaLink="true">${posturl}${post.id}</guid>
   		<#if (post.text!?length > 0)>
   		<content><![CDATA[${post.text}]]></content>
   		</#if>
   		<#if (post.keywords!?size > 0)>
		<#list post.keywords as keyword>
		<category label="<![CDATA[${keyword}]]>" />
		</#list>
   		</#if>
   		<published>${post.date?string("yyyy-MM-dd'T'HH:mm:ssZ")}</published>
	</entry>
	</#list>
</feed>
</#macro>