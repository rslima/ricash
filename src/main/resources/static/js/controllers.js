/**
 * Created by rslima on 15/02/16.
 */

"use strict";

var studentControllerModule = angular.module("ricashApp.controllers",[]);

studentControllerModule.controller("RicashController", function($rootScope, $scope, $location, $routeParams) {
    $scope.appName = "Ricash";
    $scope.authorName = "Ricardo Lima";
});
