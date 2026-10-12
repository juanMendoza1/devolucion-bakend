package com.nodos.devolucion.config;

import com.nodos.devolucion.entity.User;
import com.nodos.devolucion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Garantiza que exista el usuario administrador inicial.
 * La contraseña inicial NO está en el código: se toma de ADMIN_INITIAL_PASSWORD (.env o variable de entorno)
 * y solo se usa si el usuario aún no existe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String LOGIN_INICIAL = "jlmendoza";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_INITIAL_PASSWORD:}")
    private String adminInitialPassword;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByLogin(LOGIN_INICIAL)) {
            if (adminInitialPassword == null || adminInitialPassword.isBlank()) {
                log.warn("⚠️ El usuario inicial '{}' no existe y ADMIN_INITIAL_PASSWORD no está definido. No se crea.",
                        LOGIN_INICIAL);
                return;
            }

            User usuarioAdmin = User.builder()
                    .login(LOGIN_INICIAL)
                    .password(passwordEncoder.encode(adminInitialPassword))
                    .nombre("Juan L. Mendoza")
                    .email("jlmendoza@ejemplo.com")
                    .swtActivo(true) // Debe estar activo para pasar el 'isEnabled()'
                    .codPerfil(1)    // Perfil 1 = administrador
                    .build();

            userRepository.save(usuarioAdmin);
            log.info("✅ Usuario inicial '{}' creado.", LOGIN_INICIAL);
            return;
        }

        // Garantizamos que el usuario inicial siga siendo administrador (perfil 1) y esté activo.
        // No tocamos la contraseña para no pisar un cambio hecho por el usuario.
        User existente = userRepository.findByLogin(LOGIN_INICIAL).orElseThrow();
        if (!Integer.valueOf(1).equals(existente.getCodPerfil()) || !Boolean.TRUE.equals(existente.getSwtActivo())) {
            existente.setCodPerfil(1);
            existente.setSwtActivo(true);
            userRepository.save(existente);
            log.info("🔐 Usuario '{}' ajustado como administrador (perfil 1).", LOGIN_INICIAL);
        }
    }
}
