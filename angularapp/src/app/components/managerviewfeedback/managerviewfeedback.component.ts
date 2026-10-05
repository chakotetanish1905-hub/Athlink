import { Component, OnInit } from '@angular/core';
import { FEEDBACK_CATEGORIES } from '../../constants/constant';
import { Feedback } from '../../models/feedback.model';
import { FeedbackService } from '../../services/feedback.service';

/** Manager: "Feedback Received" with category filter and user/agent/ticket popups. */
@Component({
  selector: 'app-managerviewfeedback',
  templateUrl: './managerviewfeedback.component.html',
  styleUrls: ['./managerviewfeedback.component.css']
})
export class ManagerviewfeedbackComponent implements OnInit {

  feedbacks: Feedback[] = [];
  categoryFilter = '';
  loading = true;
  userInfo: Feedback | null = null;
  agentInfo: Feedback | null = null;
  ticketInfo: Feedback | null = null;

  constructor(private readonly feedbackService: FeedbackService) {}

  ngOnInit(): void {
    this.feedbackService.getFeedbacks().subscribe({
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

  /** Known categories plus any others present in the data. */
  get categories(): string[] {
    return Array.from(new Set([...FEEDBACK_CATEGORIES, ...this.feedbacks.map(f => f.category)]));
  }

  get filteredFeedbacks(): Feedback[] {
    return this.feedbacks.filter(f => !this.categoryFilter || f.category === this.categoryFilter);
  }
}
