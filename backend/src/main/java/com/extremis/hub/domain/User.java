package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * No row is ever created here for an anonymous visitor. Anonymous
 * tool use and anonymous ToolUsage/GeneratedResult rows always use a
 * null user reference. A row is first created when a real, verified
 * identity exists (currently: Google OAuth sign-in, Phase 16) — see
 * docs/07-phase2-system-design.md §1.1.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_user")
public class User extends Auditable {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true)
    private String googleSubjectId;

    private Instant deletedAt;
}
