package com.jianwen.springailab.chat;

import java.util.Objects;

/** Minimal chat message — mirrors Spring AI Message roles without framework deps. */
public final class Message {
    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    private final Role role;
    private final String content;
    private final String name; // tool name when Role.TOOL

    public Message(Role role, String content) {
        this(role, content, null);
    }

    public Message(Role role, String content, String name) {
        this.role = Objects.requireNonNull(role);
        this.content = content == null ? "" : content;
        this.name = name;
    }

    public Role role() { return role; }
    public String content() { return content; }
    public String name() { return name; }

    public static Message system(String c) { return new Message(Role.SYSTEM, c); }
    public static Message user(String c) { return new Message(Role.USER, c); }
    public static Message assistant(String c) { return new Message(Role.ASSISTANT, c); }
    public static Message tool(String toolName, String c) { return new Message(Role.TOOL, c, toolName); }

    @Override
    public String toString() {
        return "Message{role=" + role + ", name=" + name + ", content=" + content + "}";
    }
}
