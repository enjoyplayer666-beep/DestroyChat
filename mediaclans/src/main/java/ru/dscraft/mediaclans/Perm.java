package ru.dscraft.mediaclans;

/** Права роли в клане (16 штук, номер - как в меню). */
public enum Perm {
    ALL("Все возможности клана"),
    ANNOUNCE("Делать объявления клану"),
    INVITE("Приглашать игроков"),
    CHAT("Писать в клановый чат"),
    ICON("Менять иконку клана"),
    DESCRIPTION("Менять описание клана"),
    KICK("Исключать игроков"),
    RENAME("Менять название клана"),
    JOIN_TYPE("Менять тип вступления"),
    PASSWORD("Видеть и менять пароль клана"),
    PIN("Закреплять сообщения"),
    HISTORY("Смотреть историю клана"),
    SET_ROLE("Выдавать роли участникам"),
    EDIT_ROLES("Настраивать роли"),
    PVP("Менять PvP клана"),
    DEFAULT_ROLE("Менять начальную роль");

    private final String title;

    Perm(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    /** Номер в меню, с 1. */
    public int number() {
        return ordinal() + 1;
    }
}
