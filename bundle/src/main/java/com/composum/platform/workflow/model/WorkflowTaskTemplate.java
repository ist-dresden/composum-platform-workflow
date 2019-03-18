package com.composum.platform.workflow.model;

import com.composum.platform.models.simple.LoadedModel;
import com.composum.platform.workflow.service.WorkflowService;
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

    public static final String DEFAULT_DIALOG = "composum/platform/workflow/dialog";

    public static final String PN_TITLE = "title";
    public static final String PN_HINT = "hint";
    public static final String PN_HINT_ADDED = "hintAdded";
    public static final String PN_HINT_SELECTED = "hintSelected";
    public static final String PN_DIALOG = "dialog";
    public static final String PN_AUTO_RUN = "autoRun";

    public static final String PN_DEFAULT = "default";

    public static final String PP_OPTIONS = "options";
    public static final String PN_FORM_TYPE = "formType";
    public static final String PN_TEMPLATE = "template";

    public class Option extends LoadedModel {

        protected final String key;
        protected final String topic;
        protected final String title;
        protected final String hint;
        protected final String hintSelected;
        protected final String formType;
        protected final WorkflowTaskTemplate template;
        protected final boolean isDefault;

        public Option(@Nonnull final Resource resource) {
            initialize(WorkflowTaskTemplate.this.context, resource);
            key = resource.getName();
            String templatePath = getProperty(PN_TEMPLATE, "");
            template = StringUtils.isNotBlank(templatePath)
                    ? service.getTemplate(context, templatePath) : null;
            topic = getProperty(PN_TOPIC, "");
            formType = getProperty(PN_FORM_TYPE, "");
            title = i18n().get(PN_TITLE, template != null ? template.getTitle() : "");
            hint = i18n().get(PN_HINT, template != null ? template.getHint() : "");
            hintSelected = i18n().get(PN_HINT_SELECTED, "");
            isDefault = getProperty(PN_DEFAULT, Boolean.FALSE);
        }

        public boolean isDefault() {
            return isDefault;
        }

        public String getTitle() {
            return title;
        }

        public String getHint() {
            return hint;
        }

        public String getHintSelected(String alternativeText) {
            return StringUtils.isNotBlank(hintSelected) ? hintSelected : alternativeText;
        }

        public boolean isOptionForm() {
            return StringUtils.isNotBlank(getFormType());
        }

        public String getTopic() {
            return topic;
        }

        public String getFormType() {
            return formType;
        }

        public WorkflowTaskTemplate getTemplate() {
            return template;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Option && key.equals(((Option) other).key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    private transient LinkedHashMap<String, Option> options;

    public WorkflowTaskTemplate(WorkflowService service) {
        super(service);
    }

    @Override
    public void initialize(BeanContext context, Resource resource) {
        super.initialize(context, resource);
        getOptionsMap(); // preload to use the open context
    }

    public String getTitle() {
        return i18n().get(PN_TITLE, getName());
    }

    public String getHint() {
        return i18n().get(PN_HINT, "");
    }

    public String getHintAdded(String alternativeText) {
        return i18n().get(PN_HINT_ADDED, alternativeText);
    }

    @Nonnull
    public String[] getCategory() {
        return getProperty(PN_CATEGORY, new String[0]);
    }

    public boolean isAutoRun() {
        return getProperty(PN_AUTO_RUN, Boolean.FALSE);
    }

    @Nonnull
    public String getTopic() {
        return getProperty(PN_TOPIC, TOPIC_GENERIC);
    }

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

    public Collection<Option> getOptions() {
        return getOptionsMap().values();
    }

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
        return getPath();
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
