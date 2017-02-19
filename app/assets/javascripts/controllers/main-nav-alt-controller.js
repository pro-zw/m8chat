/**
 * Created by weizheng on 14/12/29.
 */

angular.module('m8chatApp.controllers')
    .controller('MainNavAltController', ['$scope', function($scope) {
        var navbar = $('.navbar').first();
        var adjustNavBar = function() {
            if ($(window).width() >= 992) {
                if ($(window).scrollTop() > 60) {
                    navbar.addClass('mc-small-navbar');
                } else {
                    navbar.removeClass('mc-small-navbar');
                }
            } else {
                navbar.removeClass('mc-small-navbar');
            }
        };
        adjustNavBar();
        $(window).bind('scroll', function() {
            $scope.$apply(function() {
                adjustNavBar();
            });
        });
        $(window).on('resize', function() {
            $scope.$apply(function() {
                adjustNavBar();
            });
        });
    }]);
