package com.example.CloudFlexMultiCloud.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "email",unique = true)
    private String email;

    @Column(name="password")
    private String password;

    @Column(name="created_at")
    private LocalDateTime createdAt; //= LocalDateTime.now();;

    @OneToMany(mappedBy = "user",cascade = CascadeType.ALL)
    private List<CloudAccount> cloudAccounts = new ArrayList<>();


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<CloudAccount> getCloudAccounts() {
        return cloudAccounts;
    }

    public void setCloudAccounts(List<CloudAccount> cloudAccounts) {
        this.cloudAccounts = cloudAccounts;
    }
}
