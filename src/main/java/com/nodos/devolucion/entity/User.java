package com.nodos.devolucion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuarios") // Cambia esto por el nombre real de tu tabla en BD
public class User implements UserDetails {

    // Asumo que este es el ID autoincremental de la tabla
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usu_ideregistro")
    private Long ideRegistro;

    @Column(name = "usuario_nit", length = 20)
    private String nit;

    @Column(name = "usuario_nom", length = 100)
    private String nombre;

    @Column(name = "usu_login", unique = true, nullable = false, length = 50)
    private String login;

    @Column(name = "usuario_pas", nullable = false)
    private String password;

    // Nota: Tienes 'usuario_mail' y 'usu_email' en tu lista. Solo dejé uno aquí.
    @Column(name = "usu_email")
    private String email;

    @Column(name = "usuario_codcar")
    private Integer codCargo;

    @Column(name = "usuario_codper")
    private Integer codPerfil; // Aquí manejaríamos el "Rol" para Spring Security

    @Column(name = "usuario_codemp")
    private Integer codEmpresa;

    @Column(name = "usuario_coddepemp")
    private Integer codDepEmpresa;

    @Column(name = "usuario_swtact")
    private Boolean swtActivo; // Asumo que es un switch (booleano) para saber si está activo

    @Column(name = "usuario_swtcar")
    private Boolean swtCargo;

    @Column(name = "usuario_swtper")
    private Boolean swtPerfil;

    @Column(name = "usuario_codpro")
    private Integer codPro;

    @Column(name = "usu_topfinancia")
    private Double topFinancia; // Double o BigDecimal para dinero/topes

    @Column(name = "usu_modrecexterno")
    private String modRecExterno;

    @Column(name = "usu_finvencido")
    private Boolean finVencido;

    @Column(name = "nit_oia")
    private String nitOia;

    @Column(name = "ter_ideregistro")
    private Long terIdeRegistro;

    @Column(name = "usu_ideconfirmacion")
    private String ideConfirmacion;

    @Column(name = "usu_expiraconfirmacion")
    private LocalDateTime expiraConfirmacion; // Ideal para fechas de expiración

    // Nota: Tenías 'cod_per' y 'usuario_codper'. Si son lo mismo, omite este.
    @Column(name = "cod_per")
    private Integer codPer; 

    // =========================================================
    // Métodos obligatorios de Spring Security (UserDetails)
    // =========================================================
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Por ahora lo simulamos usando codPerfil. 
        // Luego lo conectaremos a tu tabla de perfiles reales.
        return List.of(new SimpleGrantedAuthority("ROLE_PERFIL_" + codPerfil));
    }

    @Override
    public String getUsername() {
        return this.login; // Spring usará 'usu_login' para identificar al usuario
    }

    @Override
    public String getPassword() {
        return this.password; // Spring usará 'usuario_pas' para validar la clave
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; 
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // Conectamos el estado de Spring con tu campo de la BD
        return this.swtActivo != null ? this.swtActivo : false; 
    }
}