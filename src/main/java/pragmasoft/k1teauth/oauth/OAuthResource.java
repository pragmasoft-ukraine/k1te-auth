package pragmasoft.k1teauth.oauth;

import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;
import pragmasoft.k1teauth.jwt.JwtClaim;
import pragmasoft.k1teauth.jwt.JwtService;
import pragmasoft.k1teauth.oauth.client.OAuthClient;
import pragmasoft.k1teauth.oauth.code.AuthCode;
import pragmasoft.k1teauth.oauth.code.CodeGenerator;
import pragmasoft.k1teauth.oauth.consent.Consent;
import pragmasoft.k1teauth.oauth.dto.AuthRequest;
import pragmasoft.k1teauth.oauth.dto.ErrorResponse;
import pragmasoft.k1teauth.oauth.dto.TokenRequest;
import pragmasoft.k1teauth.oauth.dto.TokenResponse;
import pragmasoft.k1teauth.oauth.form.ConsentForm;
import pragmasoft.k1teauth.oauth.scope.Scope;
import pragmasoft.k1teauth.user.User;
import pragmasoft.k1teauth.user.UserService;
import pragmasoft.k1teauth.util.CsrfTokenValidator;
import pragmasoft.k1teauth.util.HashUtil;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/oauth2")
@Authenticated
@Transactional
public class OAuthResource extends Controller {

    @CheckedTemplate(requireTypeSafeExpressions = false)
    static class Templates {

        private Templates() {
            throw new IllegalStateException("Utility class");
        }

        public static native TemplateInstance consent(String clientId, String clientName, String callbackUrl,
                                                      String state, UUID userId, Set<Scope> scopes,
                                                      String codeChallenge, String codeChallengeMethod);
    }

    private static final Duration AUTH_CODE_EXP_TIME = Duration.ofMinutes(10);
    private static final Duration ACCESS_TOKEN_EXP_TIME = Duration.ofHours(1);
    private static final Duration REFRESH_TOKEN_EXP_TIME = Duration.ofDays(14);

    private final CodeGenerator codeGenerator;
    private final UserService userService;
    private final JwtService jwtService;
    private final CurrentIdentityAssociation identity;

    public OAuthResource(CodeGenerator codeGenerator,
                         UserService userService,
                         JwtService jwtService,
                         CurrentIdentityAssociation identity) {
        this.codeGenerator = codeGenerator;
        this.userService = userService;
        this.jwtService = jwtService;
        this.identity = identity;
    }

    @GET
    @Path("/auth")
    @Produces(MediaType.TEXT_HTML)
    public Response authorize(@BeanParam AuthRequest request) {
        Optional<OAuthClient> clientOptional = OAuthClient.findByClientIdOptional(request.getClientId());

        if (clientOptional.isEmpty()) {
            return buildResponse(Status.NOT_FOUND, "Client ID not found");
        }
        OAuthClient client = clientOptional.get();

        if (!client.callbackUrls.contains(request.getRedirectUri())) {
            return buildResponse(Status.BAD_REQUEST, "Invalid redirect URI");
        }
        if (!"code".equals(request.getResponseType())) {
            return buildResponse(Status.BAD_REQUEST, "Unsupported response type");
        }
        String codeChallenge = request.getCodeChallenge();
        String codeChallengeMethod = Optional.ofNullable(request.getCodeChallengeMethod()).orElse("plain");

        if (codeChallenge == null || codeChallenge.isEmpty()) {
            return buildResponse(Status.BAD_REQUEST, "Code challenge is required");
        }
        if (!List.of("plain", "S256").contains(codeChallengeMethod)) {
            return buildResponse(Status.BAD_REQUEST, "Unsupported code challenge method");
        }
        Set<Scope> scopeSet = mapScopeStringToSet(request.getScope());

        if (scopeSet.isEmpty()) {
            return buildResponse(Status.BAD_REQUEST, "Not a single existing scope has been provided");
        }
        if (!client.scopes.containsAll(scopeSet)) {
            return buildResponse(Status.BAD_REQUEST, "Unsupported scope has been provided");
        }
        String email = identity.getIdentity().getPrincipal().getName();
        User user = userService.getByEmail(email);

        Optional<Consent> consentOptional = Consent.findByResourceOwnerAndClient(user, client);

        if (consentOptional.isPresent()) {
            Response response = handleExistingConsent(request.getRedirectUri(),
                    consentOptional.get(), scopeSet, request.getState(), codeChallenge, codeChallengeMethod);
            if (response != null) {
                return response;
            }
        }
        return Response.ok(Templates.consent(client.clientId, client.name, request.getRedirectUri(),
                request.getState(), user.getId(), scopeSet, codeChallenge, codeChallengeMethod)).build();
    }

