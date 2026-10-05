import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ENDPOINTS } from '../constants/constant';
import { SupportAgentService } from './support-agent.service';

describe('SupportAgentService', () => {
  let service: SupportAgentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(SupportAgentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should call every support agent endpoint with the right verb', () => {
    service.getAllAgents().subscribe();
    expect(http.expectOne(API_ENDPOINTS.SUPPORT_AGENT.BASE).request.method).toBe('GET');
    service.getAgentById(2).subscribe();
    expect(http.expectOne(API_ENDPOINTS.SUPPORT_AGENT.BY_ID(2)).request.method).toBe('GET');
    const agent = { name: 'A', email: 'a@b.com', phone: '9876543210', expertise: 'Content Manager', experience: '2',
      status: 'Available', addedDate: new Date(), profile: '', shiftTiming: '9-6', remarks: '' };
    service.addAgent(agent).subscribe();
    expect(http.expectOne(API_ENDPOINTS.SUPPORT_AGENT.BASE).request.method).toBe('POST');
    service.updateAgent(2, agent).subscribe();
    expect(http.expectOne(API_ENDPOINTS.SUPPORT_AGENT.BY_ID(2)).request.method).toBe('PUT');
    service.deleteAgent(2).subscribe();
    expect(http.expectOne(API_ENDPOINTS.SUPPORT_AGENT.BY_ID(2)).request.method).toBe('DELETE');
  });
});
