import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-managernav',
  templateUrl: './managernav.component.html',
  styleUrls: ['./managernav.component.css']
})
export class ManagernavComponent {
  showLogoutConfirm = false;

  constructor(public readonly authService: AuthService, private readonly router: Router) {}

  confirmLogout(): void {
    this.showLogoutConfirm = false;
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
