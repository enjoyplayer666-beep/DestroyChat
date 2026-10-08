package ru.dscraft.mediaclans;

/** Права роли в клане (16 штук, номер - как в меню, порядок как на сервере-образце). */
public enum Perm {
    ALL("Все возможности клана"),
    ANNOUNCE("Делать объявления клану"),
    BUY_SLOTS("Покупать слоты игроков"),
    CHAT("Писать в клановый чат"),
    ICON("Менять иконку клана"),
    INVITE("Приглашать игроков"),
    KICK("Исключать игроков ниже ролью"),
    DESCRIPTION("Менять описание клана"),
    RENAME("Менять название клана"),
    PASSWORD("Менять пароль клана"),
    VIEW_PASSWORD("Смотреть пароль клана"),
    JOIN_TYPE("Менять тип вступления"),
    SET_ROLE("Выдавать роли игрокам"),
    PVP("Переключать PvP клана"),
    EDIT_ROLES("Настраивать роли клана"),
    HISTORY("Смотреть историю клана");

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
