/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * Modifications by Vinícius, 2025
 * Licensed under the Vinícius Non-Commercial Public License (VNCL)
 */

package br.arch.sticker.domain.service.fetch;

import static br.arch.sticker.domain.data.content.StickerContentProvider.AUTHORITY_URI;
import static br.arch.sticker.domain.data.database.StickerDatabase.ANDROID_APP_DOWNLOAD_LINK_IN_QUERY;
import static br.arch.sticker.domain.data.database.StickerDatabase.ANIMATED_STICKER_PACK;
import static br.arch.sticker.domain.data.database.StickerDatabase.AVOID_CACHE;
import static br.arch.sticker.domain.data.database.StickerDatabase.IMAGE_DATA_VERSION;
import static br.arch.sticker.domain.data.database.StickerDatabase.IOS_APP_DOWNLOAD_LINK_IN_QUERY;
import static br.arch.sticker.domain.data.database.StickerDatabase.LICENSE_AGREEMENT_WEBSITE;
import static br.arch.sticker.domain.data.database.StickerDatabase.PRIVACY_POLICY_WEBSITE;
import static br.arch.sticker.domain.data.database.StickerDatabase.PUBLISHER_EMAIL;
import static br.arch.sticker.domain.data.database.StickerDatabase.PUBLISHER_WEBSITE;
import static br.arch.sticker.domain.data.database.StickerDatabase.STICKER_PACK_IDENTIFIER_IN_QUERY;
import static br.arch.sticker.domain.data.database.StickerDatabase.STICKER_PACK_NAME_IN_QUERY;
import static br.arch.sticker.domain.data.database.StickerDatabase.STICKER_PACK_PUBLISHER_IN_QUERY;
import static br.arch.sticker.domain.data.database.StickerDatabase.STICKER_PACK_TRAY_IMAGE_IN_QUERY;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import br.arch.sticker.BuildConfig;
import br.arch.sticker.core.exception.content.ContentProviderException;
import br.arch.sticker.core.exception.content.InvalidWebsiteUrlException;
import br.arch.sticker.core.exception.sticker.PackValidatorException;
import br.arch.sticker.core.exception.sticker.StickerFileException;
import br.arch.sticker.core.exception.sticker.StickerValidatorException;
import br.arch.sticker.core.validation.StickerPackValidator;
import br.arch.sticker.core.validation.StickerValidator;
import br.arch.sticker.domain.data.model.Sticker;
import br.arch.sticker.domain.data.model.StickerPack;
import br.arch.sticker.domain.dto.ListStickerPackValidationResult;
import br.arch.sticker.domain.dto.StickerPackValidationResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class FetchStickerPackService {

    @NonNull
    public static ListStickerPackValidationResult fetchStickerPackListFromContentProvider(
            Context context
    ) throws ContentProviderException {
        final Cursor cursor = context.getContentResolver().query(AUTHORITY_URI, null, null, null, null);
        if (cursor == null) {
            throw new ContentProviderException("Não foi possível buscar no content provider, " + BuildConfig.CONTENT_PROVIDER_AUTHORITY);
        }

        HashSet<String> stickerPackIdentifierSet = new HashSet<>();

        final ArrayList<StickerPack> stickerPackList;

        final ArrayList<StickerPack> invalidPacks = new ArrayList<>();
        final HashMap<StickerPack, List<Sticker>> validPacksWithInvalidStickers = new HashMap<StickerPack, List<Sticker>>();

        if (cursor.moveToFirst()) {
            stickerPackList = new ArrayList<>(buildListStickerPack(cursor, context));
        } else {
            cursor.close();
            throw new ContentProviderException("Nenhum pacote de figurinhas encontrado no content provider");
        }

        for (StickerPack stickerPack : stickerPackList) {
            if (!stickerPackIdentifierSet.add(stickerPack.identifier)) {
                throw new ContentProviderException(
                        "Os identificadores dos pacotes de figurinhas devem ser únicos, há mais de um pacote com identificador: " +
                                stickerPack.identifier);
            }
        }

        if (stickerPackList.isEmpty()) {
            throw new ContentProviderException("Deve haver pelo menos um pacote de adesivos no aplicativo");
        }

        stickerPackList.removeIf(stickerPack -> {
            try {
                StickerPackValidator.verifyStickerPackValidity(context, stickerPack);

                List<Sticker> invalidStickers = new ArrayList<>();

                stickerPack.getStickers().removeIf(sticker -> {
                    try {
                        StickerValidator.verifyStickerValidity(context, stickerPack.identifier, sticker, stickerPack.animatedStickerPack);
                        return false;
                    } catch (StickerFileException | StickerValidatorException stickerFileException) {
                        invalidStickers.add(sticker);
                        return true;
                    }
                });

                if (!invalidStickers.isEmpty()) {
                    validPacksWithInvalidStickers.put(stickerPack, invalidStickers);
                    return true;
                }

                return stickerPack.getStickers().isEmpty();
            } catch (PackValidatorException | InvalidWebsiteUrlException appCoreStateException) {
                invalidPacks.add(stickerPack);
                return true;
            }
        });

        return new ListStickerPackValidationResult(stickerPackList, invalidPacks, validPacksWithInvalidStickers);
    }

    @NonNull
    private static ArrayList<StickerPack> buildListStickerPack(Cursor cursor, Context context) {
        ArrayList<StickerPack> stickerPackList = new ArrayList<>();
        cursor.moveToFirst();

        do {
            StickerPack stickerPack = writeCursorToStickerPack(cursor, context);
            stickerPackList.add(stickerPack);
        } while (cursor.moveToNext());

        return stickerPackList;
    }

    public static StickerPackValidationResult fetchStickerPackFromContentProvider(
            Context context, String stickerPackIdentifier) throws ContentProviderException {

        Cursor cursor = context.getContentResolver().query(Uri.withAppendedPath(AUTHORITY_URI, stickerPackIdentifier), null, null, null, null);
        if (cursor == null || cursor.getCount() == 0) {
            throw new ContentProviderException("Não foi possível buscar no content provider, " + BuildConfig.CONTENT_PROVIDER_AUTHORITY);
        }

        final StickerPack stickerPack;

        final List<Sticker> invalidSticker = new ArrayList<>();

        if (cursor.moveToFirst()) {
            stickerPack = writeCursorToStickerPack(cursor, context);
        } else {
            cursor.close();
            throw new ContentProviderException("Nenhum pacote de figurinhas encontrado no content provider");
        }

        try {
            StickerPackValidator.verifyStickerPackValidity(context, stickerPack);

            stickerPack.getStickers().removeIf(sticker -> {
                try {
                    StickerValidator.verifyStickerValidity(context, stickerPack.identifier, sticker, stickerPack.animatedStickerPack);
                    return false;
                } catch (StickerFileException | StickerValidatorException exception) {
                    invalidSticker.add(sticker);
                    return true;
                }
            });

            if (stickerPack.getStickers().isEmpty()) {
                throw new ContentProviderException("Pacote de figurinhas inválido: não restaram stickers após a validação.");
            }

            return new StickerPackValidationResult(stickerPack, invalidSticker);
        } catch (PackValidatorException | InvalidWebsiteUrlException exception) {
            throw new ContentProviderException("Pacote de figurinhas inválido: " + exception.getMessage());
        }
    }

    @NonNull
    private static StickerPack writeCursorToStickerPack(Cursor cursor, Context context) {
        final String identifier = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_IDENTIFIER_IN_QUERY));
        final String name = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_NAME_IN_QUERY));
        final String publisher = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_PUBLISHER_IN_QUERY));
        final String trayImage = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_TRAY_IMAGE_IN_QUERY));
        final String androidPlayStoreLink = cursor.getString(cursor.getColumnIndexOrThrow(ANDROID_APP_DOWNLOAD_LINK_IN_QUERY));
        final String iosAppLink = cursor.getString(cursor.getColumnIndexOrThrow(IOS_APP_DOWNLOAD_LINK_IN_QUERY));
        final String publisherEmail = cursor.getString(cursor.getColumnIndexOrThrow(PUBLISHER_EMAIL));
        final String publisherWebsite = cursor.getString(cursor.getColumnIndexOrThrow(PUBLISHER_WEBSITE));
        final String privacyPolicyWebsite = cursor.getString(cursor.getColumnIndexOrThrow(PRIVACY_POLICY_WEBSITE));
        final String licenseAgreementWebsite = cursor.getString(cursor.getColumnIndexOrThrow(LICENSE_AGREEMENT_WEBSITE));
        final String imageDataVersion = cursor.getString(cursor.getColumnIndexOrThrow(IMAGE_DATA_VERSION));
        final boolean avoidCache = cursor.getShort(cursor.getColumnIndexOrThrow(AVOID_CACHE)) > 0;
        final boolean animatedStickerPack = cursor.getShort(cursor.getColumnIndexOrThrow(ANIMATED_STICKER_PACK)) > 0;

        final StickerPack stickerPack = new StickerPack(
                identifier, name, publisher, trayImage, publisherEmail, publisherWebsite, privacyPolicyWebsite, licenseAgreementWebsite,
                imageDataVersion, avoidCache, animatedStickerPack);

        List<Sticker> stickers = FetchStickerService.fetchListStickerForPack(context, identifier);
        stickerPack.setStickers(stickers);

        stickerPack.setAndroidPlayStoreLink(androidPlayStoreLink);
        stickerPack.setIosAppStoreLink(iosAppLink);

        return stickerPack;
    }
}
