/**
 * Created by weizheng on 15/1/30.
 */

angular.module('m8chatApp.controllers')
    .controller('MyPlanController', ['$scope', '$window', '$modal', function($scope, $window, $modal) {
        var plan1Button = $('#mc-advert-plan-button1');
        var plan2Button = $('#mc-advert-plan-button2');
        var plan3Button = $('#mc-advert-plan-button3');

        var clearSelection = function() {
            $('.mc-advert-plan-column1').removeClass('mc-advert-plan-column-selected');
            $('.mc-advert-plan-column2').removeClass('mc-advert-plan-column-selected');
            $('.mc-advert-plan-column3').removeClass('mc-advert-plan-column-selected');

            plan1Button.removeClass('btn-primary');
            plan1Button.addClass('btn-default');
            plan1Button.text('Contact Us');

            plan2Button.removeClass('btn-primary');
            plan2Button.addClass('btn-default');
            plan2Button.text('Contact Us');

            plan3Button.removeClass('btn-primary');
            plan3Button.addClass('btn-default');
            plan3Button.text('Contact Us');
        };

        $scope.planName = $('.mc-my-plan-wrapper').first().attr('data-plan-name');
        var changePlan = function(planName) {
            var modalInstance = $modal.open({
                templateUrl: '/assets/javascripts/templates/advertiser-change-plan-model.html',
                controller: 'AdvertiserChangePlanController',
                resolve: {
                    planName: function () {
                        return planName;
                    }
                },
                backdrop: 'static'
            });

            modalInstance.result.then(function (result) {
                if (result == 'changed') {
                    $scope.planName = planName;
                }
            });
        };

        $scope.$watch(function() {
                return $scope.planName;
            }, function(planName) {
                clearSelection();

                if (planName == 'bronze') {
                    $('.mc-advert-plan-column1').addClass('mc-advert-plan-column-selected');
                    plan1Button.removeClass('btn-default');
                    plan1Button.addClass('btn-primary');
                    plan1Button.text('Selected');

                    plan2Button.text('Select');
                } else if (planName == 'silver') {
                    $('.mc-advert-plan-column2').addClass('mc-advert-plan-column-selected');
                    plan2Button.removeClass('btn-default');
                    plan2Button.addClass('btn-primary');
                    plan2Button.text('Selected');

                    plan1Button.text('Select');
                } else if (planName == 'gold') {
                    $('.mc-advert-plan-column3').addClass('mc-advert-plan-column-selected');
                    plan3Button.removeClass('btn-default');
                    plan3Button.addClass('btn-primary');
                    plan3Button.text('Selected');

                    plan1Button.text('Select');
                    plan2Button.text('Select');
                }
            }
        );

        $scope.advertPlanSelected = function(planIndex) {
            if (planIndex == 1 && $scope.planName != 'bronze') {
                changePlan('bronze');
            } else if (planIndex == 2 && $scope.planName != 'silver') {
                changePlan('silver');
            } else if (planIndex == 3 && $scope.planName != 'gold') {
                $window.location.href = '/#mc-contact-us-anchor';
            }
        };
    }]);
