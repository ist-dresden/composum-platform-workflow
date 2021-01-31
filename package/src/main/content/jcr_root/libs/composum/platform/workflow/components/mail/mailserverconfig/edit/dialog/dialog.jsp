<%@page session="false" pageEncoding="utf-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="cpp" uri="http://sling.composum.com/cppl/1.0" %>
<cpp:defineFrameObjects/>
<cpp:editDialog title="Element Properties">
    <cpp:widget label="Name" property="jcr:title" type="textfield" required="true" hint="A short name for the mail server configuration, for use in lists"/>
    <cpp:widget label="Description" property="jcr:description" type="richtext" hint="Optional description of purpose of the configuration"/>
    <cpp:widget label="Credential ID" property="credentialId" type="textfield" hint="optional ID for the credential service to retrieve the username and password for the mail server"/>
    <cpp:widget label="Connection Type" property="connectionType" type="select" required="true" options="SMTP,SMTPS,STARTTLS"/>
    <cpp:widget label="Host" property="host" type="textfield" required="true" hint="Hostname of the server to send mails from"/>
    <cpp:widget label="Port" property="port" type="textfield" required="true" hint="Port of the server to send mails from"/>
</cpp:editDialog>
