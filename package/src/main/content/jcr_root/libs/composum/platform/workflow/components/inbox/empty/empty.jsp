<%@page session="false" pageEncoding="utf-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<cpn:component id="model" type="com.composum.platform.tenant.view.TenantBean">
    <div style="padding: 20px 10px; border-bottom: 1px solid #ccc; color: #08c;">
            ${cpn:i18n(slingRequest,'the inbox is empty')}
    </div>
    <div style="padding: 10px;">
        <h4>Platform Workflow Tasks - ${cpn:i18n(slingRequest,'Inbox')}</h4>
        <p>${cpn:i18n(slingRequest,'the tenants workflow tasks view')}</p>
        <div style="width: 100%; text-align: center;">
            <cpn:image src="/libs/composum/platform/workflow/components/inbox/empty/screen.png"
                       style="display: inline-block; max-width: 90%; margin: 1em;"/>
        </div>
        <p>${cpn:i18n(slingRequest,'There are pending manually task listed for processing (the \'>\' action on the right in the toolbar).')}</p>
        <p>${cpn:i18n(slingRequest,'You can also start a new workflow using the \'+\' button on the right in the toolbar.')}</p>
    </div>
</cpn:component>
