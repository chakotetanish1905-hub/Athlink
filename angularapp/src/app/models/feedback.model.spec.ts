import { Feedback } from './feedback.model';

describe('Feedback model', () => {
  it('should allow creating a typed Feedback object', () => {
    const value = {} as Feedback;
    expect(value).toBeTruthy();
  });
});
