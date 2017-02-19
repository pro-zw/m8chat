/**
 * Created by weizheng on 28/11/14.
 */

angular.module('m8chatApp.services')
    .factory('contactUsService', ['$http', 'appPath', function($http, appPath) {
        return {
            send: function (model) {
                return $http.post(appPath.apiContactUs, model);
            }
        };
    }]);