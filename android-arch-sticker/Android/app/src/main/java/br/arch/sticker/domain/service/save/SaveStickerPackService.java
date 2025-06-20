/*
 * Copyright (c) 2025 Vinícius Gabriel Pereira Leitão
 * All rights reserved.
 *
 * This source code is licensed under the Vinícius Non-Commercial Public License (VNCL),
 * which is based on the GNU General Public License v3.0, with additional restrictions regarding commercial use.
 */

package br.arch.sticker.domain.service.save;

import static br.arch.sticker.domain.data.content.StickerContentProvider.STICKERS_ASSET;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import br.arch.sticker.core.exception.base.InternalAppException;
import br.arch.sticker.core.exception.content.InvalidWebsiteUrlException;
import br.arch.sticker.core.exception.sticker.PackValidatorException;
import br.arch.sticker.core.exception.sticker.StickerFileException;
import br.arch.sticker.core.exception.sticker.StickerPackSaveException;
import br.arch.sticker.core.exception.sticker.StickerValidatorException;
import br.arch.sticker.core.pattern.CallbackResult;
import br.arch.sticker.core.validation.StickerPackValidator;
import br.arch.sticker.core.validation.StickerValidator;
import br.arch.sticker.domain.data.database.StickerDatabase;
import br.arch.sticker.domain.data.database.repository.InsertStickerPackRepo;
import br.arch.sticker.domain.data.model.Sticker;
import br.arch.sticker.domain.data.model.StickerPack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

// @formatter:off
public class SaveStickerPackService {
    private final static String TAG_LOG = SaveStickerPackService.class.getSimpleName();

    @FunctionalInterface
    public interface SaveStickerPackCallback {
        void onStickerPackSaveResult(CallbackResult<StickerPack> callbackResult);
    }

    public static void saveStickerPack(
            Context context, StickerPack stickerPack,
            SaveStickerPackCallback callback
    ) {
        File mainDirectory = new File(context.getFilesDir(), STICKERS_ASSET);
        if (!createMainDirectory(mainDirectory, callback)) {
            callback.onStickerPackSaveResult(CallbackResult.failure(new Exception("Erro ao criar pacote principal de figurinhas")));
            return;
        }

        File stickerPackDirectory = createStickerPackDirectory(mainDirectory, stickerPack.identifier, callback);
        if (stickerPackDirectory == null) {
            return;
        }

        if (!copyStickers(context, stickerPack, stickerPackDirectory, callback)) {
            callback.onStickerPackSaveResult(CallbackResult.failure(new Exception("Erro ao copiar os figurinhas")));
            return;
        }

        try {
            StickerPackValidator.verifyStickerPackValidity(context, stickerPack);
        }  catch (PackValidatorException | StickerValidatorException | InvalidWebsiteUrlException appCoreStateException) {
            callback.onStickerPackSaveResult(CallbackResult.failure(appCoreStateException));
        }

        for (Sticker sticker : stickerPack.getStickers()) {
            try{
                StickerValidator.verifyStickerValidity(context, stickerPack.identifier, sticker, stickerPack.animatedStickerPack);
            } catch (StickerFileException stickerFileException) {
                if (Objects.equals(sticker.imageFileName, stickerFileException.getFileName())) {
                    sticker.setStickerIsInvalid(stickerFileException.getErrorCode());
                    callback.onStickerPackSaveResult(
                            CallbackResult.warning("Alguns stickers foram marcados como inválidos: " + stickerFileException.getFileName()));
                }
            } catch (StickerValidatorException stickerValidatorException) {
                callback.onStickerPackSaveResult(CallbackResult.failure(stickerValidatorException));
            }
        }

        insertStickerPack(context, stickerPack, callback);
    }

    private static void insertStickerPack(Context context, StickerPack stickerPack, SaveStickerPackCallback callback) {
        StickerDatabase instance = StickerDatabase.getInstance(context);
        SQLiteDatabase writableDatabase = instance.getWritableDatabase();

        new InsertStickerPackRepo().insertStickerPack(
                writableDatabase, stickerPack, callbackResult -> {
                    switch (callbackResult.getStatus()) {
                        case SUCCESS:
                            callback.onStickerPackSaveResult(CallbackResult.success(callbackResult.getData())); // NOTE: Somente esse deve retornar os dados.
                            break;
                        case WARNING:
                            Log.w(TAG_LOG, callbackResult.getWarningMessage());
                            break;
                        case FAILURE:
                            if (callbackResult.getError() instanceof StickerPackSaveException exception) {
                                callback.onStickerPackSaveResult(CallbackResult.failure(exception));
                            } else {
                                callback.onStickerPackSaveResult(CallbackResult.failure(new InternalAppException("Erro interno desconhecido!")));
                            }
                            break;
                    }
                });
    }

