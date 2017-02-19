/**
 * Created by weizheng on 28/11/14.
 */

angular.module('m8chatApp.services')
    .factory('alertService', ['appUtil', function(appUtil) {
        return {
            topic: '',
            message: '',
            type: 'alert-danger',
            linkText: '',
            linkUrl: '',

            setAlert: function(topic, message, type, linkText, linkUrl) {
                this.topic = topic;
                this.message = appUtil.parseErrorMessage(message);
                this.type = type;
                this.linkText = linkText;
                this.linkUrl = linkUrl;
            },

            clear: function() {
                this.topic = '';
                this.message = '';
                this.type = 'alert-danger';
                this.linkText = '';
                this.linkUrl = '';
            }
        };
    }]);