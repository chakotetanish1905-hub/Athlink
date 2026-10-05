import { Component, OnInit } from '@angular/core';
import { Feedback } from '../../models/feedback.model';
import { FeedbackService } from '../../services/feedback.service';
import { UI } from '../ui-helpers';

@Component({
  selector: 'app-managerviewfeedback',
  templateUrl: './managerviewfeedback.component.html',
  styleUrls: ['./managerviewfeedback.component.css']
})
export class ManagerviewfeedbackComponent implements OnInit {

  ui = UI;
  feedbacks: Feedback[] = [];
  loading = true;
  errorMessage = '';
  categoryFilter = '';
  categories = ['Service Quality', 'Professionalism', 'Response Time', 'Communication', 'Other'];

  // Dialogs: user profile, agent profile, ticket details
  userFeedback: Feedback | null = null;
  agentFeedback: Feedback | null = null;
  ticketFeedback: Feedback | null = null;

  constructor(private feedbackService: FeedbackService) {}

  ngOnInit(): void {
    this.loadFeedbacks();
  }

  loadFeedbacks(): void {
    this.loading = true;
    this.errorMessage = '';
    this.feedbackService.getFeedbacks().subscribe({
      next: (feedbacks) => {
        this.feedbacks = feedbacks.sort((a, b) => (b.feedbackId || 0) - (a.feedbackId || 0));
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = UI.errorMessage(error, 'Could not load feedback.');
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
    let positive = 0;
    for (const f of this.feedbacks) {
      if (f.rating >= 4) { positive++; }
    }
    return Math.round(positive / this.feedbacks.length * 100) + '%';
  }
}
