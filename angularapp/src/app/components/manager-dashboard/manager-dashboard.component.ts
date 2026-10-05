import { Component, OnInit } from '@angular/core';
import { TICKET_STATUS } from '../../constants/constant';
import { Ticket } from '../../models/ticket.model';
import { TicketService } from '../../services/ticket.service';

/** Manager: client satisfaction summary built from getAllTickets(). */
@Component({
  selector: 'app-manager-dashboard',
  templateUrl: './manager-dashboard.component.html',
  styleUrls: ['./manager-dashboard.component.css']
})
export class ManagerDashboardComponent implements OnInit {

  tickets: Ticket[] = [];
  happyClients = 0;
  unhappyClients = 0;
  loading = true;

  constructor(private readonly ticketService: TicketService) {}

  ngOnInit(): void {
    this.ticketService.getAllTickets().subscribe({
      next: tickets => {
        this.tickets = tickets;
        const finished = tickets.filter(t => this.isFinished(t));
        this.happyClients = finished.filter(t => t.satisfied === true).length;
        this.unhappyClients = finished.filter(t => t.satisfied === false).length;
        this.loading = false;
      },
      error: () => {
        this.tickets = [];
        this.loading = false;
      }
    });
  }

  isFinished(ticket: Ticket): boolean {
    return ticket.status === TICKET_STATUS.RESOLVED || ticket.status === TICKET_STATUS.CLOSED;
  }
}
