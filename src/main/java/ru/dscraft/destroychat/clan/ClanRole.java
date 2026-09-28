package ru.dscraft.destroychat.clan;

/** Роль участника в клане. */
public enum ClanRole {
    OWNER("<gold>Владелец</gold>"),
    ADMIN("<aqua>Администратор</aqua>"),
    MEMBER("<gray>Участник</gray>");

    private final String display;

    ClanRole(String display) {
        this.display = display;
    }

    /** Название роли в MiniMessage. */
    public String display() {
        return display;
    }

    /** Может ли управлять клановыми настройками (описание, тип вступления, приглашения, иконка). */
    public boolean canManageClan() {
        return this != MEMBER;
    }
}
