<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="wfDialog" type="com.composum.platform.workflow.model.WorkflowDialogModel" scope="request">
    <div class="modal-content form-panel">
        <cpn:form class="widget-form workflow-dialog_form" method="POST"
                  action="/bin/cpm/platform/workflow.cancelTask.json${wfDialog.path}">
            <div class="modal-header workflow-dialog_header">
                <button type="button" class="workflow-dialog_button-close fa fa-close"
                        data-dismiss="modal" aria-label="Close"></button>
                <h4 class="modal-title workflow-dialog_dialog-title">${cpn:i18n(slingRequest,'Cancel')}
                    - ${cpn:text(wfDialog.title)}</h4>
            </div>
            <div class="modal-body workflow-dialog_content">
                <div class="workflow-dialog_messages messages">
                    <div class="panel panel-warning hidden">
                        <div class="panel-heading"></div>
                        <div class="panel-body hidden"></div>
                    </div>
                </div>
                <input name="_charset_" type="hidden" value="UTF-8"/>
                <sling:call script="graph.jsp"/>
                <div class="form-group">
                    <label class="widget-label"><span
                            class="label-text">${cpn:i18n(slingRequest,'Comment')}</span><cpn:text
                            tagName="span" class="widget-hint"
                            i18n="true" value="an internal comment (optional)" type="rich"/></label>
                    <textarea name="wf.comment" class="widget text-area-widget form-control"></textarea>
                </div>
            </div>
            <div class="modal-footer workflow-dialog_footer">
                <button type="button" class="workflow-dialog_button-cancel btn btn-default"
                        data-dismiss="modal">${cpn:i18n(slingRequest,'Close')}</button>
                <button type="submit"
                        class="workflow-dialog_button-submit btn btn-danger">${cpn:i18n(slingRequest,'Cancel Workflow')}</button>
            </div>
        </cpn:form>
    </div>
</cpn:component>
