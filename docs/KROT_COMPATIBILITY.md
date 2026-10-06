# Совместимость с KROT

Исследован Windows-проект KROT и закреплённый им upstream:

- `Flowseal/tg-ws-proxy v1.10.4`;
- commit `70b982da2ca75637b61f281170e4ed57df763db8`;
- Windows runtime SHA-256
  `a8c3e1ef738a2cf4bb7b9480bc620ab4a259cdd82a2b70a1c98283c55586575d`.

## Что делает Windows KROT

KROT запускает `TgWsProxy_console.exe` с параметрами:

```text
--host 127.0.0.1 --port 1443 --secret <32 hex>
```

Это локальный **MTProto proxy**, не SOCKS5. Telegram подключается ссылкой вида:

```text
tg://proxy?server=127.0.0.1&port=1443&secret=dd<secret>
```

Движок расшифровывает 64-байтный obfuscated2 init при помощи локального секрета,
определяет transport/DC, создаёт отдельный стандартный obfuscated init для relay,
переупаковывает поток в WebSocket binary frames и подключается к:

```text
wss://kws{dc}.web.telegram.org/apiws
```

Для DC2/DC4 закреплён IP `149.154.167.220`; TLS SNI/hostname остаётся доменом
`*.web.telegram.org`. При сбоях используются совместимые Cloudflare WSS-домены,
после чего Windows-версия допускает прямой TCP fallback.

## Адаптация для Android SOCKS5

Telegram подключается к локальному SOCKS5 `127.0.0.1:<сохранённый порт>`. После SOCKS CONNECT
Telegram отправляет обычный MTProto obfuscated stream, поэтому дополнительный
`dd`-секрет между Telegram и MordaTG не нужен.

MordaTG:

1. определяет DC по SOCKS destination;
2. открывает тот же проверяемый TLS WebSocket `/apiws`;
3. передаёт оригинальный 64-байтный standard obfuscated init;
4. локально расшифровывает только transport framing, чтобы сохранить границы
   MTProto transport packets в WebSocket frames;
5. не расшифровывает MTProto payload и не записывает содержимое трафика.

Прямой TCP fallback из Windows-версии намеренно не используется: по Android-ТЗ
внешние соединения должны идти через WS/TLS. При недоступности WSS SOCKS-сессия
закрывается, и Telegram выполняет штатное переподключение.

## Таймауты и восстановление

- SOCKS/MTProto handshake: 10 секунд;
- WSS connect: 8 секунд;
- exponential backoff с jitter между endpoint attempts;
- established sockets блокируются без polling и без idle-timeout;
- при смене сети активные сокеты закрываются, чтобы Telegram создал свежую сессию;
- лимит активных соединений: 64.

## Защита данных

Логи с содержимым трафика отсутствуют. Runtime state содержит только статус,
количество соединений, объёмы, reconnect count, uptime и последний DC. TLS
certificate validation и hostname verification всегда включены.

