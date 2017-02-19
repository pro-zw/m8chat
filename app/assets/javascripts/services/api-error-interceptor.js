/**
 * Created by weizheng on 28/11/14.
 */

angular.module('m8chatApp.services')
    .factory('apiErrorInterceptor', ['$q', '$cookieStore', function($q, $cookieStore) {
        return {
            'responseError': function(response) {
                if (response.status === 401) {
                    $cookieStore.remove("X-m8chat-Advertiser-Access-Token");
                }
                return $q.reject(response);
            }
        };
    }]);