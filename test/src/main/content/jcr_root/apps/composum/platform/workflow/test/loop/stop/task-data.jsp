<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<cpn:component id="wfDialog" type="com.composum.platform.workflow.model.WorkflowDialogModel" scope="request">
    <table class="composum-platform-workflow_data-table table table-bordered table-condensed">
        <tbody>
        <cpn:div tagName="tr" test="${not empty wfDialog.task.data.userId}">
            <td class="name">${cpn:i18n(slingRequest,'User')}</td>
            <td class="value"><cpn:text value="${wfDialog.task.data.userId}"/></td>
        </cpn:div>
        <cpn:div tagName="tr" test="${not empty wfDialog.task.data.prop01}">
            <td class="name">${cpn:i18n(slingRequest,'Prop #1')}</td>
            <td class="value"><cpn:text value="${wfDialog.task.data.prop01}"/></td>
        </cpn:div>
        <cpn:div tagName="tr" test="${not empty wfDialog.task.data.approveProp}">
            <td class="name">${cpn:i18n(slingRequest,'Approve')}</td>
            <td class="value"><cpn:text value="${wfDialog.task.data.approveProp}"/></td>
        </cpn:div>
        </tbody>
    </table>
</cpn:component>
