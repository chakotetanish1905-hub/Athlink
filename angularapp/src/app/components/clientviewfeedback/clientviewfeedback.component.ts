import { Component, OnInit } from '@angular/core';
import { Feedback } from '../../models/feedback.model';
import { AuthService } from '../../services/auth.service';
import { FeedbackService } from '../../services/feedback.service';
import { UI } from '../ui-helpers';

@Component({
  selector: 'app-clientviewfeedback',
  templateUrl: './clientviewfeedback.component.html',
  styleUrls: ['./clientviewfeedback.component.css']
})
export class ClientviewfeedbackComponent implements OnInit {

  ui = UI;
  feedbacks: Feedback[] = [];
  loading = true;
  errorMessage = '';
  categoryFilter = '';
  categories = ['Service Quality', 'Professionalism', 'Response Time', 'Communication', 'Other'];

  ticketFeedback: Feedback | null = null;   // "View Ticket Info"
  agentFeedback: Feedback | null = null;    // "View Agent Info"
  deleteFeedback: Feedback | null = null;
  deleteError = '';

  constructor(private feedbackService: FeedbackService, private authService: AuthService) {}

  ngOnInit(): void {
    this.loadFeedbacks();
  }

  loadFeedbacks(): void {
    this.loading = true;
    this.errorMessage = '';
    this.feedbackService.getAllFeedbacksByUserId(this.authService.getUserId()).subscribe({
      next: (feedbacks) => {
        this.feedbacks = feedbacks.sort((a, b) => (b.feedbackId || 0) - (a.feedbackId || 0));
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = UI.errorMessage(error, 'Could not load your feedback.');
      }
    });
  }

  get filteredFeedbacks(): Feedback[] {
    if (!this.categoryFilter) { return this.feedbacks; }
    return this.feedbacks.filter(f => f.category === this.categoryFilter);
  }

  get averageRating(): number {
    if (this.feedbacks.length === 0) { return 0; }
    let total = 0;
    for (const f of this.feedbacks) { total += f.rating; }
    return total / this.feedbacks.length;
  }

  get roundedAverage(): number {
    return Math.round(this.averageRating);
  }

  get positivePercent(): string {
    if (this.feedbacks.length === 0) { return '0%'; }
    const positive = this.feedbacks.filter(f => f.rating >= 4).length;
    return Math.round(positive / this.feedbacks.length * 100) + '%';
  }

  confirmDelete(): void {
    if (!this.deleteFeedback) { return; }
    const feedback = this.deleteFeedback;
    this.feedbackService.deleteFeedback(feedback.feedbackId!).subscribe({
      next: () => {
        this.deleteFeedback = null;
        this.feedbacks = this.feedbacks.filter(f => f.feedbackId !== feedback.feedbackId);
      },
      error: (error) => this.deleteError = UI.errorMessage(error, 'Could not delete the feedback.')
    });
  }
}
