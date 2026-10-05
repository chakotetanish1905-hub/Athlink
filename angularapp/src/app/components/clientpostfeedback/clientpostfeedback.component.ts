import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { FEEDBACK_CATEGORIES, RATINGS } from '../../constants/constant';
import { Feedback } from '../../models/feedback.model';
import { Ticket } from '../../models/ticket.model';
import { AuthService } from '../../services/auth.service';
import { ErrorHandlerService } from '../../services/error-handler.service';
import { FeedbackService } from '../../services/feedback.service';
import { TicketService } from '../../services/ticket.service';

/** Client: "Add Feedback" for a Resolved/Closed ticket (/client/feedback/add/:ticketId). */
@Component({
  selector: 'app-clientpostfeedback',
  templateUrl: './clientpostfeedback.component.html',
  styleUrls: ['./clientpostfeedback.component.css']
})
export class ClientpostfeedbackComponent implements OnInit {

  readonly categories = FEEDBACK_CATEGORIES;
  readonly ratings = RATINGS;
  feedbackForm: FormGroup;
  submitted = false;
  loading = false;
  showSuccess = false;
  errorMessage = '';
  ticket: Ticket | null = null;

  constructor(
    private readonly fb: FormBuilder,
    private readonly feedbackService: FeedbackService,
    private readonly ticketService: TicketService,
    private readonly authService: AuthService,
    private readonly errorHandler: ErrorHandlerService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {
    this.feedbackForm = this.fb.group({
      feedbackText: ['', [Validators.required, Validators.maxLength(2000)]],
      category: ['', Validators.required],
      rating: ['', [Validators.required, Validators.min(1), Validators.max(5)]]
    });
  }

  ngOnInit(): void {
    const ticketId = Number(this.route.snapshot.paramMap.get('ticketId'));
    if (!ticketId) {
      this.router.navigate(['/client/agents']);
      return;
    }
    this.ticketService.getTicketById(ticketId).subscribe({
      next: ticket => (this.ticket = ticket),
      error: (err: HttpErrorResponse) => (this.errorMessage = this.errorHandler.getMessage(err))
    });
  }

  showError(field: string): boolean {
    const control = this.feedbackForm.get(field);
    return !!control && control.invalid && (control.touched || this.submitted);
  }

  hasError(field: string, error: string): boolean {
    return !!this.feedbackForm.get(field)?.hasError(error);
  }

  onSubmit(): void {
    this.submitted = true;
    this.errorMessage = '';
    if (this.feedbackForm.invalid || !this.ticket?.ticketId) {
      this.feedbackForm.markAllAsTouched();
      return;
    }
    this.loading = true;
    const value = this.feedbackForm.value;
    const feedback: Feedback = {
      feedbackText: (value.feedbackText as string).trim(),
      date: new Date(),
      userId: this.authService.getUserId() as number,
      agentId: this.ticket.agentId,
      ticketId: this.ticket.ticketId,
      category: value.category as string,
      rating: Number(value.rating)
    };
    this.feedbackService.sendFeedback(feedback).subscribe({
      next: () => {
        this.loading = false;
        this.showSuccess = true;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = err.status === 409
          ? 'You have already submitted feedback for this ticket.'
          : this.errorHandler.getMessage(err);
      }
    });
  }

  onSuccessOk(): void {
    this.showSuccess = false;
    this.submitted = false;
    this.feedbackForm.reset({ feedbackText: '', category: '', rating: '' });
  }
}
