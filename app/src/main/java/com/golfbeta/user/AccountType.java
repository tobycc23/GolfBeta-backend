package com.golfbeta.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "account_type")
@Data
public class AccountType {

    @Id
    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "video_group_ids", columnDefinition = "uuid[]")
    private List<UUID> videoGroupIds;
}
