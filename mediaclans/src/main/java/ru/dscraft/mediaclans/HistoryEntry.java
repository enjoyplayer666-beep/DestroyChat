package ru.dscraft.mediaclans;

/** Запись истории клана: создание, вход, выход, исключение, передача, смена роли (target = "игрок|роль"). */
public record HistoryEntry(Type type, String actor, String target, long time) {

    public enum Type { CREATE, JOIN, LEAVE, KICK, TRANSFER, ROLE }
}
