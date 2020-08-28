<%@page session="false" pageEncoding="utf-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="cpp" uri="http://sling.composum.com/cppl/1.0" %>
<cpp:defineFrameObjects/>
<cpp:editDialog title="Element Properties">
    <cpp:widget label="Name" property="jcr:title" type="textfield" required="true"
                hint="A short name for the email template, for use in lists"/>
    <cpp:widget label="Description" property="jcr:description" type="richtext"
                hint="Optional description of purpose of the email template"/>
    <cpp:widget label="Subject" property="subject" type="textfield"
                hint="The subject of the email, possibly with placeholders like ${placeholder}."/>
    <cpp:widget label="Body" property="body" type="textfield" required="true"
                hint="Plaintext body of the email, possibly with placeholders like ${placeholder}."/>
    <cpp:widget label="HTML body" property="html" type="richtext"
                hint="Optional email body with HTML."/>
    <cpp:widget label="From" property="from" type="textfield" required="true"
                hint="The emailaddress of the sender of the email."
                pattern="^[a-zA-Z_.-]+@[a-zA-Z_.-]+$" pattern-hint="a value matching pattern: '{}'"/>
    <cpp:widget label="To" property="to" type="textfield" hint="The emailaddress(es) of the receiver of the email."
                pattern="^[a-zA-Z_.-]+@[a-zA-Z_.-]+$" pattern-hint="a value matching pattern: '{}'" multi="true"/>
    <cpp:widget label="CC" property="cc" type="textfield" hint="The emailaddress(es) of the CC of the email."
                pattern="^[a-zA-Z_.-]+@[a-zA-Z_.-]+$" pattern-hint="a value matching pattern: '{}'" multi="true"/>
    <cpp:widget label="BCC" property="bcc" type="textfield" hint="The emailaddress(es) of the BCCs of the email."
                pattern="^[a-zA-Z_.-]+@[a-zA-Z_.-]+$" pattern-hint="a value matching pattern: '{}'" multi="true"/>
    <cpp:widget label="Reply To" property="replyTo" type="textfield" hint="Optional emailaddress(es) to which the receiver should reply to."
                pattern="^[a-zA-Z_.-]+@[a-zA-Z_.-]+$" pattern-hint="a value matching pattern: '{}'" multi="true"/>
    <cpp:widget label="Bounce Address" property="bounceAddress" type="textfield" hint="Optional emailaddress to which messages about failed deliveries go to."
                pattern="^[a-zA-Z_.-]+@[a-zA-Z_.-]+$" pattern-hint="a value matching pattern: '{}'"/>
</cpp:editDialog>
