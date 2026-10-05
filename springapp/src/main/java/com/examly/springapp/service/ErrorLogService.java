package com.examly.springapp.service;

public interface ErrorLogService {

    /** Persists a handled error into the ErrorLogs table. Never throws. */
    void logError(int status, String message, String path, String exceptionType);
}
