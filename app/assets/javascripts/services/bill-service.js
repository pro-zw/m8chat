/**
 * Created by weizheng on 15/1/15.
 */

angular.module('m8chatApp.services')
    .factory('billService', ['$http', 'appPath', function($http, appPath) {
        return {
            generate: function() {
                return $http.put(appPath.apiGenerateBill, {});
            }
        };
    }]);