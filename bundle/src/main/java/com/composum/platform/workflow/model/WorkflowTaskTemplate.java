package com.composum.platform.workflow.model;

import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.composum.platform.workflow.action.GenericWorkflowAction.TOPIC_GENERIC;

public class WorkflowTaskTemplate extends WorkflowTask {

    public static final String TEMPLATE_TYPE = "composum/platform/workflow/task/template";

    public static final String DEFAULT_DIALOG = "composum/platform/workflow/dialog";

    private transient LinkedHashMap<String, Option> options;
    protected boolean isLoop = false; // controlled by the referencing option

    @Override
    public void initialize(BeanContext context, Resource resource) {
        super.initialize(context, resource);
        getOptionsMap(); // preload to use the open context
    }

    public boolean isWorkflowLoop() {
        return isLoop;
    }

    @Nonnull
    public String getResourceType() {
        return TEMPLATE_TYPE;
    }

    @Nonnull
    public String getTopic() {
        return getProperty(PN_TOPIC, TOPIC_GENERIC);
    }

    @Nonnull
    public String[] getCategory() {
        return getProperty(PN_CATEGORY, new String[0]);
    }

    @Nonnull
    public String getTitle() {
        return i18n().get(PN_TITLE, getName());
    }

    @Nonnull
    public String getHint() {
        return i18n().get(PN_HINT, "");
    }

    public String getHintAdded(String alternativeText) {
        return i18n().get(PN_HINT_ADDED, alternativeText);
    }

    public boolean isAutoRun() {
        return getProperty(PN_AUTO_RUN, Boolean.FALSE);
    }

    @Nonnull
    public String getDialog() {
        return getProperty(PN_DIALOG, DEFAULT_DIALOG);
    }

    @Nullable
    public Option getOption(@Nullable final String key) {
        Map<String, Option> options = getOptionsMap();
        if (StringUtils.isNotBlank(key)) {
            return options.get(key);
        } else {
            for (Option option : options.values()) {
                if (option.isDefault()) {
                    return option;
                }
            }
        }
        return null;
    }

    @Override
    @Nonnull
    public Collection<Option> getOptions() {
        return getOptionsMap().values();
    }

    @Nonnull
    public LinkedHashMap<String, Option> getOptionsMap() {
        if (options == null) {
            options = new LinkedHashMap<>();
            Resource optionsRes = resource.getChild(PP_OPTIONS);
            if (optionsRes != null) {
                for (Resource option : optionsRes.getChildren()) {
                    options.put(option.getName(), new Option(option));
                }
            }
        }
        return options;
    }

    @Override
    public String toString() {
        return super.toString() + "{" + getPath() + ",loop:" + isLoop + "}";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorkflowTaskTemplate && getPath().equals(((WorkflowTaskTemplate) other).getPath());
    }

    @Override
    public int hashCode() {
        return getPath().hashCode();
    }
}
