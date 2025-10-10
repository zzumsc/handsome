package org.example.handsome.pojo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    private Long id;

    private String email;

    private String name;

    private Role role;

    private String no; // 工号/学号

    public enum Role {
        admin, student
    }


    public User() {
    }

    public User(Long id, String email, String name, String no, Role role) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.no = no;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setRole(String roleStr) {
        if (roleStr == null) {
            this.role = null;
            return;
        }
        try {
            this.role = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            this.role = null;
        }
    }

    public String getNo() {
        return no;
    }

    public void setNo(String no) {
        this.no = no;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}