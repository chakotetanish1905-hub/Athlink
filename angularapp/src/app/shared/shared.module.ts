import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ConfirmDialogComponent } from '../components/confirm-dialog/confirm-dialog.component';
import { MessageDialogComponent } from '../components/message-dialog/message-dialog.component';

/** Building blocks shared by the eager shell and every lazy feature module. */
@NgModule({
  declarations: [ConfirmDialogComponent, MessageDialogComponent],
  imports: [CommonModule, RouterModule],
  exports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule, ConfirmDialogComponent, MessageDialogComponent]
})
export class SharedModule {}
