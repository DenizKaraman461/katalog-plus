package com.denizcelikhalat.katalog.model;

/**
 * Kullanıcı rolleri. DB'de isim olarak saklanır (EnumType.STRING).
 * Spring Security tarafında ".roles(role.name())" ile "ROLE_USER" / "ROLE_ADMIN" olur.
 */
public enum Role {
    USER,
    ADMIN
}
