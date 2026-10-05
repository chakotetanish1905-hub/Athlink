import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ENDPOINTS } from '../constants/constant';
import { FeedbackService } from './feedback.service';

describe('FeedbackService', () => {
  let service: FeedbackService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(FeedbackService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should call every feedback endpoint with the right verb', () => {
    service.sendFeedback({ feedbackText: 'Great', date: new Date(), userId: 1, ticketId: 2, category: 'Service Quality', rating: 5 })
      .subscribe();
    const post = http.expectOne(API_ENDPOINTS.FEEDBACK.BASE);
    expect(post.request.method).toBe('POST');
    expect(post.request.body.date).toBeUndefined();
    post.flush({});
    service.getAllFeedbacksByUserId(1).subscribe();
    http.expectOne(API_ENDPOINTS.FEEDBACK.BY_USER(1)).flush([]);
    service.getFeedbacks().subscribe();
    http.expectOne(API_ENDPOINTS.FEEDBACK.BASE).flush([]);
    service.deleteFeedback(3).subscribe();
    expect(http.expectOne(API_ENDPOINTS.FEEDBACK.BY_ID(3)).request.method).toBe('DELETE');
  });
});
