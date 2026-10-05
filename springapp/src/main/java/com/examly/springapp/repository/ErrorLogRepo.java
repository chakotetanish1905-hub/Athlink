package com.examly.springapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.examly.springapp.model.ErrorLog;

@Repository
public interface ErrorLogRepo extends JpaRepository<ErrorLog, Long> {
}
