package com.examly.springapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.FaqEntity;

public interface FaqRepository extends JpaRepository<FaqEntity, Long> {
}
