# DestroyChat 1.0.0 (Paper 1.21.1)

Чат DestroyCraft. Работает в паре с **DestroyLobby**.

Формат: `Ⓛ ⌜Игрок⌟ ник → сообщение`

- Обычное сообщение - локальный чат (Ⓛ, радиус `local-radius`), с `!` в начале - глобальный (Ⓖ).
- Ник всегда `&7`. Префикс: личный чат-префикс → префикс привилегии из LuckPerms → `⌜Игрок⌟`.
- Значки Ⓛ / Ⓖ - из ресурс-пака SP-DS.

## В паре с DestroyLobby

DestroyLobby выключает чат в лобби и не пропускает сообщения между лобби и игровыми мирами.
DestroyChat только оформляет сообщения и считает радиус. Прямой зависимости по коду нет:
плагины собираются независимо, а связаны через порядок обработки чата и общие данные в LuckPerms.
Без DestroyLobby чат тоже работает, но тогда и в лобби.

## Команды

- `/prefix chat <текст>` или `/chatprefix <текст>` (Ultra+) - префикс только для чата, можно с градиентом:
  `/prefix chat <gradient:#FF5555:#FFFF55>Король</gradient>`. Сброс: `/prefix chat reset`.
- `/color <цвет>` (Elite SP) - цвет сообщений: `&d`, `#FF55FF`, `#FF5555:#FFFF55`, `красный`, `rainbow`.
  Сброс: `/color reset`.
- `/destroychat reload` - перезагрузить config.yml.

## Права (имена прежние, как в DONATE-SETUP.txt)

- `destroylobby.prefix.chat` - /prefix chat (Ultra+)
- `destroylobby.prefix.format` - жирный/курсив в префиксе (Ultra+, объявлено в DestroyLobby)
- `destroylobby.chat.colors` - &-коды в сообщениях (Legend+)
- `destroylobby.chat.color` - /color (Elite SP)
- `destroylobby.chat.spy` - видеть локальный чат на любом расстоянии
- `destroychat.admin` - /destroychat reload

Личный чат-префикс и цвет хранятся в LuckPerms (мета `destroy-chat-prefix` / `destroy-chat-color`),
поэтому то, что игроки уже поставили в 1.2.0, сохранится.

Другие чат-плагины (NovaChat, EssentialsChat) нужно удалить или выключить в них форматирование.

## Сборка

`mvn clean package` → `target/destroy-chat-1.0.0.jar`, либо через GitHub Actions
(`.github/workflows/main.yml` уже в проекте).
