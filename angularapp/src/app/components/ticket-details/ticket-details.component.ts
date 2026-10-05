import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TICKET_STATUS } from '../../constants/constant';
import { Ticket } from '../../models/ticket.model';
import { ErrorHandlerService } from '../../services/error-handler.service';
import { NotificationService } from '../../services/notification.service';
import { TicketService } from '../../services/ticket.service';

/** Client: ticket + assigned agent, resolution summary and "Mark as Resolved". */
@Component({
  selector: 'app-ticket-details',
  templateUrl: './ticket-details.component.html',
  styleUrls: ['./ticket-details.component.css']
})
export class TicketDetailsComponent implements OnInit {

  ticket: Ticket | null = null;
  errorMessage = '';

  showSummaryModal = false;
  showConfirmSummary = false;
  showViewSummary = false;
  summaryText = '';
  satisfaction: 'satisfied' | 'notSatisfied' | '' = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly ticketService: TicketService,
    private readonly notification: NotificationService,
    private readonly errorHandler: ErrorHandlerService
  ) {}

  ngOnInit(): void {
    this.loadTicket();
  }

  get isResolvedOrClosed(): boolean {
    return this.ticket?.status === TICKET_STATUS.RESOLVED || this.ticket?.status === TICKET_STATUS.CLOSED;
  }

  openSummary(): void {
    this.summaryText = this.ticket?.resolutionSummary ?? '';
    this.satisfaction = this.ticket?.satisfied === true ? 'satisfied'
      : this.ticket?.satisfied === false ? 'notSatisfied' : '';
    this.showSummaryModal = true;
  }

  submitSummary(): void {
    if (!this.summaryText.trim() || !this.satisfaction) {
      this.notification.error('Please provide a resolution summary and select satisfaction status.');
      return;
    }
    this.showConfirmSummary = true;
  }

  confirmSummary(): void {
    this.showConfirmSummary = false;
    if (!this.ticket?.ticketId) {
      return;
    }
    const updated: Ticket = {
      ...this.ticket,
      resolutionSummary: this.summaryText.trim(),
      satisfied: this.satisfaction === 'satisfied'
    };
    this.ticketService.updateTicket(this.ticket.ticketId, updated).subscribe({
      next: () => {
        this.showSummaryModal = false;
        this.notification.success('Resolution summary saved.');
        this.goBack();
      },
      error: (err: HttpErrorResponse) => this.notification.error(this.errorHandler.getMessage(err))
    });
  }

  markAsResolved(): void {
    if (!this.ticket?.ticketId) {
      return;
    }
    if (!this.ticket.resolutionSummary || !this.ticket.resolutionSummary.trim()) {
      this.notification.error('Please provide resolution details before marking resolve.');
      return;
    }
    const updated: Ticket = { ...this.ticket, status: TICKET_STATUS.RESOLVED };
    this.ticketService.updateTicket(this.ticket.ticketId, updated).subscribe({
      next: ticket => {
        this.ticket = ticket;
        this.notification.success('Ticket marked as resolved.');
      },
      error: (err: HttpErrorResponse) => this.notification.error(this.errorHandler.getMessage(err))
    });
  }

  goBack(): void {
    this.router.navigate(['/client/tickets']);
  }

  private loadTicket(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.ticketService.getTicketById(id).subscribe({
      next: ticket => (this.ticket = ticket),
      error: (err: HttpErrorResponse) => (this.errorMessage = this.errorHandler.getMessage(err))
    });
  }
}
