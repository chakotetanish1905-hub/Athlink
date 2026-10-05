import { Component, OnInit } from '@angular/core';
import { NgForm } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Feedback } from '../../models/feedback.model';
import { Ticket } from '../../models/ticket.model';
import { AuthService } from '../../services/auth.service';
import { FeedbackService } from '../../services/feedback.service';
import { TicketService } from '../../services/ticket.service';
import { UI } from '../ui-helpers';

@Component({
  selector: 'app-clientpostfeedback',
  templateUrl: './clientpostfeedback.component.html',
  styleUrls: ['./clientpostfeedback.component.css']
})
export class ClientpostfeedbackComponent implements OnInit {

  ui = UI;
  eligibleTickets: Ticket[] = [];
  loading = true;

  selectedTicketId: number | null = null;
  category = '';
  rating = 0;
  hoverRating = 0;
  feedbackText = '';

  submitted = false;
  saving = false;
  errorMessage = '';
  showSuccess = false;

  categories = ['Service Quality', 'Professionalism', 'Response Time', 'Communication', 'Other'];
  ratingLabels = ['', 'Poor', 'Fair', 'Good', 'Very good', 'Excellent'];

  constructor(private ticketService: TicketService, private feedbackService: FeedbackService,
              private authService: AuthService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const ticketId = this.route.snapshot.queryParamMap.get('ticketId');
    if (ticketId) {
      this.selectedTicketId = Number(ticketId);
    }
    this.loadEligibleTickets();
  }

  // Feedback can be written for Resolved / Closed tickets with an agent that have no feedback yet
  loadEligibleTickets(): void {
    this.loading = true;
    const userId = this.authService.getUserId();
    this.ticketService.getTicketsByUserId(userId).subscribe({
      next: (tickets) => {
        this.feedbackService.getAllFeedbacksByUserId(userId).subscribe({
          next: (feedbacks) => {
            const reviewed = feedbacks.map(f => f.ticket?.ticketId);
            this.eligibleTickets = tickets.filter(t => UI.isDone(t) && !!t.supportAgent && !reviewed.includes(t.ticketId));
            if (!this.eligibleTickets.some(t => t.ticketId === this.selectedTicketId)) {
              this.selectedTicketId = null;
            }
            this.loading = false;
          },
          error: () => this.loading = false
        });
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = UI.errorMessage(error, 'Could not load your tickets.');
      }
    });
  }

  get selectedTicket(): Ticket | undefined {
    return this.eligibleTickets.find(t => t.ticketId === this.selectedTicketId);
  }

  get shownRating(): number {
    return this.hoverRating || this.rating;
  }

  onSubmit(form: NgForm): void {
    this.submitted = true;
    this.errorMessage = '';
    if (form.invalid || !this.selectedTicketId || this.rating < 1) {
      return;
    }

    // The backend fills in the user (from the JWT), the agent (from the ticket) and the date
    const feedback: Feedback = {
      feedbackText: this.feedbackText.trim(),
      category: this.category,
      rating: this.rating,
      ticket: { ticketId: this.selectedTicketId } as Ticket
    };

    this.saving = true;
    this.feedbackService.sendFeedback(feedback).subscribe({
      next: () => {
        this.saving = false;
        this.showSuccess = true;
        form.resetForm();
        this.selectedTicketId = null;
        this.rating = 0;
        this.submitted = false;
        this.loadEligibleTickets();
      },
      error: (error) => {
        this.saving = false;
        this.errorMessage = UI.errorMessage(error, 'Could not submit your feedback.');
      }
    });
  }
}
