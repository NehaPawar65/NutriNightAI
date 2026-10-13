package com.nutrinight.repository;

import com.nutrinight.entity.ModelRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelRunRepository extends JpaRepository<ModelRun, Long> {

    Optional<ModelRun> findByModelNameAndModelVersion(String modelName, String modelVersion);

    List<ModelRun> findTop20ByOrderByCreatedAtDesc();
}
