<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<cpn:component id="wfDialog" type="com.composum.platform.workflow.model.WorkflowDialogModel" scope="request">
    <div class="composum-platform-workflow_dialog dialog modal fade" data-init="${wfDialog.initDialog}">
        <div class="modal-dialog">
            <div class="modal-content form-panel">
                <cpn:form class="widget-form workflow-dialog_form" method="${wfDialog.action.method}"
                          action="${wfDialog.action.url}" enctype="${wfDialog.action.encType}">
                    <div class="modal-header workflow-dialog_header">
                        <button type="button" class="workflow-dialog_button-close fa fa-close"
                                data-dismiss="modal" aria-label="Close"></button>
                        <h4 class="modal-title workflow-dialog_dialog-title">
                                ${cpn:text(wfDialog.title)}
                        </h4>
                    </div>
                    <div class="modal-body workflow-dialog_content">
                        <div class="workflow-dialog_messages messages">
                            <div class="panel panel-${wfDialog.alertKey}">
                                <div class="panel-heading">${wfDialog.alertText}</div>
                                <div class="panel-body hidden"></div>
                            </div>
                        </div>
                        <input name="_charset_" type="hidden" value="UTF-8"/>
                        <input name="path" type="hidden" value=""/>
                        <sling:call script="content.jsp"/>
                    </div>
                    <div class="modal-body workflow-dialog_task-options">
                        <sling:call script="task-options.jsp"/>
                        <sling:call script="comment.jsp"/>
                    </div>
                    <div class="modal-footer workflow-dialog_footer">
                        <button type="button"
                                class="workflow-dialog_button-cancel workflow-dialog_button btn btn-default"
                                data-dismiss="modal">${cpn:i18n(slingRequest,'Cancel')}</button>
                        <sling:call script="process-button.jsp"/>
                    </div>
                </cpn:form>
            </div>
        </div>
    </div>
</cpn:component>
