<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="wfDialog" type="com.composum.platform.workflow.model.WorkflowDialogModel" scope="request">
    <div class="composum-platform-workflow_option-set">
        <c:forEach items="${wfDialog.task.template.options}" var="option">
            <div class="composum-platform-workflow_option form-check">
                <input class="composum-platform-workflow_option-radio form-check-input" type="radio" name="wf.option"
                       value="${option.name}"/>
                <label class="composum-platform-workflow_option-label form-check-label">${cpn:text(option.title)}
                    <cpn:text tagName="span" class="composum-platform-workflow_option-hint"
                              value="${option.hint}"/></label>
                <c:if test="${option.optionForm}">
                    <div class="composum-platform-workflow_option-form hidden">
                        <sling:include resourceType="${option.formType}"/>
                    </div>
                </c:if>
            </div>
        </c:forEach>
    </div>
</cpn:component>
