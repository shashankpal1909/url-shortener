package com.shashank.url_shortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shashank.url_shortener.entity.URL;

public interface URLRepository extends JpaRepository<URL, Long> {

    Optional<URL> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

}