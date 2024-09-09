package org.acme.auth;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.auth.request.RegisterRequest;
import org.acme.mapper.UserMapper;
import org.acme.user.User;
import org.acme.user.UserService;

@Path("/auth")
@Produces(MediaType.TEXT_HTML)
public class AuthResource {

    @Inject
    Template login;

    @Inject
    Template register;

    @Inject
    UserService userService;

    @Inject
    UserMapper userMapper;

    @GET
    @Path("/login")
    public TemplateInstance login() {
        return login.instance();
    }

    @GET
    @Path("/register")
    public TemplateInstance register() {
        return register.instance();
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public void register(RegisterRequest request) {
        User user = userMapper.mapToUser(request);
        userService.create(user);
    }
}
