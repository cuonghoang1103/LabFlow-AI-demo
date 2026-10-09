package vn.swt301.labflowdemo.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    /** Marks every still-usable token of a user for one purpose as used (e.g. old reset links). */
    @Modifying
    @Query("update UserToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateAll(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);
}
