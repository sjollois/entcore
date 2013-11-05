function Timeline($scope, date, http, navigation){
	$scope.notifications = [];
	http.get('lastNotifications').done(function(response){
		$scope.notifications = response.results;
		$scope.$apply();
	});

	$scope.formatDate = function(dateString){
		return date.calendar(dateString);
	};

	$scope.navigate = navigation.navigate;
}

function Personalization($scope, http, ui){
	http.get('/timeline/public/json/themes.json').done(function(data){
		$scope.skins = data;
		$scope.$apply();
	})

	$scope.saveTheme = function(skin){
		ui.setStyle(skin.skinPath);
	};

	$scope.togglePanel = function(){
		$scope.showPanel = !$scope.showPanel;
	};

	$scope.create = {
		comment: {
			comment: ''
		}
	}
}

function Widgets(){

}