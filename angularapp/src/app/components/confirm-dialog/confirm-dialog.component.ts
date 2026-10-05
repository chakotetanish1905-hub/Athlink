import { Component, EventEmitter, Input, Output } from '@angular/core';

/** Reusable "Are you sure ...?" popup. */
@Component({
  selector: 'app-confirm-dialog',
  template: `
    <div class="modal-backdrop" (click)="cancel.emit()">
      <div class="modal-box modal-small" role="dialog" aria-modal="true" (click)="$event.stopPropagation()">
        <p class="modal-message" id="confirmMessage">{{ message }}</p>
        <div class="modal-actions">
          <button type="button" id="confirmButton" class="btn" [ngClass]="confirmClass" (click)="confirm.emit()">
            {{ confirmText }}
          </button>
          <button type="button" id="cancelButton" class="btn btn-grey" (click)="cancel.emit()">{{ cancelText }}</button>
        </div>
      </div>
    </div>
  `
})
export class ConfirmDialogComponent {
  @Input() message = 'Are you sure?';
  @Input() confirmText = 'Yes';
  @Input() cancelText = 'Cancel';
  @Input() confirmClass = 'btn-danger';
  @Output() confirm = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();
}
