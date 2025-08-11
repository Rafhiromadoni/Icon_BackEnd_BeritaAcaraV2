package com.rafhi.entity;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

@Entity
public class AppUser extends PanacheEntity {
    public String username;
    public String password;
    // public String role;

    public static void add(String username, String password) {
        AppUser user = new AppUser();
        user.username = username;
        user.password = BcryptUtil.bcryptHash(password); // Password di-hash
        // user.role = role;
        user.persist();
    }
}