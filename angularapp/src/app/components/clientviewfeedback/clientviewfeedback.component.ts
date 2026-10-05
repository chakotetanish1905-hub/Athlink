import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { Feedback } from '../../models/feedback.model';
import { AuthService } from '../../services/auth.service';
import { ErrorHandlerService } from '../../services/error-handler.service';
import { FeedbackService } from '../../services/feedback.service';
import { NotificationService } from '../../services/notification.service';

/** Client: "My Feedback". */
@Component({
  selector: 'app-clientviewfeedback',
  templateUrl: './clientviewfeedback.component.html',
  styleUrls: ['./clientviewfeedback.component.css']
})
export class ClientviewfeedbackComponent implements OnInit {

  feedbacks: Feedback[] = [];
  loading = true;
  ticketInfo: Feedback | null = null;
  agentInfo: Feedback | null = null;
  feedbackToDelete: Feedback | null = null;

  constructor(
    private readonly feedbackService: FeedbackService,
    private readonly authService: AuthService,
    private readonly notification: NotificationService,
    private readonly errorHandler: ErrorHandlerService
  ) {}

  ngOnInit(): void {
    this.loadFeedbacks();
  }

  confirmDelete(): void {
    const feedback = this.feedbackToDelete;
    this.feedbackToDelete = null;
    if (!feedback?.feedbackId) {
      return;
    }
    this.feedbackService.deleteFeedback(feedback.feedbackId).subscribe({
      next: () => {
        this.notification.success('Feedback deleted successfully.');
        this.loadFeedbacks();
      },
      error: (err: HttpErrorResponse) => this.notification.error(this.errorHandler.getMessage(err))
    });
  }

  private loadFeedbacks(): void {
    const userId = this.authService.getUserId();
    if (userId === null) {
      return;
    }
    this.loading = true;
    this.feedbackService.getAllFeedbacksByUserId(userId).subscribe({
      next: feedbacks => {
        this.feedbacks = feedbacks;
        this.loading = false;
      },
      error: () => {
        this.feedbacks = [];
        this.loading = false;
      }
    });
  }
}
