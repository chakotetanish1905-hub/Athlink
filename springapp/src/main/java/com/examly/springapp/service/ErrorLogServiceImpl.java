package com.examly.springapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.examly.springapp.model.ErrorLog;
import com.examly.springapp.repository.ErrorLogRepo;

@Service
public class ErrorLogServiceImpl implements ErrorLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorLogServiceImpl.class);
    private static final int MAX_MESSAGE = 1000;

    private final ErrorLogRepo errorLogRepo;

    public ErrorLogServiceImpl(ErrorLogRepo errorLogRepo) {
        this.errorLogRepo = errorLogRepo;
    }

    @Override
    public void logError(int status, String message, String path, String exceptionType) {
        try {
            String safeMessage = message == null ? null
                    : message.substring(0, Math.min(message.length(), MAX_MESSAGE));
            errorLogRepo.save(new ErrorLog(status, safeMessage, path, exceptionType));
            LOGGER.trace("Error persisted to ErrorLogs: status={} path={} type={}", status, path, exceptionType);
        } catch (RuntimeException ex) {
            // Error logging must never break the original response.
            LOGGER.error("Could not persist error log entry: {}", ex.getMessage());
        }
    }
}
