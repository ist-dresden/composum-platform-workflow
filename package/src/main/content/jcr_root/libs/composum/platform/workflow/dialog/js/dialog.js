/**
 *
 *
 */
(function (window) {
    'use strict';

    window.workflow = window.workflow || {};

    (function (workflow, core) {

        workflow.DialogOption = Backbone.View.extend({

            initialize: function (options) {
                var c = workflow.const.css;
                this.$radio = this.$('.' + c.base + c._option + c._radio);
                this.$label = this.$('.' + c.base + c._option + c._label);
                this.$form = this.$('.' + c.base + c._option + c._form);
                this.$label.click(_.bind(this.selectOption, this));
                this.$radio.change(_.bind(this.optionSelected, this));
            },

            selectOption: function (event) {
                if (event) {
                    event.preventDefault();
                }
                this.$radio.prop('checked', true);
                this.optionSelected();
                return false;
            },

            optionSelected: function (event) {
                if (event) {
                    event.preventDefault();
                }
                this.dialog.$forms.removeClass('fade in').addClass('hidden');
                var chosen = this.dialog.getChosenOption();
                if (chosen) {
                    chosen.$form.addClass('fade in').removeClass('hidden')
                }
                return false;
            }
        });

        workflow.Dialog = core.components.FormDialog.extend({

            initialize: function (config) {
                var c = workflow.const.css;
                core.components.FormDialog.prototype.initialize.apply(this, [config]);
                this.$options = this.$('.' + c.dialog.base + c.dialog._options);
                this.$radios = this.$options.find('.' + c.base + c._option + c._radio);
                this.$forms = this.$options.find('.' + c.base + c._option + c._form);
                var dialog = this;
                var options = this.options = [];
                this.$options.find('.' + c.base + c._option).each(function () {
                    var option = core.getView(this, workflow.DialogOption);
                    option.dialog = dialog;
                    options.push(option);
                });
            },

            getChosenOption: function () {
                var c = workflow.const.css;
                var $chosen = this.$options.find('.' + c.base + c._option + c._radio + ':checked')
                    .closest('.' + c.base + c._option);
                return $chosen.length > 0 ? $chosen[0].view : undefined;
            },

            validateForm: function () {
                var result = core.components.FormDialog.prototype.validateForm.apply(this);
                var chosenOption = this.getChosenOption();
                if (!chosenOption) {
                    this.validationHint('error', 'Option', 'an option must be chosen');
                    result = false;
                }
                return result;
            },

            doSubmit: function (callback) {
                this.submitForm(callback);
            }
        });

    })(window.workflow, window.core);

})(window);
