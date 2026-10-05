import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { apiUrl } from '../../apiconfig';
import { API_ENDPOINTS } from '../constants/constant';
import { Feedback } from '../models/feedback.model';

@Injectable({ providedIn: 'root' })
export class FeedbackService {

  public apiUrl: string = apiUrl;

  constructor(private readonly http: HttpClient) {}

  sendFeedback(feedback: Feedback): Observable<Feedback> {
    return this.http.post<Feedback>(API_ENDPOINTS.FEEDBACK.BASE, this.toPayload(feedback));
  }

  getAllFeedbacksByUserId(userId: number): Observable<Feedback[]> {
    return this.http.get<Feedback[] | null>(API_ENDPOINTS.FEEDBACK.BY_USER(userId)).pipe(map(list => list ?? []));
  }

  deleteFeedback(feedbackId: number): Observable<void> {
    return this.http.delete<void>(API_ENDPOINTS.FEEDBACK.BY_ID(feedbackId));
  }

  getFeedbacks(): Observable<Feedback[]> {
    return this.http.get<Feedback[] | null>(API_ENDPOINTS.FEEDBACK.BASE).pipe(map(list => list ?? []));
  }

  /** The server stamps the feedback date; nested read-only details are not sent. */
  private toPayload(feedback: Feedback): Partial<Feedback> {
    const { user, supportAgent, ticket, ...payload } = feedback;
    if (payload.date instanceof Date) {
      delete (payload as Partial<Feedback>).date;
    }
    return payload;
  }
}
