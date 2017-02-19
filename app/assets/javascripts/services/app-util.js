/**
 * Created by weizheng on 16/12/14.
 */

angular.module('m8chatAppCommon.services')
    .factory('appUtil', ['$window', function($window) {
        return {
            getQueryParam: function(name) {
                var query = $window.location.search.substring(1);
                var vars = query.split('&');
                for (var i = 0; i < vars.length; i++) {
                    var pair = vars[i].split('=');
                    if (decodeURIComponent(pair[0]) == name) {
                        return decodeURIComponent(pair[1]);
                    }
                }
            },
            parseErrorMessage: function(message, status) {
                if (status === 0) {
                    return 'The server or your device appears offline. Please disable or pause your ad blocking plugin to use m8chat';
                } else if (status === 401) {
                    return 'Session expires. Please log out and login again';
                } else {
                    if (typeof message === 'string') {
                        return message;
                    } else if (message && typeof message === 'object') { // In JavaScript, typeof null is object
                        if (message.hasOwnProperty('errors') && message.errors) {
                            // return (message.errors[0].path + ': ' + message.errors[0].messages[0]);
                            return (message.errors[0].messages[0]);
                        }
                    } else {
                        return 'Unknown error occurs';
                    }
                }
            }
        };
    }]);