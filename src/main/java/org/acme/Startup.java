package org.acme;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.acme.user.User;
import org.acme.user.UserRole;
import org.acme.user.repository.UserRepository;

@ApplicationScoped
public class Startup {

    @Inject
    UserRepository userRepository;

    public void saveAdminUser(@Observes StartupEvent event) {
        userRepository.save(new User("Egor", "egor@gmail.com", "password", UserRole.ADMIN));
    }

    public void saveRegularUser(@Observes StartupEvent event) {
        userRepository.save(new User("John", "john@gmail.com", "password"));
    }
}