    @POST
    @Path("/consent")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response consent(@BeanParam @Valid ConsentForm form,
                            @CookieParam("csrf-token") Cookie csrfTokenCookie,
                            @FormParam("csrf-token") String csrfTokenForm) {
        CsrfTokenValidator.validate(csrfTokenCookie, csrfTokenForm);

        UriBuilder uriBuilder = UriBuilder.fromPath(form.getCallbackUrl());

        if (form.userGaveConsent()) {
            User user = userService.getById(form.getUserId());
            OAuthClient client = OAuthClient.findByClientIdOptional(form.getClientId()).orElseThrow();
            Optional<Consent> consentOptional = Consent.findByResourceOwnerAndClient(user, client);

            Consent consent;
            if (consentOptional.isPresent()) {
                consent = consentOptional.get();
                consent.scopes.addAll(mapScopeStringToSet(form.getScopes()));
            } else {
                consent = new Consent();
                consent.resourceOwner = userService.getById(form.getUserId());
                consent.client = OAuthClient.findByClientIdOptional(form.getClientId()).orElseThrow();
                consent.scopes = mapScopeStringToSet(form.getScopes());
            }
            return buildAuthCodeAndRedirect(uriBuilder, consent, form.getState(), form.getCodeChallenge(), form.getCodeChallengeMethod());
        } else {
            uriBuilder
                    .queryParam("error", "access_denied")
                    .queryParam("error_message", "The resource owner declined to provide the necessary consent");
        }
        return Response.seeOther(uriBuilder.build()).build();
    }

    @POST
    @Path("/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(@BeanParam TokenRequest request,
                          @Context SecurityContext context) {
        String clientId = context.getUserPrincipal().getName();
        Optional<OAuthClient> clientOptional = OAuthClient.findByClientIdOptional(clientId);

        if (clientOptional.isEmpty()) {
            return buildResponse(Status.UNAUTHORIZED, Status.UNAUTHORIZED.getReasonPhrase());
        }
        OAuthClient client = clientOptional.get();

        return switch (request.getGrantType()) {
            case "authorization_code" -> handleAuthorizationCodeGrant(request, client);
            case "refresh_token" -> handleRefreshTokenGrant(request, client);
            case "client_credentials" -> handleClientCredentialsGrant(client);
            default -> buildResponse(Status.BAD_REQUEST, "Unsupported grant type");
        };
    }

    private Response handleAuthorizationCodeGrant(TokenRequest request, OAuthClient client) {
        Optional<AuthCode> authCodeOptional = AuthCode.findByCodeOptional(request.getCode());

        if (authCodeOptional.isEmpty()) {
            return buildResponse(Status.NOT_FOUND, "Auth code does not exist or has already been used");
        }
        AuthCode authCode = authCodeOptional.get();
        Consent consent = authCode.consent;

        if (authCode.isExpired()) {
            AuthCode.deleteByCode(authCode.code);
            return buildResponse(Status.BAD_REQUEST, "Auth code has expired");
        }
        if (!consent.client.clientId.equals(client.clientId)) {
            return buildResponse(Status.BAD_REQUEST, "Authorization code was issued to a different client");
        }
        String codeVerifier = request.getCodeVerifier();

        if (codeVerifier == null || codeVerifier.isEmpty()) {
            return buildResponse(Status.BAD_REQUEST, "Code verifier is required");
        }
        if (!verifyCodeChallenge(authCode.codeChallenge, codeVerifier, authCode.codeChallengeMethod)) {
            return buildResponse(Status.BAD_REQUEST, "Invalid code verifier");
        }
        User resourceOwner = consent.resourceOwner;
        AuthCode.deleteByCode(request.getCode());

        String accessToken = generateAccessToken(resourceOwner.getId().toString(), consent.scopes);
        String refreshToken = generateRefreshToken(consent.id.toString());

        return buildTokenResponse(accessToken, refreshToken);
    }

