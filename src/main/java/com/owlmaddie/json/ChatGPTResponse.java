// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
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
