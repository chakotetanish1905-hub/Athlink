import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
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

  it('uses the SRS endpoints', () => {
    service.getFeedbacks().subscribe(list => expect(list).toEqual([]));
    http.expectOne(service.apiUrl + '/api/feedback').flush(null);

    service.getAllFeedbacksByUserId(2).subscribe();
    http.expectOne(service.apiUrl + '/api/feedback/user/2').flush([]);

    service.sendFeedback({ feedbackText: 'Great help', category: 'Service Quality', rating: 5 }).subscribe();
    expect(http.expectOne(service.apiUrl + '/api/feedback').request.method).toBe('POST');

    service.deleteFeedback(3).subscribe();
    expect(http.expectOne(service.apiUrl + '/api/feedback/3').request.method).toBe('DELETE');
  });
});
