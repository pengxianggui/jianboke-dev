'use strict';

angular.module('jianboke')
	.factory('Comment', function($resource) {
		return $resource('api/comment/:id', {}, {
            like: {
                url: 'api/comment/like',
                method: 'POST',
                params: {
                    commentId: "@commentId"
                }
            }
		});
	});