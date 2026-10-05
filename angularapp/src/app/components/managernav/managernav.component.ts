import { Component, EventEmitter, Output } from '@angular/core';
import { Router } from '@angular/router';
import { UI } from '../ui-helpers';
import { AuthService } from '../../services/auth.service';

// Manager sidebar. Shows only Manager features.
@Component({
  selector: 'app-managernav',
  templateUrl: './managernav.component.html',
  styleUrls: ['./managernav.component.css']
})
export class ManagernavComponent {

  @Output() toggleSidebar = new EventEmitter<void>();
  @Output() logout = new EventEmitter<void>();

  ui = UI;

  constructor(public authService: AuthService, private router: Router) {}

  // Marks the current menu item. "Add Support Agent" and "View Support Agents" share a URL prefix,
  // so the add/edit form only highlights "Add Support Agent".
  isActive(item: string): boolean {
    const url = this.router.url.split('?')[0];
    if (item === 'home') { return url === '/home'; }
    if (item === 'dashboard') { return url === '/manager/dashboard'; }
    if (item === 'tickets') { return url.startsWith('/manager/tickets'); }
    if (item === 'addAgent') { return url.startsWith('/manager/agents/add') || url.startsWith('/manager/agents/edit'); }
    if (item === 'agents') { return url === '/manager/agents'; }
    if (item === 'feedback') { return url.startsWith('/manager/feedbacks'); }
    return false;
  }
}
