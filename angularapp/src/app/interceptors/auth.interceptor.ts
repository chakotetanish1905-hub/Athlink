import {
  HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, Observable, throwError } from 'rxjs';
import { PUBLIC_API_URLS } from '../constants/constant';
import { AuthService } from '../services/auth.service';
import { ErrorHandlerService } from '../services/error-handler.service';
import { NotificationService } from '../services/notification.service';

/**
 * The ONLY place that attaches "Authorization: Bearer <JWT>".
 * Also centralises 401 (session expired -> logout + login page), 403, 5xx and network errors.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly notification: NotificationService
  ) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const isPublic = PUBLIC_API_URLS.some(url => request.url.startsWith(url));
    const token = this.authService.getToken();

    const outgoing = token && !isPublic
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;

    return next.handle(outgoing).pipe(
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && !isPublic) {
          this.handleGlobalError(error);
        }
        return throwError(() => error);
      })
    );
  }

  private handleGlobalError(error: HttpErrorResponse): void {
    switch (error.status) {
      case 401:
        this.authService.logout();
        this.notification.error(ErrorHandlerService.DEFAULT_MESSAGES[401]);
        this.router.navigate(['/login']);
        break;
      case 403:
        this.notification.error(ErrorHandlerService.DEFAULT_MESSAGES[403]);
        break;
      case 0:
        this.notification.error(ErrorHandlerService.DEFAULT_MESSAGES[0]);
        break;
      default:
        if (error.status >= 500) {
          this.notification.error(ErrorHandlerService.DEFAULT_MESSAGES[500]);
        }
    }
  }
}
