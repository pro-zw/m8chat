/**
 * Created by weizheng on 28/11/14.
 */

angular.module('m8chatApp.filters')
    .filter('default', function () {
        return function (input, value) {
            if (input !== null && input !== undefined && (input !== '' || angular.isNumber(input))) {
                return input;
            }
            return value || '';
        };
    });