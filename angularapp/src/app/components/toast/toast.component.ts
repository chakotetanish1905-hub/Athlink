import { Component } from '@angular/core';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-toast',
  template: `
    <div class="toast-container" aria-live="polite">
      <div *ngFor="let toast of notification.toasts$ | async" class="toast" [ngClass]="'toast-' + toast.type"
           (click)="notification.dismiss(toast.id)">
        {{ toast.message }}
      </div>
    </div>
  `
})
export class ToastComponent {
  constructor(public readonly notification: NotificationService) {}
}
