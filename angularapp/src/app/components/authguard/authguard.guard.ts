import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from '../../services/auth.service';

/**
 * Route protection (SRS: Auth Guard with canActivate).
 * - Not logged in / expired token  -> /login
 * - Route requires another role (route data: { role: 'Manager' | 'Client' }) -> /error/403
 * This is navigation convenience only; the backend enforces JWT and roles on every request.
 */
@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {

  constructor(private readonly authService: AuthService, private readonly router: Router) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    if (!this.authService.isLoggedIn()) {
      this.authService.logout();
      return this.router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    const requiredRole = route.data['role'] as string | undefined;
    if (requiredRole && this.authService.getUserRole() !== requiredRole) {
      return this.router.createUrlTree(['/error', 403]);
    }
    return true;
  }
}
