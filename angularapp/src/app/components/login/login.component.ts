import { Component } from '@angular/core';
import { NgForm } from '@angular/forms';
import { Router } from '@angular/router';
import { Login } from '../../models/login.model';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {

  login: Login = { email: '', password: '' };
  showPassword = false;
  submitted = false;
  loading = false;
  errorMessage = '';

  constructor(private authService: AuthService, private router: Router) {
    // Already logged in: go straight to the app
    if (this.authService.isLoggedIn()) {
      this.goToStartPage();
    }
  }

  onSubmit(form: NgForm): void {
    this.submitted = true;
    this.errorMessage = '';
    if (form.invalid) {
      return;
    }

    this.loading = true;
    this.authService.login({ email: this.login.email.trim(), password: this.login.password }).subscribe({
      next: () => {
        this.loading = false;
        this.goToStartPage();
      },
      error: (error) => {
        this.loading = false;
        if (error.status === 0) {
          this.errorMessage = 'Unable to connect to SupportSphere. Please try again.';
        } else {
          this.errorMessage = 'Invalid Email or Password';
        }
      }
    });
  }

  // Manager starts on the dashboard, Client on the home page
  private goToStartPage(): void {
    if (this.authService.isManager()) {
      this.router.navigate(['/manager/dashboard']);
    } else {
      this.router.navigate(['/home']);
    }
  }
}
