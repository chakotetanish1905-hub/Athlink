import { Component, OnInit } from '@angular/core';
import { Ticket } from '../../models/ticket.model';
import { AuthService } from '../../services/auth.service';
import { TicketService } from '../../services/ticket.service';
import { UI } from '../ui-helpers';

// Home page for both roles.
// Client: overview of their own tickets. Manager: information about SupportSphere and shortcuts.
@Component({
  selector: 'app-home-page',
  templateUrl: './home-page.component.html',
  styleUrls: ['./home-page.component.css']
})
export class HomePageComponent implements OnInit {

  ui = UI;
  isManager = false;
  firstName = '';
  greeting = '';

  tickets: Ticket[] = [];
  recentTickets: Ticket[] = [];
  waitingOnYou: Ticket[] = [];
  openCount = 0;
  resolvedCount = 0;
  loading = false;
  errorMessage = '';

  constructor(private authService: AuthService, private ticketService: TicketService) {}

  ngOnInit(): void {
    this.isManager = this.authService.isManager();
    this.firstName = this.authService.getUsername().split(' ')[0];

    const hour = new Date().getHours();
    if (hour < 12) {
      this.greeting = 'Good morning';
    } else if (hour < 17) {
      this.greeting = 'Good afternoon';
    } else {
      this.greeting = 'Good evening';
    }

    if (!this.isManager) {
      this.loadMyTickets();
    }
  }

  loadMyTickets(): void {
    this.loading = true;
    this.errorMessage = '';
    this.ticketService.getTicketsByUserId(this.authService.getUserId()).subscribe({
      next: (tickets) => {
        this.tickets = tickets.sort(UI.newestFirst);
        this.recentTickets = this.tickets.slice(0, 5);
        this.openCount = 0;
        this.resolvedCount = 0;
        this.waitingOnYou = [];
        for (const t of this.tickets) {
          if (t.status === 'Open' || t.status === 'In Progress') {
            this.openCount++;
            // An agent is working on it: the client confirms the fix with a resolution summary
            if (t.supportAgent) {
              this.waitingOnYou.push(t);
            }
          } else {
            this.resolvedCount++;
          }
        }
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = UI.errorMessage(error, 'Could not load your tickets.');
      }
    });
  }
}
