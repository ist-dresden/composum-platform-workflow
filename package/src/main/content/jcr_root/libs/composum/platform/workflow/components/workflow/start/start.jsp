<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="wfDialog" type="com.composum.platform.workflow.model.WorkflowStartModel" scope="request">
    <div class="composum-platform-workflow_dialog dialog modal fade">
        <div class="modal-dialog">
            <div class="modal-content form-panel">
                <cpn:form class="widget-form workflow-dialog_form" method="POST"
                          action="/bin/cpm/platform/workflow.addTask.json${wfDialog.path}">
                    <div class="modal-header workflow-dialog_header">
                        <button type="button" class="workflow-dialog_button-close fa fa-close"
                                data-dismiss="modal" aria-label="Close"></button>
                        <h4 class="modal-title workflow-dialog_dialog-title">
                                ${cpn:i18n(slingRequest,'Start Workflow')}
                        </h4>
                    </div>
                    <div class="modal-body workflow-dialog_content">
                        <div class="workflow-dialog_messages messages">
                            <div class="panel panel-warning hidden">
                                <div class="panel-heading"></div>
                                <div class="panel-body hidden"></div>
                            </div>
                        </div>
                        <input name="_charset_" type="hidden" value="UTF-8"/>
                        <input name="tenant.id" type="hidden" value="${wfDialog.tenantId}"/>
                        <input name="wf.target" type="hidden" value="${wfDialog.resource.path}"/>
                        <div class="form-group">
                            <label class="widget-label"><span
                                    class="label-text">${cpn:i18n(slingRequest,'Workflow')}</span><cpn:text
                                    tagName="span" class="widget-hint"
                                    i18n="true" value="select the workflow to start" type="rich"/></label>
                            <ul class="composum-platform-workflow_list form-control">
                                <c:forEach items="${wfDialog.workflows}" var="workflow">
                                    <li class="composum-platform-workflow_list-item">
                                        <label><input type="radio" name="wf.template" value="${workflow.path}"
                                                      class="composum-platform-workflow_radio"/>
                                            <cpn:text class="title">${workflow.title}</cpn:text>
                                            <cpn:text class="hint" type="rich">${workflow.hint}</cpn:text></label>
                                    </li>
                                </c:forEach>
                            </ul>
                        </div>
                        <div class="form-group">
                            <label class="widget-label"><span
                                    class="label-text">${cpn:i18n(slingRequest,'Comment')}</span><cpn:text
                                    tagName="span" class="widget-hint"
                                    i18n="true" value="an internal comment (optional)" type="rich"/></label>
                            <textarea name="wf.comment" class="widget text-area-widget form-control"></textarea>
                        </div>
                    </div>
                    <div class="modal-footer workflow-dialog_footer">
                        <button type="button"
                                class="workflow-dialog_button-cancel workflow-dialog_button btn btn-default"
                                data-dismiss="modal">${cpn:i18n(slingRequest,'Cancel')}</button>
                        <button type="submit"
                                class="workflow-dialog_button-submit workflow-dialog_button btn btn-primary">${cpn:i18n(slingRequest,'Start')}</button>
                    </div>
                </cpn:form>
            </div>
        </div>
    </div>
</cpn:component>
