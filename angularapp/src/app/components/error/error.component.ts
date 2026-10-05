import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';

interface ErrorInfo {
  title: string;
  message: string;
}

/** Custom error page (SRS: "Something Went Wrong"), with specific text for common HTTP codes. */
@Component({
  selector: 'app-error',
  templateUrl: './error.component.html',
  styleUrls: ['./error.component.css']
})
export class ErrorComponent implements OnInit {

  private static readonly ERRORS: { [code: string]: ErrorInfo } = {
    '401': { title: 'Session Expired', message: 'Please login again.' },
    '403': { title: 'Access Denied', message: 'You are not authorized to view this page.' },
    '404': { title: 'Page Not Found', message: 'The page or resource you requested could not be found.' },
    '500': { title: 'Something Went Wrong', message: "We're sorry, but an error occurred. Please try again later." }
  };

  code = '500';
  info: ErrorInfo = ErrorComponent.ERRORS['500'];

  constructor(private readonly route: ActivatedRoute, public readonly authService: AuthService) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const requested = params.get('code') ?? this.route.snapshot.data['code'] ?? '500';
      this.code = ErrorComponent.ERRORS[requested] ? requested : '500';
      this.info = ErrorComponent.ERRORS[this.code];
    });
  }
}
