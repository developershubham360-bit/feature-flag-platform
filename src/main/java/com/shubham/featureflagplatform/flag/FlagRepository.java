package com.shubham.featureflagplatform.flag;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagRepository extends JpaRepository<Flag, Long> {
    Optional<Flag> findByKeyAndEnvironment(String key, String environment);
}
