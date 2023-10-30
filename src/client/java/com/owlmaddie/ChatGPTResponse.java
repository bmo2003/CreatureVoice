package com.owlmaddie;

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
