/**
 * Created by weizheng on 28/11/14.
 */

angular.module('m8chatApp.services')
    .factory('appEvent', function appEvent() {
        return {
            loginRequired: 'loginRequired'
        };
    });