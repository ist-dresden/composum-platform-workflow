<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<div class="composum-platform-workflow_dialog workflow-graph dialog modal fade">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header workflow-dialog_header">
                <button type="button" class="workflow-dialog_button-close fa fa-close"
                        data-dismiss="modal" aria-label="Close"></button>
                <h4 class="modal-title workflow-dialog_dialog-title">
                    ${cpn:i18n(slingRequest,'Workflow')}
                </h4>
            </div>
            <div class="modal-body workflow-dialog_content">
                <sling:include resourceType="composum/platform/workflow/components/workflow" replaceSelectors="graph"/>
            </div>
            <div class="modal-footer workflow-dialog_footer">
                <button type="button" class="btn btn-primary"
                        data-dismiss="modal">${cpn:i18n(slingRequest,'Close')}</button>
            </div>
        </div>
    </div>
</div>
