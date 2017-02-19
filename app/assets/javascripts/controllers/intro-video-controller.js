/**
 * Created by weizheng on 22/11/14.
 */

angular.module('m8chatApp.controllers')
    .controller('IntroVideoController', ['$scope', '$window', function($scope, $window) {
        $scope.$watch(function() {
            return $window.innerWidth;
        }, function() {
            var videoFrame = $('#mc-intro-video-frame');
            var wrapperSize = videoFrame.parent().width();
            var radio = 9/16;

            if (wrapperSize > 545.0) {
                videoFrame.width(545.0);
                videoFrame.height(545.0 * radio);
            } else {
                videoFrame.width(wrapperSize);
                videoFrame.height(wrapperSize * radio);
            }
        });
    }]);