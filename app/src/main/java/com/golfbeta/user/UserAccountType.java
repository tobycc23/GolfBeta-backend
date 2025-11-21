package com.golfbeta.user;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "user_account_types")
@Data
public class UserAccountType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false, unique = true)
    private UserProfile userProfile;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "account_type", nullable = false, referencedColumnName = "name")
    private AccountType accountType;

}
