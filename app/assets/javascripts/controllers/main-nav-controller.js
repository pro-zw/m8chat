/**
 * Created by weizheng on 26/11/14.
 */

angular.module('m8chatApp.controllers')
    .controller('MainNavController', ['$scope', '$window', '$timeout', 'advertiserService', 'alertService', 'appUtil', function($scope, $window, $timeout, advertiserService, alertService, appUtil) {
        // $scope.navSelected = window.location.hash.substring(1) ? window.location.hash.substring(1) : 'mc-jumbotron-anchor';
        $scope.navSelected = appUtil.getQueryParam("anchor") ? appUtil.getQueryParam("anchor")  : 'mc-jumbotron-anchor';
        var scrollToAnchor = function(anchor, event) {
            if (anchor) {
                var target = $('#' + anchor);
                if (target.length) {
                    if (event) {
                        event.preventDefault();
                    }
                    $('html,body').animate({scrollTop: target.offset().top}, "slow");
                }
            }
        };
        scrollToAnchor($scope.navSelected);
        $scope.navClicked = function(anchor, event) {
            $scope.navSelected = anchor;
            scrollToAnchor(anchor, event);
        };

        // Handle login panel
        var loginPopover = $("#mc-login-popover");
        var loginPopoverTab = $("#mc-login-tab");
        var loginPopoverLink = $("#mc-login-popup-link");
        var mainNav = $('#mc-mainnav');

        $scope.loginSelected = function(event) {
            if (event) {
                event.preventDefault();
            }

            if (loginPopover.css('display') === 'none') {
                loginPopover.css('display', 'block');
                loginPopover.find('input').first().focus();

                if ($(window).width() <= 768) {
                    $('html,body').animate({scrollTop: loginPopoverLink.offset().top}, "slow");
                }
            } else {
                loginPopover.css('display', 'none');
            }
        };

        $scope.login = function() {
            $scope.isLogining = true;

            advertiserService.login($scope.loginModel)
                .success(function(data) {
                    $scope.isLogining = false;

                    if (data.status == 'registered') {
                        alertService.setAlert('login-alert', 'Please check your emails and confirm your email address first', 'alert-warning');
                    } else if (data.status == 'suspended_unpaid') {
                        alertService.setAlert('login-alert', 'Your account is suspended due to unpaid bill. Please contact us', 'alert-warning');
                    } else if (data.status == 'suspended') {
                        alertService.setAlert('login-alert', 'Your account is suspended. Please contact us', 'alert-warning');
                    } else {
                        alertService.setAlert('login-alert', 'Loading account...', 'alert-success');
                        $timeout(function(){
                            $window.location.href = '/advert/advertisers/me/advert';
                        }, 1200);
                    }
                })
                .error(function(data, status) {
                    $scope.isLogining = false;

                    if (status === 401) {
                        alertService.setAlert('login-alert', 'Login failed. Please check your email and password.', 'alert-warning');
                    } else {
                        alertService.setAlert('login-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                    }
                });
        };

        var emailInput = $('input[name=email]');
        $scope.forgotPassword = function(event) {
            if (event) {
                event.preventDefault();
            }

            if ($scope.loginForm.email.$invalid) {
                emailInput.addClass('highlight');
                setTimeout(
                    function() { emailInput.removeClass('highlight'); },
                    2000
                );
            } else {
                emailInput.removeClass('highlight');

                $scope.isLogining = true;
                advertiserService.forgotPassword($scope.loginModel.email)
                    .success(function() {
                        $scope.isLogining = false;
                        alertService.setAlert('login-alert', 'Please check your mailbox for password reset email', 'alert-success');
                    })
                    .error(function(data, status) {
                        $scope.isLogining = false;
                        alertService.setAlert('login-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                    });
            }
        };

        $scope.navbarToggled = function() {
            loginPopover.css('display', 'none');
        };

        var adjustLoginPopover = function() {
            if ($(window).width() >= 768) {
                loginPopoverTab.css('display', 'block');
                loginPopover.css('border-top-right-radius', '0')
                    .css('left', 'auto')
                    .css('right', ($(window).width() - mainNav.width())/2 - 15 + 'px')
                    .css('width', '400px');

                if ($(window).scrollTop() > 60) {
                    loginPopover.css('margin-top', '-8px');
                } else {
                    loginPopover.css('margin-top', '-41px');
                }
            } else {
                loginPopoverTab.css('display', 'none');
                loginPopover.css('margin-top', '2px')
                    .css('border-top-right-radius', '8px')
                    .css('left', '0')
                    .css('right', 'auto')
                    .css('width', '100%');
            }
        };
        adjustLoginPopover();

        // Handle main navigation bar
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
                adjustLoginPopover();

                if ($(window).width() >= 768) {
                    loginPopover.css('display', 'none');
                }
            });
        });
        $(window).on('resize', function() {
            $scope.$apply(function() {
                adjustNavBar();
                adjustLoginPopover();
            });
        });
    }]);