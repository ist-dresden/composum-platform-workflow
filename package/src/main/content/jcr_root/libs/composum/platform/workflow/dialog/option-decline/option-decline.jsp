<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<cpn:component id="wfDialog" type="com.composum.platform.workflow.model.WorkflowDialogModel" scope="request">
    <div class="form-grouo widget text-area-widget">
        <label class="widget-label"><span
                class="label-text">${cpn:i18n(slingRequest,'Message')}</span><cpn:text
                tagName="span" class="widget-hint"
                i18n="true" value="write a message if you want to inform the initiator (optional)" type="rich"/></label>
        <textarea name="data/answer" class="form-control"></textarea>
    </div>
</cpn:component>
