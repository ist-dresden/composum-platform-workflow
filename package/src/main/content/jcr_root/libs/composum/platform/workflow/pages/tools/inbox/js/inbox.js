/**
 *
 *
 */
(function (window) {
    'use strict';

    window.workflow = window.workflow || {};

    (function (workflow, core) {

        workflow.PagesInboxToolbar = workflow.InboxToolbar.extend({

            initialize: function (options) {
                workflow.InboxToolbar.prototype.initialize.apply(this, [options]);
                this.$reload = this.$('.reload');
                this.$reload.click(_.bind(workflow.inboxView.reload, workflow.inboxView));
            }
        });

        workflow.PagesInboxList = workflow.InboxView.extend({

            initialize: function (options) {
                workflow.InboxView.prototype.initialize.apply(this, [options]);
                $(document).on('site:selected.Inbox', _.bind(this.onSiteChanged, this));
            },

            onSiteChanged: function (event, path) {
                if (this.path !== path) {
                    this.path = path;
                    this.reload();
                }
            },

            onTaskSelected: function () {
                this.$tasks.removeClass('selected');
                this.$selected.addClass('selected');
            },

            onTaskAction: function () {
                this.reload();
            },

            reload: function () {
                // FIXME scope selection (CMP-62)
                var tenant = this.$el.data('tenant') || '*';
                var scope = ''; // site
                scope = '?tenant.id=' + tenant; // tenant or all (*)
                core.getHtml('/bin/cpm/platform/workflow.taskList.html' + (this.path ? this.path : '') + scope,
                    _.bind(function (content) {
                        workflow.$inboxParent.html(content);
                        workflow.inboxView = core.getView('.composum-platform-workflow_inbox', workflow.PagesInboxList);
                        workflow.inboxToolbar.initHandlers();
                    }, this));
            }
        });

        workflow.inboxView = core.getView('.composum-platform-workflow_inbox', workflow.PagesInboxList);
        workflow.inboxToolbar = core.getView('.composum-platform-workflow .composum-pages-tools_actions', workflow.PagesInboxToolbar);
        workflow.$inboxParent = workflow.inboxView.$el.parent();

    })(window.workflow, window.core);

})(window);
