import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { PASSWORD_MIN_LENGTH, VALIDATION_PATTERNS } from '../../constants/constant';
import { Login } from '../../models/login.model';
import { AuthService } from '../../services/auth.service';
import { ErrorHandlerService } from '../../services/error-handler.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {

  readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  loginForm: FormGroup;
  submitted = false;
  loading = false;
  showPassword = false;
  errorMessage = '';

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly errorHandler: ErrorHandlerService,
    private readonly router: Router,
    private readonly route: ActivatedRoute
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.pattern(VALIDATION_PATTERNS.EMAIL)]],
      password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH)]]
    });
  }

  ngOnInit(): void {
    if (this.authService.isLoggedIn()) {
      this.router.navigate(['/home']);
    }
  }

  showError(field: string): boolean {
    const control = this.loginForm.get(field);
    return !!control && control.invalid && (control.touched || this.submitted);
  }

  hasError(field: string, error: string): boolean {
    return !!this.loginForm.get(field)?.hasError(error);
  }

  onSubmit(): void {
    this.submitted = true;
    this.errorMessage = '';
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }
    this.loading = true;
    const credentials: Login = {
      email: (this.loginForm.value.email as string).trim(),
      password: this.loginForm.value.password as string
    };
    this.authService.login(credentials).subscribe({
      next: () => {
        this.loading = false;
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        this.router.navigateByUrl(returnUrl && returnUrl.startsWith('/') ? returnUrl : '/home');
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = err.status === 401
          ? 'Invalid email address or password'
          : this.errorHandler.getMessage(err);
      }
    });
  }
}
