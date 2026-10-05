import { Component, EventEmitter, Input, Output } from '@angular/core';

/** Reusable success popup with a single button ("Ok" / "Log In"). */
@Component({
  selector: 'app-message-dialog',
  template: `
    <div class="modal-backdrop">
      <div class="modal-box modal-small" role="dialog" aria-modal="true">
        <p class="modal-message" id="successMessage" [class.text-success]="success">{{ message }}</p>
        <div class="modal-actions">
          <button type="button" id="okButton" class="btn btn-primary" (click)="ok.emit()">{{ buttonText }}</button>
        </div>
      </div>
    </div>
  `
})
export class MessageDialogComponent {
  @Input() message = '';
  @Input() buttonText = 'Ok';
  @Input() success = false;
  @Output() ok = new EventEmitter<void>();
}
