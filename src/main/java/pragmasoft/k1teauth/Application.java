package pragmasoft.k1teauth;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import pragmasoft.k1teauth.common.ServerInfo;
import pragmasoft.k1teauth.oauth.scope.Scope;
import pragmasoft.k1teauth.oauth.scope.ScopeRepository;

@Singleton
public class Application {

    private final ScopeRepository scopeRepository;
    private final ServerInfo serverInfo;

    public Application(ScopeRepository scopeRepository,
                       ServerInfo serverInfo) {
        this.scopeRepository = scopeRepository;
        this.serverInfo = serverInfo;
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

    @EventListener
    public void onStartup(StartupEvent startupEvent) {
        if (!scopeRepository.existsById("openid")) {
            scopeRepository.save(new Scope("openid", "Obtain basic profile information", serverInfo.getBaseUrl()));
        }
    }
}