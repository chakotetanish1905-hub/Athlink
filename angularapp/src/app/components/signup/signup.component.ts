import { Component } from '@angular/core';
import { NgForm } from '@angular/forms';
import { Router } from '@angular/router';
import { User } from '../../models/user.model';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css']
})
export class SignupComponent {

  user: User = { username: '', email: '', mobileNumber: '', password: '', userRole: '' };
  confirmPassword = '';
  submitted = false;
  loading = false;
  errorMessage = '';
  registered = false;
  registeredEmail = '';

  constructor(private authService: AuthService, private router: Router) {}

  passwordsDiffer(): boolean {
    return this.confirmPassword !== '' && this.confirmPassword !== this.user.password;
  }

  onSubmit(form: NgForm): void {
    this.submitted = true;
    this.errorMessage = '';
    if (form.invalid || this.passwordsDiffer()) {
      return;
    }

    this.loading = true;
    const newUser: User = {
      username: this.user.username.trim(),
      email: this.user.email.trim(),
      mobileNumber: this.user.mobileNumber.trim(),
      password: this.user.password,
      userRole: this.user.userRole
    };

    this.authService.register(newUser).subscribe({
      next: () => {
        this.loading = false;
        this.registeredEmail = newUser.email;
        this.registered = true;
        form.resetForm();
        this.submitted = false;
      },
      error: (error) => {
        this.loading = false;
        if (error.status === 409) {
          this.errorMessage = 'A user with this email already exists';
        } else if (error.error && error.error.message) {
          this.errorMessage = error.error.message;
        } else {
          this.errorMessage = 'Registration failed. Please try again.';
        }
      }
    });
  }

  goToLogin(): void {
    this.registered = false;
    this.router.navigate(['/login']);
  }
}
