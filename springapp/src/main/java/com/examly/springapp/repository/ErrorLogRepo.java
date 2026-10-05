package com.examly.springapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.ErrorLog;

public interface ErrorLogRepo extends JpaRepository<ErrorLog, Long> {
}
