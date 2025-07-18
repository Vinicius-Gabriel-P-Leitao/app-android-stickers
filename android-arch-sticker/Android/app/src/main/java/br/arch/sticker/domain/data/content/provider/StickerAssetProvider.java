/*
 * Copyright (c) 2025 Vinícius Gabriel Pereira Leitão
 * All rights reserved.
 *
 * This source code is licensed under the Vinícius Non-Commercial Public License (VNCL),
 * which is based on the GNU General Public License v3.0, with additional restrictions regarding commercial use.
 */

package br.arch.sticker.domain.data.content.provider;

import static br.arch.sticker.domain.data.content.StickerContentProvider.STICKERS_ASSET;
import static br.arch.sticker.domain.data.database.StickerDatabaseHelper.ANIMATED_STICKER_PACK;
import static br.arch.sticker.view.core.util.convert.ConvertThumbnail.THUMBNAIL_FILE;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import br.arch.sticker.core.error.throwable.base.InternalAppException;
import br.arch.sticker.core.error.throwable.content.ContentProviderException;
import br.arch.sticker.core.error.throwable.sticker.StickerFileException;
import br.arch.sticker.core.validation.StickerValidator;
import br.arch.sticker.domain.data.database.StickerDatabaseHelper;
import br.arch.sticker.domain.data.database.repository.SelectStickerPackRepo;

public class StickerAssetProvider {
    private final static String TAG_LOG = StickerAssetProvider.class.getSimpleName();

    private final SelectStickerPackRepo selectStickerPackRepo;
    private final StickerValidator stickerValidator;
    private final Context context;

    public StickerAssetProvider(Context context) {
        this.context = context.getApplicationContext();
        this.stickerValidator = new StickerValidator(this.context);
        SQLiteDatabase database = StickerDatabaseHelper.getInstance(
                this.context).getReadableDatabase();
        this.selectStickerPackRepo = new SelectStickerPackRepo(database);
    }

    public AssetFileDescriptor fetchStickerAsset(Uri uri, boolean isWhatsApp) throws ContentProviderException, FileNotFoundException {
        final File stickerPackDir = new File(context.getFilesDir(), STICKERS_ASSET);
        final List<String> pathSegments = uri.getPathSegments();

        if (pathSegments.size() != 3) {
            throw new ContentProviderException("Segmentos de caminho devem ser 3, uri é: " + uri);
        }

        final String fileName = pathSegments.get(2);
        final String stickerPackIdentifier = pathSegments.get(1);

        if (TextUtils.isEmpty(stickerPackIdentifier)) {
            throw new ContentProviderException("Identificador está vazio, uri: " + uri);
        }

        if (TextUtils.isEmpty(fileName)) {
            throw new ContentProviderException("Nome do arquivo está vazio, uri: " + uri);
        }

        final File stickerDirectory = new File(stickerPackDir, stickerPackIdentifier);
        final File stickerFile = new File(stickerDirectory, fileName);

        if (!stickerDirectory.exists() || !stickerDirectory.isDirectory()) {
            throw new FileNotFoundException(
                    "Diretório de figurinhas não encontrado: " + stickerDirectory.getPath());
        }

        if (!stickerFile.exists() || !stickerFile.isFile()) {
            throw new FileNotFoundException(
                    "Arquivo não encontrado ou inválido: " + stickerFile.getAbsolutePath());
        }

        if (THUMBNAIL_FILE.equalsIgnoreCase(fileName)) {
            return openAssetFileSafely(stickerFile, "thumbnail");
        }

        try (Cursor cursor = selectStickerPackRepo.getStickerPackIsAnimated(
                stickerPackIdentifier)) {
            if (cursor == null) {
                throw new ContentProviderException("Cursor nulo ao buscar pacote de figurinhas.");
            }

            if (!cursor.moveToFirst()) {
                throw new ContentProviderException(
                        "Pacote de figurinha não encontrado no banco: " + stickerPackIdentifier);
            }

            final boolean animatedStickerPack = cursor.getInt(
                    cursor.getColumnIndexOrThrow(ANIMATED_STICKER_PACK)) != 0;
            if (isWhatsApp) {
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(".webp")) {
                    Log.w(TAG_LOG, "Arquivo ignorado por não ser .webp: " + fileName);
                    return null;
                }

                try {
                    stickerValidator.validateStickerFile(stickerPackIdentifier, fileName,
                            animatedStickerPack);
                } catch (StickerFileException | InternalAppException exception) {
                    Log.w(TAG_LOG,
                            "Sticker inválido, ignorado: " + stickerFile.getAbsolutePath() + " - " + exception.getMessage());
                    throw new ContentProviderException(exception.getMessage(), exception);
                }
            } else {
                Log.d(TAG_LOG,
                        "Ignorando validação porque não é o WhatsApp: " + stickerFile.getAbsolutePath());
            }

            return this.openAssetFileSafely(stickerFile, "sticker");
        } catch (SQLException sqlException) {
            Log.e(TAG_LOG,
                    "Erro no banco de dados ao buscar se o pacote é animado: " + stickerPackIdentifier,
                    sqlException);
            throw new ContentProviderException(sqlException.getMessage(), sqlException);
        } catch (RuntimeException exception) {
            Log.e(TAG_LOG,
                    "Erro inesperado ao buscar sticker stickerPack: " + stickerPackIdentifier,
                    exception);
            throw new ContentProviderException("Erro inesperado ao buscar sticker stickerPack",
                    exception);
        }
    }

    private AssetFileDescriptor openAssetFileSafely(File file, String type) {
        try {
            ParcelFileDescriptor parcelFileDescriptor = ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_ONLY);
            return new AssetFileDescriptor(parcelFileDescriptor, 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        } catch (IOException exception) {
            Log.e(TAG_LOG, "Erro ao abrir " + type + ": " + file.getAbsolutePath(), exception);
            throw new ContentProviderException(
                    "Erro ao abrir " + type + ": " + file.getAbsolutePath(), exception);
        }
    }
}
