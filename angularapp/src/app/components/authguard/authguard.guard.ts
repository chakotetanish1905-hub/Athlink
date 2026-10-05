import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, UrlTree } from '@angular/router';
import { AuthService } from '../../services/auth.service';

// Blocks pages for users who are not logged in (redirect to /login)
// and pages meant for the other role (route data: { role: 'Manager' | 'Client' }).
// This only controls navigation - the backend still checks every API call.
@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {

  constructor(private authService: AuthService, private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot): boolean | UrlTree {
    if (!this.authService.isLoggedIn()) {
      return this.router.createUrlTree(['/login']);
    }

    const requiredRole = route.data['role'];
    if (requiredRole && requiredRole !== this.authService.getUserRole()) {
      return this.router.createUrlTree(['/error'], { queryParams: { code: 404 } });
    }
    return true;
  }
}