    private Response handleRefreshTokenGrant(TokenRequest request, OAuthClient client) {
        String refreshToken = request.getRefreshToken();

        if (!jwtService.extractAudience(refreshToken).contains(uriInfo.getAbsolutePath().toString())) {
            return buildResponse(Status.BAD_REQUEST, "Audience mismatch in the refresh token");
        }
        Optional<Consent> consentOptional;
        try {
            consentOptional = Consent.findByIdOptional(UUID.fromString(jwtService.extractSubject(refreshToken)));
        } catch (IllegalArgumentException e) {
            return buildResponse(Status.BAD_REQUEST, "Invalid refresh token subject");
        }
        if (consentOptional.isEmpty()) {
            return buildResponse(Status.BAD_REQUEST, "Refresh token no longer valid: consent revoked");
        }
        Consent consent = consentOptional.get();

        if (!consent.client.clientId.equals(client.clientId)) {
            return buildResponse(Status.BAD_REQUEST, "Refresh Token was issued to a different client");
        }
        String accessToken = generateAccessToken(consent.resourceOwner.getId().toString(), consent.scopes);
        String newRefreshToken = generateRefreshToken(consent.id.toString());

        return buildTokenResponse(accessToken, newRefreshToken);
    }

    private Response handleClientCredentialsGrant(OAuthClient client) {
        String accessToken = generateAccessToken(client.clientId, client.scopes);
        return buildTokenResponse(accessToken, null);
    }

    private Response handleExistingConsent(String callbackUrl, Consent consent, Set<Scope> scopeSet, String state,
                                           String codeChallenge, String codeChallengeMethod) {
        UriBuilder uriBuilder = UriBuilder.fromPath(callbackUrl);

        if (AuthCode.findByConsentOptional(consent).isPresent()) {
            uriBuilder
                    .queryParam("error", "bad_request")
                    .queryParam("error_message", "An existing auth code belonging to this user hasn't yet been used");

            return Response.seeOther(uriBuilder.build()).build();
        }
        scopeSet.removeAll(consent.scopes);

        if (scopeSet.isEmpty()) {
            return buildAuthCodeAndRedirect(uriBuilder, consent, state, codeChallenge, codeChallengeMethod);
        }
        return null;
    }

    private Response buildAuthCodeAndRedirect(UriBuilder uriBuilder, Consent consent, String state,
                                              String codeChallenge, String codeChallengeMethod) {
        AuthCode authCode = new AuthCode();
        String code = codeGenerator.generate(20);
        authCode.code = code;
        authCode.codeChallenge = codeChallenge;
        authCode.codeChallengeMethod = codeChallengeMethod;
        authCode.expiresAt = LocalDateTime.now().plus(AUTH_CODE_EXP_TIME);
        authCode.consent = consent;
        authCode.persist();

        uriBuilder
                .queryParam("code", code)
                .queryParam("state", state);

        return Response.seeOther(uriBuilder.build()).build();
    }

    private boolean verifyCodeChallenge(String codeChallenge, String codeVerifier, String method) {
        return switch (method) {
            case "plain" -> codeChallenge.equals(codeVerifier);
            case "S256" -> codeChallenge.equals(HashUtil.hashWithSHA256(codeVerifier));
            default -> false;
        };
    }

    private Set<Scope> mapScopeStringToSet(String scopes) {
        return Arrays.stream(scopes.split(" "))
                .map(scope -> Scope.findByName(scope.strip()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String generateAccessToken(String subject, Set<Scope> scopes) {
        return jwtService.generate(subject,
                scopes.stream().map(scope -> scope.audience).toList(),
                ACCESS_TOKEN_EXP_TIME,
                new JwtClaim("scopes", scopes.stream().map(scope -> scope.name).toList()));
    }

    private String generateRefreshToken(String subject) {
        return jwtService.generate(subject, List.of(uriInfo.getAbsolutePath().toString()), REFRESH_TOKEN_EXP_TIME);
    }

    private Response buildTokenResponse(String accessToken, String refreshToken) {
        return Response.ok(
                new TokenResponse(accessToken, refreshToken, ACCESS_TOKEN_EXP_TIME.toSeconds(), "Bearer")
        ).build();
    }

    private Response buildResponse(Status status, Object entity) {
        return Response.status(status)
                .entity(entity instanceof String error ? new ErrorResponse(error) : entity)
                .build();
    }
}
