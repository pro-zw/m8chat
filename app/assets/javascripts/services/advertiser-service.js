/**
 * Created by weizheng on 26/12/14.
 */

angular.module('m8chatApp.services')
    .factory('advertiserService', ['$http', 'appPath', function($http, appPath) {
        return {
            getPlanName: function(model) {
                var planName = "Unknown";
                switch (model.planSelected) {
                    case 1:
                        planName = "bronze";
                        break;
                    case 2:
                        planName = "silver";
                        break;
                    default:
                        break;
                }

                return planName;
            },
            register: function(model) {
                return $http.put(appPath.apiRegister, {
                    name: model.name,
                    companyName: model.companyName,
                    email: model.email,
                    plainPassword: model.password,
                    planName: this.getPlanName(model)
                });
            },
            login: function(model) {
                return $http.post(appPath.apiLogin, {
                   email: model.email,
                   plainPassword: model.password
                });
            },
            updateBusiness: function(model) {
                return $http.post(appPath.apiUpdateBusiness, model);
            },
            updatePhoto: function(data, photoIndex) {
                return $http.post(appPath.apiUpdatePhoto(photoIndex), data, {
                    headers: {'Content-Type': undefined },
                    transformRequest: angular.identity
                });
            },
            deletePhoto: function(photoIndex) {
                return $http.delete(appPath.apiDeletePhoto(photoIndex));
            },
            updateAccount: function(model) {
                if (model.password) {
                    return $http.post(appPath.apiUpdateAccount, {
                        name: model.name,
                        companyName: model.companyName,
                        email: model.email,
                        plainPassword: model.password
                    });
                } else {
                    return $http.post(appPath.apiUpdateAccount, {
                        name: model.name,
                        companyName: model.companyName,
                        email: model.email
                    });
                }
            },
            forgotPassword: function(email) {
                return $http.post(appPath.apiForgotPassword, {
                    email: email
                });
            },
            changePlan: function(planName) {
                return $http.post(appPath.apiChangePlan, {
                    planName: planName
                });
            },
            getCreditCard: function() {
                return $http.get(appPath.apiGetCreditCard);
            },
            updatePaymentMethod: function(paymentMethod, cardToken) {
                return $http.post(appPath.apiChangePaymentMethod, cardToken ? {
                    paymentMethod: paymentMethod,
                    cardToken: cardToken
                } : {
                    paymentMethod: paymentMethod
                });
            },
            removeStripeCustomerId: function() {
                return $http.delete(appPath.apiRemoveStripeCustomerId);
            }
        };
    }]);