import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { PASSWORD_MIN_LENGTH, ROLES, VALIDATION_PATTERNS } from '../../constants/constant';
import { User } from '../../models/user.model';
import { AuthService } from '../../services/auth.service';
import { ErrorHandlerService } from '../../services/error-handler.service';

/** Form-level validator: password and confirmPassword must match. */
export const passwordsMatchValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return password && confirm && password !== confirm ? { passwordMismatch: true } : null;
};

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css']
})
export class SignupComponent {

  readonly roles = [ROLES.MANAGER, ROLES.CLIENT];
  readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  signupForm: FormGroup;
  submitted = false;
  loading = false;
  errorMessage = '';
  showSuccess = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly errorHandler: ErrorHandlerService,
    private readonly router: Router
  ) {
    this.signupForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
      email: ['', [Validators.required, Validators.pattern(VALIDATION_PATTERNS.EMAIL)]],
      mobileNumber: ['', [Validators.required, Validators.pattern(VALIDATION_PATTERNS.MOBILE)]],
      password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(64)]],
      confirmPassword: ['', [Validators.required]],
      userRole: ['', [Validators.required]]
    }, { validators: passwordsMatchValidator });
  }

  showError(field: string): boolean {
    const control = this.signupForm.get(field);
    return !!control && control.invalid && (control.touched || this.submitted);
  }

  hasError(field: string, error: string): boolean {
    return !!this.signupForm.get(field)?.hasError(error);
  }

  get passwordMismatch(): boolean {
    const confirm = this.signupForm.get('confirmPassword');
    return this.signupForm.hasError('passwordMismatch') && !!confirm && (confirm.touched || this.submitted);
  }

  onSubmit(): void {
    this.submitted = true;
    this.errorMessage = '';
    if (this.signupForm.invalid) {
      this.signupForm.markAllAsTouched();
      return;
    }
    this.loading = true;
    const value = this.signupForm.value;
    const user: User = {
      username: (value.username as string).trim(),
      email: (value.email as string).trim(),
      mobileNumber: (value.mobileNumber as string).trim(),
      password: value.password as string,
      userRole: value.userRole as string
    };
    this.authService.register(user).subscribe({
      next: () => {
        this.loading = false;
        this.showSuccess = true;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = err.status === 409 ? 'A user with this email already exists'
          : this.errorHandler.getMessage(err);
      }
    });
  }

  goToLogin(): void {
    this.showSuccess = false;
    this.router.navigate(['/login']);
  }
}
