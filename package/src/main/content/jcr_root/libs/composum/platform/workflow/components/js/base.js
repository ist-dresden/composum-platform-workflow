/**
 *
 *
 */
(function () {
    'use strict';
    CPM.namespace('platform.workflow');

    (function (workflow, core) {

        workflow.const = {
            css: {
                base: 'composum-platform-workflow',
                _inbox: '_inbox-table',
                _task: '_inbox-task',
                _dialog: '_task-dialog',
                _option: '_option',
                _radio: '-radio',
                _label: '-label',
                _form: '-form',
                toolbar: {
                    base: 'workflow-inbox',
                    _toolbar: '-toolbar'
                },
                dialog: {
                    base: 'workflow-dialog',
                    _title: '_dialog-title',
                    _list: '_list',
                    _item: '_list-item',
                    _start: '_start-content',
                    type: 'type',
                    title: 'title',
                    hint: 'short-desc',
                    _options: '_task-options'
                }
            },
            url: {
                base: '/bin/cpm/platform/workflow',
                _dialog: '.dialog.html',
                _start: '.startDialog.html',
                _cancel: '.cancelTask.json'
            }
        };

    })(CPM.platform.workflow, CPM.core);

})();
