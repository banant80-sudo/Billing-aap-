"""Generate untracked Firebase client configuration without printing key values."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parent.parent
EXPOSED_KEY_HASH = "ff1e4676e5b987ca2812c822230f9d0461d50324a8297664b7dcb3b196cf3802"


def read_key(env_name):
    key = os.environ.get(env_name, "").strip()
    if not re.fullmatch(r"AIza[A-Za-z0-9_-]{35}", key):
        raise SystemExit(f"Set {env_name} to the Google API key for this Firebase client.")
    if hashlib.sha256(key.encode()).hexdigest() == EXPOSED_KEY_HASH:
        raise SystemExit(f"{env_name} must use a replacement for the exposed key.")
    return key


def load_android_config(template_path):
    config = json.loads(template_path.read_text(encoding="utf-8"))
    client, = config["client"]
    override = os.environ.get("GOOGLE_SERVICES_JSON_BASE64", "").strip()
    if not override:
        return config, client

    try:
        candidate = json.loads(base64.b64decode("".join(override.split()), validate=True))
        project = candidate["project_info"]
        expected_project = config["project_info"]
        if any(project[field] != expected_project[field] for field in ("project_id", "project_number")):
            raise ValueError
        matching = [
            entry for entry in candidate["client"]
            if entry["client_info"] == client["client_info"]
        ]
        selected, = matching
        if not any(
            entry["client_type"] == 3 and entry["client_id"].endswith(".apps.googleusercontent.com")
            for entry in selected["oauth_client"]
        ):
            raise ValueError
    except (AttributeError, KeyError, TypeError, ValueError):
        raise SystemExit(
            "GOOGLE_SERVICES_JSON_BASE64 must be an Android Firebase configuration "
            "for the existing Finboss app with a Google Sign-In OAuth client."
        ) from None

    candidate["client"] = [selected]
    return candidate, selected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("client", choices=["android", "web"])
    args = parser.parse_args()
    os.umask(0o077)

    if args.client == "android":
        key = read_key("FINBOSS_ANDROID_FIREBASE_API_KEY")
        path = ROOT / "app" / "google-services.template.json"
        config, client = load_android_config(path)
        client["api_key"] = [{"current_key": key}]
        path.with_name("google-services.json").write_text(
            json.dumps(config, indent=2) + "\n", encoding="utf-8"
        )
    else:
        key = read_key("FINBOSS_WEB_FIREBASE_API_KEY")
        config_file = ROOT / "web-portal" / "js" / "firebase-runtime-config.js"
        config_file.write_text(
            "export const firebaseApiKey = " + json.dumps(key) + ";\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
