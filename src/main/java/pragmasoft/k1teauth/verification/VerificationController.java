package pragmasoft.k1teauth.verification;

import com.nimbusds.jwt.proc.BadJWTException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import pragmasoft.k1teauth.security.jwt.JwtService;
import pragmasoft.k1teauth.user.UserService;

import java.net.URI;
import java.util.UUID;

@Controller("/verify")
@Secured(SecurityRule.IS_ANONYMOUS)
public class VerificationController {

    private final UserService userService;
    private final JwtService jwtService;

    public VerificationController(UserService userService,
                                  JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @Get(uri = "/registration")
    public HttpResponse<?> verifyRegistration(@QueryValue String token) throws BadJWTException {
        String subj = jwtService.extractClaimsSet(token).getSubject();
        userService.verifyUser(UUID.fromString(subj));

        return HttpResponse.seeOther(URI.create("/auth/login"));
    }

    @Get(uri = "/reset-password")
    public HttpResponse<?> verifyResetPassword(@QueryValue String token) throws BadJWTException {
        String subj = jwtService.extractClaimsSet(token).getSubject();
        userService.verifyUser(UUID.fromString(subj));

        return HttpResponse.seeOther(URI.create("/auth/reset-password/" + UUID.fromString(subj)));
    }
}
