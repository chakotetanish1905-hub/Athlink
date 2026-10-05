import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ToastType = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  message: string;
  type: ToastType;
}

/** Lightweight toast messages shown at the top-right of every page. */
@Injectable({ providedIn: 'root' })
export class NotificationService {

  private nextId = 1;
  private readonly toastsSubject = new BehaviorSubject<Toast[]>([]);
  readonly toasts$ = this.toastsSubject.asObservable();

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'error');
  }

  info(message: string): void {
    this.show(message, 'info');
  }

  dismiss(id: number): void {
    this.toastsSubject.next(this.toastsSubject.value.filter(t => t.id !== id));
  }

  private show(message: string, type: ToastType): void {
    // Avoid stacking the same message twice (e.g. several parallel 403s).
    if (this.toastsSubject.value.some(t => t.message === message)) {
      return;
    }
    const toast: Toast = { id: this.nextId++, message, type };
    this.toastsSubject.next([...this.toastsSubject.value, toast]);
    setTimeout(() => this.dismiss(toast.id), 4000);
  }
}
