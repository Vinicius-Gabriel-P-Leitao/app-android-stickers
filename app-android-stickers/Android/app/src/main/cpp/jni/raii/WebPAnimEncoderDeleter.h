/*
 * Copyright (c) 2025 Vinícius Gabriel Pereira Leitão
 * All rights reserved.
 *
 * This source code is licensed under the Vinícius Non-Commercial Public License (VNCL),
 * which is based on the GNU General Public License v3.0, with additional restrictions regarding commercial use.
 */


#ifndef ANDROID_WEBPANIMENCODERDELETER_H
#define ANDROID_WEBPANIMENCODERDELETER_H

#include <memory>

extern "C" {
#include <mux.h>
#include "libswscale/swscale.h"
}

struct WebPAnimEncoderDeleter {
    void operator()(WebPAnimEncoder *enc) const {
        WebPAnimEncoderDelete(enc);
    }
};

using WebPAnimEncoderPtr = std::unique_ptr<WebPAnimEncoder, WebPAnimEncoderDeleter>;

#endif //ANDROID_WEBPANIMENCODERDELETER_H
