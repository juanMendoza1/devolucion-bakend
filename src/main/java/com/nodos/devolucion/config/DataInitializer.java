package com.nodos.devolucion.config;

import com.nodos.devolucion.entity.User;
import com.nodos.devolucion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        
        String loginInicial = "jlmendoza";

        // 1. Verificamos si el usuario ya existe para no duplicarlo cada vez que reinicies Tomcat
        if (!userRepository.existsByLogin(loginInicial)) {
            
            // 2. Construimos el usuario usando el patrón Builder que habilitaste con Lombok
            User usuarioAdmin = User.builder()
                    .login(loginInicial)
                    // ¡Muy importante encriptar la contraseña para que el login funcione!
                    .password(passwordEncoder.encode("***REMOVED***")) 
                    .nombre("Juan L. Mendoza")
                    .email("jlmendoza@ejemplo.com") // Puedes poner tu correo real
                    .swtActivo(true) // Debe estar activo para pasar el 'isEnabled()'
                    .codPerfil(1) // Te asignamos el perfil 1 (por ejemplo, Admin)
                    .build();

            // 3. Guardamos en la base de datos
            userRepository.save(usuarioAdmin);
            
            System.out.println("✅ Usuario inicial '" + loginInicial + "' creado exitosamente en la base de datos.");
        } else {
            System.out.println("👍 El usuario '" + loginInicial + "' ya estaba registrado. Omitiendo creación.");
        }
    }
}