import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ApiError } from '../models/api-error.model';

/** Turns any HTTP failure into one user-friendly message. */
@Injectable({ providedIn: 'root' })
export class ErrorHandlerService {

  static readonly DEFAULT_MESSAGES: { [status: number]: string } = {
    0: 'Unable to reach the server. Please check your connection.',
    400: 'Please check the submitted values.',
    401: 'Please login again.',
    403: 'You are not authorized.',
    404: 'Resource not found.',
    409: 'Duplicate/conflict: this record already exists or is in use.',
    500: 'Something went wrong.'
  };

  getMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return ErrorHandlerService.DEFAULT_MESSAGES[500];
    }
    const body = error.error as ApiError | null;
    if (error.status === 400 && body?.validationErrors) {
      const first = Object.values(body.validationErrors)[0];
      if (first) {
        return first;
      }
    }
    // Backend messages for 4xx are user-safe; 5xx bodies are always replaced with a generic message.
    if (error.status >= 400 && error.status < 500 && body && typeof body.message === 'string' && body.message) {
      return body.message;
    }
    return ErrorHandlerService.DEFAULT_MESSAGES[error.status] ?? ErrorHandlerService.DEFAULT_MESSAGES[500];
  }
}
