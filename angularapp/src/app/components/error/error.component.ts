import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

// Custom error pages: 404 for unknown pages, and "Something Went Wrong" (/error) for everything else.
@Component({
  selector: 'app-error',
  templateUrl: './error.component.html',
  styleUrls: ['./error.component.css']
})
export class ErrorComponent implements OnInit {

  code = '500';

  constructor(private route: ActivatedRoute, private router: Router, public authService: AuthService) {}

  ngOnInit(): void {
    const codeParam = this.route.snapshot.queryParamMap.get('code');
    if (codeParam) {
      this.code = codeParam;
    } else if (!this.router.url.startsWith('/error')) {
      this.code = '404';   // the "**" route: the page does not exist
    }
  }

  get homeLink(): string {
    if (!this.authService.isLoggedIn()) { return '/login'; }
    return this.authService.isManager() ? '/manager/dashboard' : '/home';
  }

  goBack(): void {
    history.back();
  }
}
