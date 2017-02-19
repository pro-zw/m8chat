/**
 * Created by weizheng on 28/11/14.
 */

angular.module('m8chatApp.services')
    .factory('appPath', ['$location', function($location) {
        return {
            // Remote api path
            apiContactUs: '/support/api/contact-us/send',
            apiRegister: '/advert/api/advertisers/register',
            apiLogin: '/advert/api/advertisers/login',
            apiCheckEmail: '/advert/api/advertisers/check-email',
            apiUpdateBusiness: '/advert/api/advertisers/me/update-business',
            apiUpdatePhoto: function(photoIndex) {
                return '/advert/api/advertisers/me/photos/' + photoIndex + '/update';
            },
            apiDeletePhoto: function(photoIndex) {
                return '/advert/api/advertisers/me/photos/' + photoIndex;
            },
            apiUpdateAccount: '/advert/api/advertisers/me/update-account',
            apiForgotPassword: '/advert/api/advertisers/forgot-password',
            apiCreatePayment: function(billId) {
                return '/advert/api/advertisers/me/bills/' + billId + '/create-payment';
            },
            apiGenerateBill: '/advert/api/advertisers/me/bills/generate',
            apiChangePlan: '/advert/api/advertisers/me/change-plan',
            apiGetCreditCard: '/advert/api/advertisers/me/credit-card',
            apiChangePaymentMethod: '/advert/api/advertisers/me/change-payment-method',
            apiRemoveStripeCustomerId: '/advert/api/advertisers/me/stripe-customer-id'
        };
    }]);
