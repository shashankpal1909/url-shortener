package com.shashank.url_shortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shashank.url_shortener.entity.URL;

public interface URLRepository extends JpaRepository<URL, Long> {

    Optional<URL> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /**
     * Atomically increments the click count for the given short code by 1.
     * Replaces the read-modify-write pattern with a single UPDATE statement,
     * eliminating race conditions under concurrent redirect traffic.
     *
     * @return number of rows updated (0 if short code not found)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE URL u SET u.clickCount = u.clickCount + 1 WHERE u.shortCode = :shortCode")
    int incrementClickCount(@Param("shortCode") String shortCode);

    /**
     * Atomically increments the click count for the given short code by the
     * specified amount. Used by the async click-aggregation flush job to apply
     * batched Redis counters to the database in a single statement.
     *
     * @return number of rows updated (0 if short code not found)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE URL u SET u.clickCount = u.clickCount + :increment WHERE u.shortCode = :shortCode")
    int incrementClickCountBy(@Param("shortCode") String shortCode, @Param("increment") long increment);

}