    private static boolean createMainDirectory(File mainDirectory, SaveStickerPackCallback callback) {
        if (!mainDirectory.exists()) {
            boolean created = mainDirectory.mkdirs();

            if (!created) {
                callback.onStickerPackSaveResult(CallbackResult.failure(new StickerPackSaveException("Falha ao criar o mainDirectory: " + mainDirectory.getPath())));
                return false;
            }
            callback.onStickerPackSaveResult(CallbackResult.debug("mainDirectory criado com sucesso: " + mainDirectory.getPath()));
        } else {
            callback.onStickerPackSaveResult(CallbackResult.debug("mainDirectory já existe: " + mainDirectory.getPath()));
        }

        return true;
    }

    private static File createStickerPackDirectory(File mainDirectory, String stickerPackIdentifier, SaveStickerPackCallback callback) {
        File stickerPackDirectory = new File(mainDirectory, stickerPackIdentifier);

        if (!stickerPackDirectory.exists()) {
            boolean created = stickerPackDirectory.mkdirs();

            if (!created) {
                callback.onStickerPackSaveResult(
                        CallbackResult.failure(new StickerPackSaveException("Falha ao criar a pasta: " + stickerPackDirectory.getPath())));
                return null;
            }

            callback.onStickerPackSaveResult(CallbackResult.debug("Pasta criada com sucesso: " + stickerPackDirectory.getPath()));
        } else {
            callback.onStickerPackSaveResult(CallbackResult.warning("Pasta já existe: " + stickerPackDirectory.getPath()));
        }
        return stickerPackDirectory;
    }

    private static boolean copyStickers(Context context, StickerPack stickerPack, File stickerPackDirectory, SaveStickerPackCallback callback) {
        List<Sticker> stickerList = stickerPack.getStickers();
        Sticker firstSticker = stickerList.get(0);

        File thumbnailSticker = new File(context.getCacheDir(), firstSticker.imageFileName);
        compressAndSaveThumbnail(thumbnailSticker, stickerPackDirectory, callback);

        for (Sticker sticker : stickerList) {
            String fileName = sticker.imageFileName;
            File sourceFile = new File(context.getCacheDir(), fileName);
            File destFile = new File(stickerPackDirectory, fileName);

            if (sourceFile.exists()) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Path sourcePath = sourceFile.toPath();
                        Path destPath = destFile.toPath();

                        Files.copy(sourcePath, destPath);

                        callback.onStickerPackSaveResult(CallbackResult.debug("Arquivo copiado para: " + destFile.getPath()));
                    }
                } catch (IOException exception) {
                    callback.onStickerPackSaveResult(CallbackResult.failure(new StickerPackSaveException("Arquivo não encontrado: " + fileName, exception)));
                    return false;
                }
            } else {
                callback.onStickerPackSaveResult(CallbackResult.failure(new StickerPackSaveException("Arquivo não encontrado: " + fileName)));
                return false;
            }
        }
        return true;
    }

    private static void compressAndSaveThumbnail(File originalFile, File destinationDir, SaveStickerPackCallback callback) {
        if (!originalFile.exists()) {
            callback.onStickerPackSaveResult(CallbackResult.failure(new StickerPackSaveException("Arquivo para thumbnail não encontrado: " + originalFile.getAbsolutePath())));
            return;
        }

        try {
            Bitmap bitmap = BitmapFactory.decodeFile(originalFile.getAbsolutePath());
            if (bitmap == null) {
                callback.onStickerPackSaveResult(CallbackResult.warning("Erro ao decodificar o bitmap."));
                return;
            }

            File thumbnailFile = new File(destinationDir, "thumbnail.jpg");
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();

            int quality = 100;
            byte[] compressedBytes;

            do {
                outStream.reset();
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream);
                compressedBytes = outStream.toByteArray();
                quality -= 5;
            } while (compressedBytes.length > 40 * 1024 && quality > 5);

            FileOutputStream fileOutputStream = new FileOutputStream(thumbnailFile);
            fileOutputStream.write(compressedBytes);
            fileOutputStream.flush();
            fileOutputStream.getFD().sync();
            fileOutputStream.close();

            callback.onStickerPackSaveResult(CallbackResult.debug("Thumbnail salva com sucesso: " + thumbnailFile.getAbsolutePath()));
        } catch (IOException exception) {
            callback.onStickerPackSaveResult(CallbackResult.failure(exception));
        }
    }
}
