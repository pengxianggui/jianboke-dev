'use strict';

angular.module('jianboke')
	.factory('Book', function($resource) {
		return $resource('api/book', {}, {});
	})