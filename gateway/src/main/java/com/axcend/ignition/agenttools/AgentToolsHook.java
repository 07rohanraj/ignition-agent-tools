package com.axcend.ignition.agenttools;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.dataroutes.AccessControlStrategy;
import com.inductiveautomation.ignition.gateway.dataroutes.HttpMethod;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.util.Optional;

public class AgentToolsHook extends AbstractGatewayModuleHook {

    private AgentToolsRouteHandlers routeHandlers;

    @Override
    public void setup(GatewayContext context) {
        routeHandlers = new AgentToolsRouteHandlers(context);
    }

    @Override
    public void startup(LicenseState activationState) {
        // All initialization is done during setup.
    }

    @Override
    public void shutdown() {
        if (routeHandlers != null) {
            routeHandlers.shutdownServices();
        }
        routeHandlers = null;
    }

    @Override
    public void mountRouteHandlers(RouteGroup routes) {
        if (routeHandlers == null) {
            throw new IllegalStateException("Agent Tools route handlers were not initialized before mounting routes.");
        }

        routes.newRoute("/health")
                .type(RouteGroup.TYPE_JSON)
                .handler(routeHandlers::health)
                .method(HttpMethod.GET)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        routes.newRoute("/view/validate")
                .type(RouteGroup.TYPE_JSON)
                .handler(routeHandlers::validateView)
                .method(HttpMethod.POST)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        routes.newRoute("/script/exec")
                .type(RouteGroup.TYPE_JSON)
                .handler(routeHandlers::execScript)
                .method(HttpMethod.POST)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        routes.newRoute("/gateway/info")
                .type(RouteGroup.TYPE_JSON)
                .handler(routeHandlers::gatewayInfo)
                .method(HttpMethod.GET)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        routes.newRoute("/tags/providers")
                .type(RouteGroup.TYPE_JSON)
                .handler(routeHandlers::tagProviders)
                .method(HttpMethod.GET)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        routes.newRoute("/tags/browse")
                .type(RouteGroup.TYPE_JSON)
                .handler(routeHandlers::browseTags)
                .method(HttpMethod.POST)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        routes.newRoute("/tags/read")
                .type(RouteGroup.TYPE_JSON)
                .handler(routeHandlers::readTags)
                .method(HttpMethod.POST)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        routes.newRoute("/query/run")
                .type(RouteGroup.TYPE_JSON)
                .handler(routeHandlers::runQuery)
                .method(HttpMethod.POST)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        routes.newRoute("/projects")
                .type(RouteGroup.TYPE_JSON)
                .handler(routeHandlers::listProjects)
                .method(HttpMethod.GET)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();

        routes.newRoute("/projects/resources")
                .type(RouteGroup.TYPE_JSON)
                .handler(routeHandlers::projectResources)
                .method(HttpMethod.POST)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .mount();
    }

    @Override
    public Optional<String> getMountPathAlias() {
        return Optional.of("agent-tools");
    }
}
