package com.nodos.devolucion.repository;

import com.nodos.devolucion.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data JPA lee el nombre del método y genera automáticamente el SQL:
    // SELECT * FROM usuarios WHERE usu_login = ?
    Optional<User> findByLogin(String login);

    // Te dejo estas dos por si más adelante necesitas validar duplicados
    // al momento de importar o registrar nuevos usuarios
    Optional<User> findByNit(String nit);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByLogin(String login);
}