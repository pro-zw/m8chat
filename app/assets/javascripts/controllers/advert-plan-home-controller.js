angular.module('m8chatApp.controllers')
    .controller('AdvertPlanHomeController', ['$scope', '$window', function($scope, $window){
        $scope.advertPlanSelected = function(planIndex) {
            switch(planIndex) {
                case 1:
                    $window.location.href = '/register?plan-pre-selected=1';
                    break;
                case 2:
                    $window.location.href = '/register?plan-pre-selected=2';
                    break;
                case 3:
                    $('html,body').animate({scrollTop: $('#mc-contact-us-anchor').offset().top}, "slow");
                    break;
                default:
                    break;
            }
        };
    }]);