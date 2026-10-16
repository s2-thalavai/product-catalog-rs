package com.infy.catalog.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Kubernetes probes and a root redirect.
 * <ul>
 *   <li>GET /health/live  - 200 while the process serves HTTP (liveness/startup)</li>
 *   <li>GET /health/ready - 200 when started, not shutting down and the database answers (readiness)</li>
 *   <li>GET /             - 303 to /api/v1/products (handy in a browser)</li>
 * </ul>
 */
@Path("")
@Hidden
public class HealthResource {

    private final BooleanSupplier ready;

    public HealthResource(BooleanSupplier ready) {
        this.ready = ready;
    }

    @GET
    public Response root() {
        return Response.seeOther(URI.create("/api/v1/products")).build();
    }

    @GET
    @Path("health/live")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> live() {
        return Map.of("status", "UP");
    }

    @GET
    @Path("health/ready")
    @Produces(MediaType.APPLICATION_JSON)
    public Response ready() {
        boolean up = ready.getAsBoolean();
        return Response.status(up ? 200 : 503).entity(Map.of("status", up ? "UP" : "DOWN")).build();
    }
}
