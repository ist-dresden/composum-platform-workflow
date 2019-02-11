package com.composum.platform.workflow.model;

public class WorkflowTaskTemplate extends WorkflowTask {

    public static final String PN_TITLE = "title";
    public static final String PN_HINT = "hint";
    public static final String PN_DIALOG = "dialog";

    public static final String PP_OPTIONS = "options";
    public static final String PN_TEMPLATE = "template";

    private transient String title;
    private transient String hint;

    public String getTitle(){
        if (title == null) {
            title = getProperty(PN_TITLE, getName());
        }
        return title;
    }

    public String getHint(){
        if (hint == null) {
            hint = getProperty(PN_HINT, "");
        }
        return hint;
    }
}
