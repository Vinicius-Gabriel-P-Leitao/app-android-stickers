/*
 * Copyright (c) 2025 Vinícius Gabriel Pereira Leitão
 * All rights reserved.
 *
 * This source code is licensed under the Vinícius Non-Commercial Public License (VNCL),
 * which is based on the GNU General Public License v3.0, with additional restrictions regarding commercial use.
 */

package com.vinicius.sticker.view.feature.media.fragment;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vinicius.sticker.R;

public class PermissionSettingFragment extends BottomSheetDialogFragment {
   private PermissionCallback callback;

   public interface PermissionCallback {
      void onPermissionsGranted();

      void onPermissionsDenied();
   }

   public void setCallback(PermissionCallback callback) {
      this.callback = callback;
   }

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setStyle(STYLE_NORMAL, R.style.BottomSheetStyle);
   }

   @androidx.annotation.Nullable
   @Override
   public View onCreateView(
       LayoutInflater inflater,
       @androidx.annotation.Nullable ViewGroup container,
       @androidx.annotation.Nullable Bundle savedInstanceState
   ) {
      View view = inflater.inflate( R.layout.dialog_permission_settings, container, false );

      Button buttonOpenSettings = view.findViewById(R.id.open_settings);
      buttonOpenSettings.setOnClickListener( viewAccept -> {
         Intent intent = new Intent( Settings.ACTION_APPLICATION_DETAILS_SETTINGS );
         Uri uri = Uri.fromParts( "package", requireContext().getPackageName(), null );
         intent.setData( uri );
         startActivity( intent );

         if ( callback != null ) {
            callback.onPermissionsGranted();
         }
         dismiss();
      } );

      Button buttonCancel = view.findViewById( R.id.cancel_permission_button );
      buttonCancel.setOnClickListener( viewCancel -> {
         if ( callback != null ) {
            callback.onPermissionsDenied();
         }
         dismiss();
      } );

      return view;
   }

   @NonNull
   @Override
   public Dialog onCreateDialog(Bundle savedInstanceState) {
      BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
      dialog.setOnShowListener(dialogInterface -> {
         BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
         FrameLayout bottomSheet = bottomSheetDialog.findViewById(R.id.design_bottom_sheet);

         if (bottomSheet != null) {
            bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
         }
      });
      return dialog;
   }
}
