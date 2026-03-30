# Ulanzi TC001 Scrolling Sign (AWTRIX 3) - Hubitat Driver

This driver supports AWTRIX 3 communication over MQTT only.

## MQTT Setup

1. Configure:
	- `mqttBroker`
	- `mqttPort`
	- `mqttUsername` / `mqttPassword` (optional if broker allows anonymous access)
	- `mqttTopic` (base topic for your AWTRIX device)
2. Save preferences.
3. Run `Initialize` (or use debug command `mqttConnect`).

## MQTT Topics Used

Published commands use `<mqttTopic>/<command>`:

- `power` for `on()` / `off()`
- `notify` for `deviceNotification()`
- `notify/dismiss` for `dismiss()`
- `reboot` for `reboot()`
- `rtttl` for `sound()`

The `statusRetrieval()` command requests AWTRIX status topics over MQTT:

- `statusRetrieval("stats")` -> `<mqttTopic>/stats`
- `statusRetrieval("effects")` -> `<mqttTopic>/stats/effects`
- `statusRetrieval("transitions")` -> `<mqttTopic>/stats/transitions`
- `statusRetrieval("loop")` -> `<mqttTopic>/stats/loop`
- `statusRetrieval("all")` -> requests all of the above

Subscribed topics (under `<mqttTopic>/...`) include:

- `stats`
- `stats/currentApp`
- `stats/buttonSelect`
- `stats/buttonLeft`
- `stats/buttonRight`
- `stats/effects`
- `stats/transitions`
- `stats/loop`

Additional status retrieval results are stored in these attributes:

- `effects`
- `transitions`
- `appLoop`

## Hubitat Button Events

The driver emits standard Hubitat button capability events (`pushed` and `released`).

Button mapping:

- Button `1` = left button (`stats/buttonLeft`)
- Button `2` = middle/select button (`stats/buttonSelect`)
- Button `3` = right button (`stats/buttonRight`)

Event mapping:

- Payloads like `pressed`, `pushed`, `down` -> `pushed`
- Payloads like `released`, `up` -> `released`

This enables direct Rules Machine triggers such as "Button 2 pushed".

## Brightness Control (SwitchLevel)

The driver supports Hubitat `SwitchLevel` so you can control brightness from dashboards and Rules Machine.

- Use `setLevel(0..100)` from automations or a dashboard dimmer tile.
- The driver converts level (%) to AWTRIX brightness (`BRI`, 0..255).
- Brightness is published to `<mqttTopic>/settings` (fixed topic).
- Incoming `stats.bri` updates both:
	- `brightness` (0..255)
	- `level` (0..100)

Example payload published by `setLevel(50)`:

`{"BRI":128}`

## Health and Reconnect Behavior

- The driver tracks MQTT connection status and marks `healthStatus` online/offline.
- Failed connection or publish attempts trigger automatic reconnect with exponential backoff.
- Reconnect attempts are bounded to prevent endless broker hammering.
- Health check scheduling follows `healthCheckMethod` and `healthCheckInterval` preferences.

## MQTT Diagnostics

When debug commands are enabled (`_DEBUG = true`), use:

- `mqttStatus` to log current MQTT runtime state (connected, broker/topic, reconnect count, unknown topic count, subscribed topics)
- `mqttConnect` to manually reconnect
- `disconnect` to manually disconnect

## Notes

- Unknown MQTT topics are ignored and counted for diagnostics.
- Malformed `stats` JSON payloads are handled defensively and logged.

