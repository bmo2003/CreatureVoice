// SPDX-License-Identifier: GPL-3.0-or-later | CreatureChat™ © owlmaddie LLC
// Code: GPLv3 | Assets: CC BY-NC 4.0; See LICENSE.md & LICENSE-ASSETS.md.
package com.owlmaddie.json;

import java.util.List;


public class ChatGPTResponse {
    public List<ChatGPTChoice> choices;
    
    public static class ChatGPTChoice {
        public ChatGPTMessage message;
    }

    public static class ChatGPTMessage {
        public String content;
    }
}
