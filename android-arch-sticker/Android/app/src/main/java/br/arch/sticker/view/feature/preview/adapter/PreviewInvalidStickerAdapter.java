/*
 * Copyright (c) 2025 Vinícius Gabriel Pereira Leitão
 * All rights reserved.
 *
 * This source code is licensed under the Vinícius Non-Commercial Public License (VNCL),
 * which is based on the GNU General Public License v3.0, with additional restrictions regarding commercial use.
 */

package br.arch.sticker.view.feature.preview.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import br.arch.sticker.R;
import br.arch.sticker.domain.data.model.Sticker;
import br.arch.sticker.domain.data.model.StickerPack;
import br.arch.sticker.domain.service.fetch.FetchStickerAssetService;
import br.arch.sticker.view.feature.preview.viewholder.InvalidStickerListViewHolder;

public class PreviewInvalidStickerAdapter extends RecyclerView.Adapter<InvalidStickerListViewHolder> {
    @NonNull
    private final String stickerPackIdentifier;
    @NonNull
    private final List<Sticker> stickerList;
    @Nullable
    private final StickerPack stickerPack;

    private int maxNumberOfStickersInARow;

    public PreviewInvalidStickerAdapter(@NonNull String stickerPackIdentifier, @NonNull List<Sticker> stickerList) {
        this.stickerPackIdentifier = stickerPackIdentifier;
        this.stickerList = new ArrayList<>(stickerList);
        stickerPack = null;
    }

    public PreviewInvalidStickerAdapter(@NonNull StickerPack stickerPack) {
        this.stickerPack = stickerPack;
        this.stickerList = new ArrayList<>(stickerPack.getStickers());
        this.stickerPackIdentifier = stickerPack.identifier;
    }


    @NonNull
    @Override
    public InvalidStickerListViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        final Context context = viewGroup.getContext();
        final LayoutInflater layoutInflater = LayoutInflater.from(context);
        final View stickersRow = layoutInflater.inflate(R.layout.invalid_stickers_list_item, viewGroup, false);
        return new InvalidStickerListViewHolder(stickersRow);
    }

    @Override
    public void onBindViewHolder(@NonNull InvalidStickerListViewHolder viewHolder, int position) {
        Context context = viewHolder.itemView.getContext();

        if (!stickerList.isEmpty()) {
            final Sticker sticker = stickerList.get(position);
            viewHolder.stickerPreview.setImageURI(FetchStickerAssetService.buildStickerAssetUri(stickerPackIdentifier, sticker.imageFileName));
            viewHolder.textErrorMessage.setText(sticker.stickerIsValid);
        }

        if (stickerPack != null) {
            final Sticker sticker = stickerList.get(position);
            viewHolder.stickerPreview.setImageURI(FetchStickerAssetService.buildStickerAssetUri(stickerPackIdentifier, sticker.imageFileName));
            viewHolder.textErrorMessage.setText(TextUtils.isEmpty(sticker.stickerIsValid) ? context.getString(R.string.sticker_is_valid) : sticker.stickerIsValid);
            viewHolder.buttonFix.setVisibility(TextUtils.isEmpty(sticker.stickerIsValid) ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return stickerList != null ? stickerList.size() : 0;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setImageRowSpec(int maxNumberOfStickersInARow, int minMarginBetweenImages) {
        if (this.maxNumberOfStickersInARow != maxNumberOfStickersInARow) {
            this.maxNumberOfStickersInARow = maxNumberOfStickersInARow;
            notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateStickerPackItems(List<Sticker> newItems) {
        this.stickerList.clear();
        this.stickerList.addAll(newItems);
        notifyDataSetChanged();
    }
}
