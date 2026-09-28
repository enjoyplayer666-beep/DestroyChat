package ru.dscraft.destroychat.util;

/**
 * Права и ключи LuckPerms-мета.
 * Имена прав специально оставлены "destroylobby.*" - те же, что уже выданы группам
 * по DONATE-SETUP, перевыдавать ничего не нужно. Мета-ключи тоже прежние, поэтому
 * уже поставленные чат-префиксы и цвета сохранятся.
 */
public final class Perms {

    /** Жирные/курсивные префиксы и любые символы в /prefix set (Ultra+). */
    public static final String PREFIX_FORMAT = "destroylobby.prefix.format";
    /** Отдельный префикс только для чата: /prefix chat (Ultra+). */
    public static final String PREFIX_CHAT = "destroylobby.prefix.chat";
    /** &-коды цвета прямо в сообщениях чата (Legend+). */
    public static final String CHAT_CODES = "destroylobby.chat.colors";
    /** Постоянный цвет сообщений: /color (Elite SP). */
    public static final String CHAT_COLOR = "destroylobby.chat.color";

    /** Свой цвет и наклон ника: /nickcolor (команда проекта). */
    public static final String NICK_COLOR = "destroychat.nickcolor";

    /** Мета-ключ личного чат-префикса в LuckPerms. */
    public static final String META_CHAT_PREFIX = "destroy-chat-prefix";
    /** Мета-ключ цвета сообщений в LuckPerms. */
    public static final String META_CHAT_COLOR = "destroy-chat-color";
    /** Мета-ключ цвета ника (/nickcolor). */
    public static final String META_NAME_COLOR = "destroy-name-color";
    /** Мета-ключ наклона ника (/nickcolor italic). */
    public static final String META_NAME_ITALIC = "destroy-name-italic";

    private Perms() {
    }
}
