import { SupportAgent } from './support-agent.model';

describe('SupportAgent model', () => {
  it('should allow creating a typed SupportAgent object', () => {
    const value = {} as SupportAgent;
    expect(value).toBeTruthy();
  });
});
