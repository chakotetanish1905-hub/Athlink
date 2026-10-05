import { Component, EventEmitter, Output } from '@angular/core';
import { Router } from '@angular/router';
import { UI } from '../ui-helpers';
import { AuthService } from '../../services/auth.service';

// Client sidebar. Shows only Client features.
@Component({
  selector: 'app-clientnav',
  templateUrl: './clientnav.component.html',
  styleUrls: ['./clientnav.component.css']
})
export class ClientnavComponent {

  @Output() toggleSidebar = new EventEmitter<void>();
  @Output() logout = new EventEmitter<void>();

  ui = UI;

  constructor(public authService: AuthService, private router: Router) {}

  isActive(item: string): boolean {
    const url = this.router.url.split('?')[0];
    if (item === 'home') { return url === '/home'; }
    if (item === 'addTicket') { return url === '/client/tickets/add' || url.startsWith('/client/tickets/edit'); }
    if (item === 'tickets') {
      return url.startsWith('/client/tickets') && url !== '/client/tickets/add' && !url.startsWith('/client/tickets/edit');
    }
    if (item === 'agents') { return url.startsWith('/client/agents'); }
    if (item === 'feedback') { return url.startsWith('/client/feedback'); }
    return false;
  }
}
