/**
 *
 *
 */
(function (window) {
    'use strict';

    window.workflow = window.workflow || {};

    (function (workflow, core) {

        workflow.InboxTableTask = Backbone.View.extend({

            initialize: function (options) {
                this.$('.workflow').click(_.bind(this.toggleWorkflow, this));
                this.$el.click(_.bind(this.openDialog, this));
            },

            openDialog: function (event) {
                if (event) {
                    event.preventDefault();
                }
                workflow.inboxTable.openDialog(this);
                return false;
            },

            toggleWorkflow: function (event) {
                if (event) {
                    event.preventDefault();
                }
                if (this.$workflow) {
                    this.$workflow.popover('toggle');
                } else {
                    core.getHtml(this.$el.data('path') + '.dialog.html', _.bind(function (content) {
                        core.showLoadedDialog(core.components.LoadedDialog, content);
                    }, this));
                }
                return false;
            }
        });

        workflow.InboxTable = Backbone.View.extend({

            initialize: function (options) {
                var c = workflow.const.css;
                this.$tasks = this.$('.' + c.base + c._task);
                var tasks = this.tasks = [];
                this.$tasks.each(function () {
                    tasks.push(core.getView(this, workflow.InboxTableTask));
                });
            },

            openDialog: function (task) {
                var u = workflow.const.url;
                var path = task.$el.data('path');
                core.getHtml(u.base + u._dialog + path,
                    _.bind(function (content) {
                        if (content) {
                            core.showFormDialog(workflow.Dialog, content);
                        }
                    }, this));
            }
        });

        workflow.onTableLoad = function () {
            workflow.inboxTable = core.getView(
                '.' + workflow.const.css.base + workflow.const.css._inbox, workflow.InboxTable);
        };

        workflow.onTableLoad();

    })(window.workflow, window.core);

})(window);
