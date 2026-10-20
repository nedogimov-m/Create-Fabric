#!/usr/bin/env python3
"""Exact source menu translations and the native editor's own labels, without replacing other keys."""

from pathlib import Path
import json


ENGLISH = {
    "title": "Create configuration",
    "scope.client": "Client",
    "scope.common": "Common",
    "scope.server": "Server",
    "scope.server_defaults": "Server defaults",
    "search": "Search settings",
    "all_groups": "All groups",
    "reset_scope": "Reset %s",
    "reset_scope_hint": "Restore all %s settings to their defaults. Apply to save.",
    "apply": "Apply",
    "reset_value": "Reset to default: %s",
    "applying": "Applying…",
    "context_changed": "Settings changed location. Reopen this screen.",
    "server_busy": "Server is busy. Try applying again.",
    "remote_read_only": "Server settings are read only.",
    "saved": "Saved",
    "save_failed": "Could not save: %s",
    "page": "Page %s/%s · %s settings",
    "invalid_value": "Enter a valid value within the allowed range.",
    "default": "Default: %s",
    "bounds": "Range: %s to %s",
    "not_loaded": "Settings are not loaded yet.",
    "unsaved": "Unsaved changes",
    "waiting_snapshot": "Waiting for server settings (read only).",
    "no_results": "No matching settings",
    "ponder_unavailable": "Ponder is currently unavailable.",
}

RUSSIAN = {
    "title": "Настройки Create",
    "scope.client": "Клиент",
    "scope.common": "Общие",
    "scope.server": "Сервер",
    "scope.server_defaults": "Шаблон сервера",
    "search": "Поиск настроек",
    "all_groups": "Все разделы",
    "reset_scope": "Сбросить: %s",
    "reset_scope_hint": "Восстановить все настройки «%s» по умолчанию. Для сохранения нажмите «Применить».",
    "apply": "Применить",
    "reset_value": "По умолчанию: %s",
    "applying": "Применение…",
    "context_changed": "Источник настроек изменился. Откройте экран заново.",
    "server_busy": "Сервер занят. Повторите применение.",
    "remote_read_only": "Настройки сервера доступны только для просмотра.",
    "saved": "Сохранено",
    "save_failed": "Не удалось сохранить: %s",
    "page": "Страница %s/%s · настроек: %s",
    "invalid_value": "Введите допустимое значение в указанных пределах.",
    "default": "По умолчанию: %s",
    "bounds": "Пределы: от %s до %s",
    "not_loaded": "Настройки ещё не загружены.",
    "unsaved": "Есть несохранённые изменения",
    "waiting_snapshot": "Ожидание настроек сервера (только просмотр).",
    "no_results": "Настройки не найдены",
    "ponder_unavailable": "Размышления пока недоступны.",
}


def generate(output: Path, upstream: Path) -> dict[str, list[str]]:
    source_locales: dict[str, dict] = {}
    for root in (upstream / "src/main/resources", upstream / "src/generated/resources"):
        for source in sorted((root / "assets/create/lang").glob("*.json")):
            source_locales.setdefault(source.name, {}).update(json.loads(source.read_text()))
    owned: dict[str, list[str]] = {}
    for name, source in source_locales.items():
        entries = {key: value for key, value in source.items() if key.startswith("create.menu.")}
        ui = ENGLISH if name == "en_us.json" else RUSSIAN if name == "ru_ru.json" else {}
        entries.update({"create.config.ui." + key: value for key, value in ui.items()})
        if not entries:
            continue
        relative = "assets/create/lang/" + name
        target = output / relative
        values = json.loads(target.read_text()) if target.exists() else {}
        values.update(entries)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(values, indent=2, ensure_ascii=False) + "\n")
        owned[relative] = sorted(entries)
    return owned


if __name__ == "__main__":
    fabric = Path(__file__).resolve().parents[1]
    generate(fabric / "src/main/resources", fabric.parent)
