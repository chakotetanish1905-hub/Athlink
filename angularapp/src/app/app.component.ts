import { Component } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { UI } from './components/ui-helpers';
import { AuthService } from './services/auth.service';

interface Crumb {
  label: string;
  link?: string;
}

// The application shell: role-based sidebar (managernav / clientnav), top bar and logout dialog.
// The login and signup pages are shown full screen without the shell.
@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {

  ui = UI;
  currentUrl = '';
  sidebarToggled = false;
  profileOpen = false;
  showLogoutDialog = false;
  searchText = '';
  crumbs: Crumb[] = [];

  constructor(public authService: AuthService, private router: Router) {
    this.router.events.subscribe(event => {
      if (event instanceof NavigationEnd) {
        this.currentUrl = event.urlAfterRedirects;
        this.profileOpen = false;
        this.sidebarToggled = false;
        this.crumbs = this.buildCrumbs(this.currentUrl.split('?')[0]);
      }
    });
  }

  get showShell(): boolean {
    const path = this.currentUrl.split('?')[0];
    return this.authService.isLoggedIn() && path !== '/login' && path !== '/signup' && path !== '/';
  }

  get username(): string {
    return this.authService.getUsername();
  }

  get role(): string {
    return this.authService.getUserRole() || '';
  }

  toggleSidebar(): void {
    this.sidebarToggled = !this.sidebarToggled;
  }

  // The top-bar search opens the tickets page filtered by the typed text
  search(): void {
    const page = this.role === 'Manager' ? '/manager/tickets' : '/client/tickets';
    this.router.navigate([page], { queryParams: { q: this.searchText.trim() || null } });
  }

  askLogout(): void {
    this.profileOpen = false;
    this.showLogoutDialog = true;
  }

  logout(): void {
    this.showLogoutDialog = false;
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  // Breadcrumbs shown in the top bar, for example "Tickets / #1005"
  private buildCrumbs(path: string): Crumb[] {
    const isManager = this.role === 'Manager';
    const ticketsLabel = isManager ? 'Tickets' : 'My Tickets';
    const ticketsLink = isManager ? '/manager/tickets' : '/client/tickets';
    const parts = path.split('/');

    if (path === '/home') { return [{ label: 'Home' }]; }
    if (path === '/manager/dashboard') { return [{ label: 'Dashboard' }]; }
    if (path === '/manager/tickets' || path === '/client/tickets') { return [{ label: ticketsLabel }]; }
    if (path === '/client/tickets/add') { return [{ label: ticketsLabel, link: ticketsLink }, { label: 'Create ticket' }]; }
    if (path.startsWith('/client/tickets/edit')) { return [{ label: ticketsLabel, link: ticketsLink }, { label: 'Edit ticket' }]; }
    if (path.startsWith('/manager/tickets/') || path.startsWith('/client/tickets/')) {
      return [{ label: ticketsLabel, link: ticketsLink }, { label: '#' + parts[parts.length - 1] }];
    }
    if (path === '/manager/agents' || path === '/client/agents') { return [{ label: 'Support Agents' }]; }
    if (path === '/manager/agents/add') { return [{ label: 'Support Agents', link: '/manager/agents' }, { label: 'Add agent' }]; }
    if (path.startsWith('/manager/agents/edit')) { return [{ label: 'Support Agents', link: '/manager/agents' }, { label: 'Edit agent' }]; }
    if (path === '/manager/feedbacks') { return [{ label: 'Feedback' }]; }
    if (path === '/client/feedbacks') { return [{ label: 'My Feedback' }]; }
    if (path === '/client/feedback/add') { return [{ label: 'My Feedback', link: '/client/feedbacks' }, { label: 'Add feedback' }]; }
    return [{ label: 'SupportSphere' }];
  }
}
