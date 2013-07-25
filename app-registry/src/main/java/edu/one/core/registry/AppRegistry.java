package edu.one.core.registry;

import edu.one.core.infra.Controller;
import edu.one.core.infra.request.filter.ActionFilter;
import edu.one.core.infra.request.filter.SecurityHandler;
import edu.one.core.registry.service.AppRegistryService;

public class AppRegistry extends Controller {

	@Override
	public void start() {
		super.start();

		AppRegistryService service = new AppRegistryService(vertx, container, rm, securedActions);

		try {
			service.registerMethod(config.getString("address"), "collectApps");
		} catch (NoSuchMethodException | IllegalAccessException e) {
			log.error(e.getMessage(), e);
		}

		service.get("/app-registry", "view", this);

		service.get("/applications", "listApplications");

		service.get("/application/:name", "listApplicationActions");

		service.get("/applications/actions", "listApplicationsWithActions");

		service.post("/role", "createRole");

		service.get("/roles", "listRoles");

		service.get("/roles/actions", "listRolesWithActions");

		SecurityHandler.addFilter(new ActionFilter(service.securedUriBinding(), vertx.eventBus()));

	}

}
