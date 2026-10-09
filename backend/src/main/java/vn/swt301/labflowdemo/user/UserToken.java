package vn.swt301.labflowdemo.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Email-verification, password-reset or refresh token. Only the SHA-256 hash is stored. */
@Entity
@Table(name = "user_tokens")
@Getter
@Setter
@NoArgsConstructor
public class UserToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private TokenPurpose purpose;

    private String tokenHash;
    private Instant expiresAt;
    /** null = not used yet. A used token can never be used again. */
    private Instant usedAt;
    private Instant createdAt;
}
