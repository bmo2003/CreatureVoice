// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.network;

import java.net.URI;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.ClickEvent.OpenUrl;

public class ClickEventHelper {
    /** new API: use the OpenUrl record */
    public static ClickEvent openUrl(String url) {
        return new OpenUrl(URI.create(url));
    }
}
