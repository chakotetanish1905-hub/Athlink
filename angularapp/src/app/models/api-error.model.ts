/** Error body returned by the backend GlobalExceptionHandler. */
export interface ApiError {
  timestamp?: string;
  status: number;
  error?: string;
  message: string;
  path?: string;
  validationErrors?: { [field: string]: string };
}
