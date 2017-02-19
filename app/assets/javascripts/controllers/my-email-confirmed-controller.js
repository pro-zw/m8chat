/**
 * Created by weizheng on 15/1/22.
 */

angular.module('m8chatApp.controllers')
    .controller('MyEmailConfirmedController', ['$scope', '$window', '$timeout', function($scope, $window, $timeout) {
        $timeout(function(){
            $window.location.href = '/advert/advertisers/me/advert';
        }, 1800);
    }]);