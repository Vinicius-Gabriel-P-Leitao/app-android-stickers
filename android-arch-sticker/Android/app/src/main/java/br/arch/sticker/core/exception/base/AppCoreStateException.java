/*
 * Copyright (c) 2025 Vinícius Gabriel Pereira Leitão
 * All rights reserved.
 *
 * This source code is licensed under the Vinícius Non-Commercial Public License (VNCL),
 * which is based on the GNU General Public License v3.0, with additional restrictions regarding commercial use.
 */

package br.arch.sticker.core.exception.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AppCoreStateException extends IllegalStateException {
    private final String errorCode;
    private final Object[] details;

    public AppCoreStateException(@NonNull String message, @Nullable String errorCode) {
        this(message, null, errorCode);
    }

    public AppCoreStateException(@NonNull String message, @Nullable Throwable cause, @Nullable String errorCode) {
        this(message, cause, errorCode, null);
    }

    public AppCoreStateException(@NonNull String message, @Nullable Throwable cause, @Nullable String errorCode, @Nullable Object[] details) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    @Nullable
    public String getErrorCode() {
        return errorCode;
    }

    @Nullable
    public Object[] getDetails() {
        return details;
    }
}
