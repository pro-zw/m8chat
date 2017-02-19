/**
 * Created by weizheng on 15/1/13.
 */

angular.module('m8chatApp.services')
    .factory('paypalService', ['$http', 'appPath', function($http, appPath) {
        return {
            createPayment: function(billId) {
                return $http.post(appPath.apiCreatePayment(billId), {});
            }
        };
    }]);